import { Injectable, computed, signal } from '@angular/core';
import type {
  DecisionRequest,
  DecisionType,
  EngagementUpdateListItem,
  Freshness,
  PendingUpdateResponse,
} from '@contract/api-contract';
import { DETAIL_FIXTURE, LIST_FIXTURE } from './pending-update.fixture';

/** A detail response the user is allowed to act on. Narrowed from the union, not asserted. */
export type ActionableUpdate = Extract<PendingUpdateResponse, { status: 'UPDATE_AVAILABLE' }>;

/** List rows for which an update is known to be pending, whatever became of the summary. */
export type BehindListItem = Extract<
  EngagementUpdateListItem,
  { status: 'UPDATE_AVAILABLE' | 'SUMMARY_PENDING' | 'SUMMARY_UNAVAILABLE' }
>;

const BEHIND_STATUSES = ['UPDATE_AVAILABLE', 'SUMMARY_PENDING', 'SUMMARY_UNAVAILABLE'] as const;

export function isBehind(item: EngagementUpdateListItem): item is BehindListItem {
  return (BEHIND_STATUSES as readonly string[]).includes(item.status);
}

/**
 * A decision can only be built from a state that carries a decisionToken, and only
 * UPDATE_AVAILABLE does. The guard is the single place that fact is expressed; every button,
 * badge and template downstream derives from it rather than re-deciding for itself.
 */
export function isActionable(detail: PendingUpdateResponse): detail is ActionableUpdate {
  return detail.status === 'UPDATE_AVAILABLE';
}

/** Beyond this, the projection lag is worth telling the user about rather than hiding. */
const STALENESS_WARNING_SECONDS = 120;

/**
 * Client-side state for pending template updates.
 *
 * <p>Holds server responses as they were received and derives everything else. No status is
 * recomputed here and no change summary is composed here: both are server facts, and a client
 * that second-guesses them would produce a second, divergent account of the same methodology
 * change. The client's job is to decide what a practitioner sees and what they may act on.
 */
@Injectable({ providedIn: 'root' })
export class PendingUpdateStore {
  private readonly listItems = signal<readonly EngagementUpdateListItem[]>([]);
  private readonly details = signal<Readonly<Record<string, PendingUpdateResponse>>>({});
  private readonly listFreshness = signal<Freshness | null>(null);
  private readonly selectedId = signal<string | null>(null);

  /**
   * Decisions this session has submitted. In the real client these go to the decision endpoint;
   * keeping them observable is what lets a test assert that a decision carries the token of the
   * summary the user actually read.
   */
  private readonly submittedDecisions = signal<readonly DecisionRequest[]>([]);

  readonly rows = this.listItems.asReadonly();
  readonly freshness = this.listFreshness.asReadonly();
  readonly decisions = this.submittedDecisions.asReadonly();

  /** Rows with a pending update, in the order the server returned them. */
  readonly rowsNeedingAttention = computed(() => this.listItems().filter(isBehind));

  readonly pendingCount = computed(() => this.rowsNeedingAttention().length);

  /**
   * How far behind the read model was when this answer was produced.
   *
   * <p>Surfaced rather than hidden: the projection is eventually consistent by construction, and
   * a practitioner deciding whether to trust an empty list deserves to know the answer is from a
   * moment ago rather than from now.
   */
  readonly stalenessSeconds = computed(() => {
    const current = this.listFreshness();
    if (!current) {
      return null;
    }
    const served = Date.parse(current.servedAt);
    const asOf = Date.parse(current.projectionAsOf);
    return Math.max(0, Math.round((served - asOf) / 1000));
  });

  readonly stalenessIsWorthShowing = computed(() => {
    const seconds = this.stalenessSeconds();
    return this.listFreshness()?.degraded === true || (seconds !== null && seconds > STALENESS_WARNING_SECONDS);
  });

  readonly selected = computed<PendingUpdateResponse | null>(() => {
    const id = this.selectedId();
    return id === null ? null : (this.details()[id] ?? null);
  });

  /** The selected engagement when — and only when — the user may decide on it. */
  readonly selectedActionable = computed<ActionableUpdate | null>(() => {
    const detail = this.selected();
    return detail !== null && isActionable(detail) ? detail : null;
  });

  /** Loads the sample data. A real client would call the list endpoint here. */
  load(): void {
    this.listItems.set(LIST_FIXTURE.items);
    this.listFreshness.set(LIST_FIXTURE.freshness);
    this.details.set(DETAIL_FIXTURE);
  }

  select(engagementId: string | null): void {
    this.selectedId.set(engagementId);
  }

  apply(): void {
    this.decide('APPLY');
  }

  decline(): void {
    this.decide('DECLINE');
  }

  /**
   * Submits a decision for the selected engagement.
   *
   * <p>The request carries the decisionToken from the response the user actually reviewed. If a
   * new template version was published while they were reading, the server rejects it as
   * superseded rather than applying content they never saw — which is why the token travels with
   * the decision instead of the server re-deriving "latest" at submission time.
   *
   * <p>The row moves to DECISION_IN_PROGRESS immediately. Applying loads the engagement file,
   * which takes about a minute, so the interface has to represent work in flight rather than
   * pretending the decision was instant.
   */
  private decide(decision: DecisionType): void {
    const target = this.selectedActionable();
    if (!target) {
      return;
    }

    const request: DecisionRequest = {
      decision,
      decisionToken: target.decisionToken,
      requestId: `req-${target.engagementId}-${target.summary.toVersion}`,
    };
    this.submittedDecisions.update((existing) => [...existing, request]);

    const submittedAt = new Date().toISOString();
    this.details.update((current) => ({
      ...current,
      [target.engagementId]: {
        status: 'DECISION_IN_PROGRESS',
        freshness: target.freshness,
        engagementId: target.engagementId,
        baselineVersion: target.baselineVersion,
        targetVersion: target.latestVersion,
        decision,
        submittedAt,
      },
    }));

    this.listItems.update((items) =>
      items.map((item) =>
        item.engagementId === target.engagementId
          ? {
              engagementId: item.engagementId,
              engagementName: item.engagementName,
              templateId: item.templateId,
              templateDisplayName: item.templateDisplayName,
              status: 'DECISION_IN_PROGRESS',
              baselineVersion: target.baselineVersion,
              targetVersion: target.latestVersion,
              decision,
              submittedAt,
            }
          : item,
      ),
    );
  }
}
