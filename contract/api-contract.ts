/**
 * Template Update Service — client/server contract.
 *
 * This file is the single source of truth for the vocabulary shared by the Java domain
 * model (Part 2) and the Angular client state (Part 3). TypeScript is used here as a
 * lingua franca for the contract; it is not the implementation language of the server.
 *
 * Two constraints shape every decision below:
 *
 *  1. Loading an engagement file takes ~1 minute. Nothing on a user-facing read path may
 *     touch the engagement store. Baseline versions are served from an event-fed
 *     projection, which makes every read answer *eventually consistent* — so the contract
 *     must carry freshness explicitly rather than implying "now".
 *
 *  2. Product templates are shared across firms. The expensive work — diffing and
 *     rendering a change summary — depends only on (templateId, fromVersion, toVersion),
 *     never on the engagement or the tenant. It is therefore computed once globally and
 *     reused by every firm.
 */

// ---------------------------------------------------------------------------
// Primitives
// ---------------------------------------------------------------------------

/** ISO-8601 instant in UTC, e.g. "2026-08-25T13:02:27Z". */
export type Instant = string;

/** Monotonically increasing product template version. */
export type TemplateVersion = number;

export type TemplateId = string;
export type EngagementId = string;

// ---------------------------------------------------------------------------
// Status
// ---------------------------------------------------------------------------

/**
 * The lifecycle of an engagement's pending-update state.
 *
 * `UNKNOWN`, `SUMMARY_PENDING` and `SUMMARY_UNAVAILABLE` are first-class states rather
 * than null fields. Each represents a distinct, honest thing the server can say, and each
 * requires different UI treatment. Collapsing them into a nullable summary would force the
 * client to guess why the data is missing.
 */
export type EngagementUpdateStatus =
  /** The projection holds no baseline for this engagement, so we cannot say whether an
   *  update is pending. This is the honest answer during backfill: on an existing product
   *  the projection starts empty while engagements already exist, and populating it means
   *  loading each engagement once at ~1 minute. Rather than reporting "up to date" —
   *  which would silently hide pending updates — the contract admits it does not know.
   *  Not an error state, and expected to be transient per engagement. */
  | 'UNKNOWN'
  /** Baseline equals the latest published version. Nothing to decide. */
  | 'UP_TO_DATE'
  /** Behind the latest version, and the change summary is ready to review. */
  | 'UPDATE_AVAILABLE'
  /** Behind the latest version, but the summary for this version range is still being
   *  computed. The pending-update badge is trustworthy; the summary is not ready yet. */
  | 'SUMMARY_PENDING'
  /** Behind the latest version, and summary generation failed. The user is correctly told
   *  an update exists, and is not shown a summary we cannot stand behind. */
  | 'SUMMARY_UNAVAILABLE'
  /** An Apply or Decline has been accepted and the engagement system is processing it
   *  (~1 minute). Prevents a second submission against the same baseline. */
  | 'DECISION_IN_PROGRESS';

// ---------------------------------------------------------------------------
// Freshness
// ---------------------------------------------------------------------------

/**
 * Freshness envelope, present on every projection-derived response.
 *
 * The read model is eventually consistent by construction, so the contract states when the
 * answer was true instead of implying it is current. `projectionAsOf` is the watermark of
 * the engagement event stream the projection has consumed — not the time the request was
 * served, which would be a comforting lie.
 */
export interface Freshness {
  /** Instant the projection was current to. */
  projectionAsOf: Instant;
  /** Instant this response was served. The gap between the two is the staleness the user
   *  is exposed to, and is what the client surfaces when it grows beyond a threshold. */
  servedAt: Instant;
  /** True when the projection is lagging beyond its operational budget. Lets the client
   *  warn without hardcoding the threshold, and keeps the policy on the server. */
  degraded: boolean;
}

// ---------------------------------------------------------------------------
// GET /api/v1/engagements/template-updates
// ---------------------------------------------------------------------------

/**
 * The list view: "which of my engagement files have pending updates?"
 *
 * Firm scope is derived from the caller's authenticated tenant, never from a parameter.
 * This endpoint answers from the projection plus cached template metadata only: a handful
 * of integer comparisons, no engagement loads, no diff computation.
 *
 * Deliberately excludes the change summary. Summaries are keyed by version range, not by
 * engagement, so returning them per item would duplicate the same payload across hundreds
 * of rows. The list carries only what the badge and the row need; the summary is fetched
 * when the user opens one.
 *
 * Response: 200
 */
export interface EngagementUpdateListResponse {
  freshness: Freshness;
  items: EngagementUpdateListItem[];
}

interface EngagementUpdateListItemBase {
  engagementId: EngagementId;
  engagementName: string;
  templateId: TemplateId;
  templateDisplayName: string;
}

/**
 * Discriminated on `status` so that illegal combinations cannot be represented: an
 * up-to-date engagement has no `versionsBehind`, and a row with an unknown baseline
 * exposes no version numbers at all.
 */
export type EngagementUpdateListItem =
  | (EngagementUpdateListItemBase & {
      status: 'UNKNOWN';
    })
  | (EngagementUpdateListItemBase & {
      status: 'UP_TO_DATE';
      currentVersion: TemplateVersion;
    })
  | (EngagementUpdateListItemBase & {
      status: 'UPDATE_AVAILABLE' | 'SUMMARY_PENDING' | 'SUMMARY_UNAVAILABLE';
      /** The version this engagement was created from, and the baseline for the diff. */
      baselineVersion: TemplateVersion;
      /** The latest published version of this template. */
      latestVersion: TemplateVersion;
      /** Number of published versions v such that `baselineVersion < v <= latestVersion`.
       *  AUDIT-CA v3 against a latest of v5 gives 2. Computed server-side so the client
       *  never does arithmetic on version numbers: they are identifiers from a publication
       *  history, not a guaranteed dense sequence. */
      versionsBehind: number;
      /** Publication instant of the latest version — the "how urgent is this" signal. */
      latestPublishedAt: Instant;
    })
  | (EngagementUpdateListItemBase & {
      status: 'DECISION_IN_PROGRESS';
      baselineVersion: TemplateVersion;
      targetVersion: TemplateVersion;
      decision: DecisionType;
      submittedAt: Instant;
    });

// ---------------------------------------------------------------------------
// GET /api/v1/engagements/{engagementId}/pending-update
// ---------------------------------------------------------------------------

/**
 * The detail view: the human-readable summary the user reads before deciding.
 *
 * Returns the *net effect* of moving from the engagement's baseline straight to the latest
 * version — a single diff(templateId, baseline, latest), not a folded chain of consecutive
 * diffs. Where several updates have accumulated, intermediate values were never present in
 * this engagement and will never be: showing them would ask a non-technical auditor to
 * evaluate a state that cannot occur. The versions that were skipped are still reported,
 * as context, in `accumulatedVersions`.
 *
 * Response: 200, or 404 when the engagement is not visible to the caller's firm.
 */
export type PendingUpdateResponse =
  | {
      status: 'UNKNOWN';
      freshness: Freshness;
      engagementId: EngagementId;
    }
  | {
      status: 'UP_TO_DATE';
      freshness: Freshness;
      engagementId: EngagementId;
      currentVersion: TemplateVersion;
    }
  | {
      status: 'UPDATE_AVAILABLE';
      freshness: Freshness;
      engagementId: EngagementId;
      baselineVersion: TemplateVersion;
      latestVersion: TemplateVersion;
      /** Every version published after the baseline, oldest first, ending at the latest.
       *  Supports "2 versions published since yours: v7 (21 Jul), v8 (25 Aug)" — context
       *  about how much has accumulated, without implying the summary walks through each
       *  one. The summary describes the net effect of the whole range. */
      accumulatedVersions: PublishedVersion[];
      summary: ChangeSummary;
      /** Echoed back on the decision request so the server can detect that the user
       *  reviewed a version range that is no longer current. Present only in this state:
       *  the states below deliberately omit it, so a user structurally cannot Apply an
       *  update whose summary they were unable to read. */
      decisionToken: DecisionToken;
    }
  | {
      status: 'SUMMARY_PENDING';
      freshness: Freshness;
      engagementId: EngagementId;
      baselineVersion: TemplateVersion;
      latestVersion: TemplateVersion;
      accumulatedVersions: PublishedVersion[];
      /** Hint for the client's retry cadence. Keeps polling policy on the server. */
      retryAfterSeconds: number;
    }
  | {
      status: 'SUMMARY_UNAVAILABLE';
      freshness: Freshness;
      engagementId: EngagementId;
      baselineVersion: TemplateVersion;
      latestVersion: TemplateVersion;
      accumulatedVersions: PublishedVersion[];
      /** Stable, non-technical reason for the failure. The user is still told truthfully
       *  that an update exists; they are simply not shown a summary we cannot vouch for. */
      reason: 'DIFF_SOURCE_UNAVAILABLE' | 'RENDERING_FAILED';
      /** Correlation id for support. Never a stack trace. */
      incidentId: string;
    }
  | {
      status: 'DECISION_IN_PROGRESS';
      freshness: Freshness;
      engagementId: EngagementId;
      baselineVersion: TemplateVersion;
      targetVersion: TemplateVersion;
      decision: DecisionType;
      submittedAt: Instant;
    };

export interface PublishedVersion {
  version: TemplateVersion;
  publishedAt: Instant;
}

// ---------------------------------------------------------------------------
// The human-readable change summary
// ---------------------------------------------------------------------------

/**
 * Where the raw diff becomes human-readable: on the server, before this contract.
 *
 * The client receives sentences and classified facts, never raw JSON paths to interpret.
 * Three reasons:
 *
 *  - Templates are shared across firms, so one rendering per (templateId, from, to) serves
 *    every tenant. Rendering client-side would repeat identical work in every browser.
 *  - The wording of a change to an audit methodology is domain content, not presentation.
 *    It must be identical in the web client, in exports and in any future surface, and it
 *    must be reproducible when a firm asks why a change was described the way it was.
 *  - Shipping raw diffs for the client to interpret would scatter the phrasing rules across
 *    every consumer and make them impossible to version or audit centrally.
 *
 * Every field below is produced by deterministic rules over the diff. No generative model
 * originates a value here: in this domain a hallucinated materiality threshold is a
 * defensibility failure, not a cosmetic one. An LLM may be used offline to *propose*
 * phrasing templates for review, and its output is frozen into `rendererVersion`.
 */
export interface ChangeSummary {
  templateId: TemplateId;
  fromVersion: TemplateVersion;
  toVersion: TemplateVersion;
  /** When this summary was computed. Distinct from `Freshness.projectionAsOf`: the summary
   *  comes from the shared cache, the baseline from the tenant projection. Two sources,
   *  two independent freshness facts, both stated. */
  generatedAt: Instant;
  /** Identifies the deterministic rule set that produced the wording. Cached summaries are
   *  invalidated when this changes, and it makes any rendered sentence reproducible. */
  rendererVersion: string;
  headline: ChangeHeadline;
  groups: ChangeGroup[];
}

/** The at-a-glance counts, so the user knows the size of the decision before reading. */
export interface ChangeHeadline {
  totalChanges: number;
  added: number;
  modified: number;
  removed: number;
  /** Changes the rules flagged as warranting attention — threshold moves, removals of
   *  required procedures. Surfaced separately so a large update does not bury them. */
  notable: number;
}

/** Changes grouped by the template section they belong to, in template order. */
export interface ChangeGroup {
  /** Section key from the template structure, e.g. "planning". Used for stable ordering
   *  and deep links, not shown to the user. */
  sectionKey: string;
  /** Section name as the practitioner knows it, e.g. "Planning". */
  sectionLabel: string;
  changes: HumanChange[];
}

export interface HumanChange {
  /** Stable within a summary; lets the client key lists and the user cite a change. */
  id: string;
  kind: 'ADDED' | 'MODIFIED' | 'REMOVED';
  /** The kind of template element in the user's language: "question", "checklist",
   *  "guidance threshold", "procedure". Derived from the diff path structure. */
  elementType: string;
  /** One plain-language sentence, deterministically generated. */
  description: string;
  /** Present only when a scalar value moved, so the UI can render before/after without
   *  parsing the sentence. Pre-formatted server-side (e.g. "4.5%", "4.0%") to keep number
   *  and unit formatting in one place. */
  valueChange?: {
    before: string;
    after: string;
  };
  /** The diff path this sentence was derived from, carried for traceability: when a firm
   *  asks why a change was described a certain way, the description can be tied back to
   *  the exact diff entry. The client never parses or interprets it — it is an opaque
   *  correlation string, surfaced only in support and export contexts. */
  sourcePath: string;
  significance: 'NORMAL' | 'NOTABLE';
}

// ---------------------------------------------------------------------------
// POST /api/v1/engagements/{engagementId}/pending-update/decision
// ---------------------------------------------------------------------------

export type DecisionType = 'APPLY' | 'DECLINE';

/**
 * Opaque token identifying the exact version range the user reviewed. Opaque so the client
 * cannot synthesise one: a decision must be traceable to a summary that was actually
 * served.
 */
export type DecisionToken = string;

export interface DecisionRequest {
  decision: DecisionType;
  /** From the `PendingUpdateResponse` the user acted on. If a new template version was
   *  published while the user was reading, this no longer matches and the request is
   *  rejected rather than silently applying content they never reviewed. */
  decisionToken: DecisionToken;
  /** Client-generated idempotency key. A retry after a timeout must not enqueue a second
   *  decision against an operation that takes ~1 minute to complete. */
  requestId: string;
}

/**
 * Applying requires loading the engagement — the same ~1 minute constraint that rules out
 * synchronous reads. The decision is therefore accepted and processed asynchronously:
 * `202 Accepted`, and the engagement moves to `DECISION_IN_PROGRESS` until the engagement
 * system emits its completion event and the projection catches up.
 */
export type DecisionResponse =
  /** 202 */
  | {
      outcome: 'ACCEPTED';
      decisionId: string;
      engagementId: EngagementId;
      decision: DecisionType;
      baselineVersion: TemplateVersion;
      targetVersion: TemplateVersion;
      acceptedAt: Instant;
      /** Expected completion window, so the client can set expectations instead of
       *  spinning indefinitely. */
      estimatedCompletionSeconds: number;
    }
  /** 409 — a newer template version was published while the user was reviewing. The client
   *  re-fetches and asks the user to review the updated range. Never auto-resolved: the
   *  whole point is that the user decides on what they actually read. */
  | {
      outcome: 'SUPERSEDED';
      engagementId: EngagementId;
      reviewedTargetVersion: TemplateVersion;
      currentLatestVersion: TemplateVersion;
    }
  /** 409 — a decision for this engagement is already being processed. */
  | {
      outcome: 'ALREADY_IN_PROGRESS';
      engagementId: EngagementId;
      decisionId: string;
      submittedAt: Instant;
    };
