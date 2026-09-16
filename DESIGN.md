# Design — Pending Template Updates

## 1. High-Level Architecture

One constraint decides everything: loading an engagement file takes about a minute, and the
template version is stored inside it. With hundreds of engagements per firm, no user-facing read
can ask the engagement store. The answer must exist before the user asks.

So the version is lifted out of the slow store when it changes. Engagement lifecycle events —
created, applied, declined — feed a small per-tenant **projection** holding
`engagementId → (templateId, baselineVersion)`. That is the only place a baseline is read from.
The **template catalog** holds the publication history, shared by every firm because the templates
are; a few templates updated weekly, served from memory.

Being behind is therefore **derived**, not stored, and publishing writes nothing into any customer
database. Fanning out on publish to flag affected engagements would mean one write per tenant
database — a path that can fail halfway and leave the flag lying — to avoid a comparison that
costs nothing. A derived answer has no second copy to drift.

Publishing does one thing: warm the summary cache. A summary depends only on
`(templateId, fromVersion, toVersion)`, never on the engagement or firm, so hundreds of
engagements collapse to a handful of ranges computed once for all customers.

```text
  WRITE PATH (weekly)                        READ PATH (milliseconds)

  ┌──────────────────┐                        ┌──────────────┐
  │  Template store  │                        │    Angular   │
  │  (shared by all  │                        │    client    │
  │   customer firms)│                        └──────┬───────┘
  └────────┬─────────┘                               │ GET
           │ TemplatePublished                       ▼
           │ (warms cache only —          ┌────────────────────────┐
           │  no tenant writes)           │  Template Update       │
           ▼                              │  Service               │
  ┌────────────────────────┐              │  baseline vs history   │
  │  Summary cache         │◄─────────────┤  → status              │
  │  key: (template,       │              └───────────┬────────────┘
  │        from, to)       │                          │
  │  GLOBAL, all tenants   │                          ▼
  └────────────────────────┘        ┌──────────────────────────────────┐
                                    │  Baseline projection (per tenant)│
                                    │  ENG-1003  AUDIT-CA  base=3      │
                                    └────────────────┬─────────────────┘
                                                     ▲ Created / Applied / Declined
                                    ┌────────────────┴─────────────────┐
                                    │  Engagement system (~1 min/file) │
                                    │  never read on the read path     │
                                    └──────────────────────────────────┘
```

**Server / client boundary.** The server owns every fact: whether an update is pending, how many
versions accumulated, the wording of what changed, whether a decision may be made. The client owns
presentation and intent, and never recomputes a status or composes a sentence about a methodology
change.

**Accumulated updates** are diffed once for the whole range, baseline straight to latest, and
presented as their **net effect**. Replaying the chain would mislead: REVIEW-CA moves a tolerance
0.15 → 0.12 in v7 and 0.12 → 0.10 in v8, but an engagement on v6 has never held 0.12 and never
will. The skipped versions are reported as context ("2 versions published since yours"), because
how much accumulated is a real input to the decision — the intermediate *values* are not.

### Client / Server Contract

Type definitions in [`contract/api-contract.ts`](contract/api-contract.ts). Three endpoints: the
firm's list, one engagement's detail, the decision. Four things it makes explicit:

- **Status is a six-state discriminated union**, not a flag beside nullable fields. `UNKNOWN`,
  `SUMMARY_PENDING` and `SUMMARY_UNAVAILABLE` are distinct because each is a different honest
  answer needing different treatment. An `UP_TO_DATE` engagement has no `summary` field to read.
- **Freshness is explicit, and doubled.** `projectionAsOf` is the event-stream watermark;
  `ChangeSummary.generatedAt` is when the summary was rendered. Independent lag, so one timestamp
  would misrepresent one of them.
- **Decisions carry a `decisionToken`** naming the range reviewed, so a publish landing mid-review
  returns `409 SUPERSEDED` instead of applying content the user never saw. The token exists only in
  `UPDATE_AVAILABLE`: an update whose summary could not be read is structurally undecidable.
- **The decision endpoint is asynchronous** — `202`, then `DECISION_IN_PROGRESS`. Applying loads
  the engagement: the same minute that rules out synchronous reads.

### Human-Readable Change Summary

**Rendered server-side, by deterministic rules.** Templates are shared, so one rendering serves
every firm instead of repeating identical work in every browser. And the wording of a methodology
change is domain content, not presentation: it must be identical across the client, exports and
any future surface, and reproducible when a firm asks why a change was described as it was —
which is what `rendererVersion` is for.

Rules, not a language model, own every number and structural fact. A summary reading "4.0%" when
the template says 4.5% is indistinguishable from a correct one and would be relied on in a
professional judgement. An LLM is useful for drafting and critiquing these phrasing rules offline;
it does not run in this path.

## 2. Implementation Plan

1. **Contract first** — the Java model and the client state both derive from it.
2. **Projection and catalog.** Subscribe to engagement events, expose the list endpoint. This
   alone delivers the badge, the largest slice of value, before any summary exists.
3. **Backfill.** On an existing product the projection starts empty while engagements already
   exist, and populating it costs one slow load per file. Those engagements report `UNKNOWN`
   rather than a reassuring `UP_TO_DATE`, while a throttled background job drains the set,
   prioritised by recent access so active files resolve first.
4. **Summary cache**, warmed on publish for ranges in use, computed on demand otherwise;
   `SUMMARY_PENDING` covers the gap.
5. **Client**, then the decision endpoint.

## 3. Testing Strategy

The included tests cover the three engagement states, plus the two client behaviours that could be
wrong invisibly: that the net effect reaches the user with no superseded intermediate value, and
that an unreadable summary cannot be decided on.

Beyond the excerpt, in priority order: **projection correctness under event loss and reordering**,
since a silently wrong badge is the worst failure here; **reconciliation** against the engagement
store on a sample; **contract conformance**, so the Java model and client state cannot drift from
the types; **cache invalidation** on a `rendererVersion` bump; and the **superseded decision** path.

## 4. Evaluation & Observability

The badge can be wrong without anyone noticing, so correctness needs active measurement, not error
rates alone. A scheduled job loads a small random sample the slow way and compares the real version
against the projection; mismatches alert, and the rate is the system's honest accuracy figure.

Alongside it: **projection lag** as the primary SLI, exported as `projectionAsOf` so the client can
tell the user rather than hide it; **cache hit rate** and render-failure rate, which predict
`SUMMARY_PENDING` and `SUMMARY_UNAVAILABLE` exposure; and **superseded-decision rate**, which says
whether publish cadence has outrun how long firms take to review. Decline rate by template is worth
watching too — it is a content signal, not a system fault.

## 5. Failure Modes & Tradeoffs

**A lost or reordered event** makes the projection lie, confidently. Mitigated by monotonic
per-engagement event versioning, the reconciliation sample above, and never presenting the answer
as current: `projectionAsOf` travels with every response.

**The diff service is unavailable** → `SUMMARY_UNAVAILABLE`. The user is still told truthfully that
an update exists, and is not shown a summary the system cannot stand behind. One undescribable
template cannot fail a list covering hundreds of engagements.

**A stale catalog** could leave an engagement briefly ahead of the known latest. The comparison is
written so an empty "published after" set means up to date, making that safe by construction.

**Tradeoffs.** The net effect loses the narrative of *why* each intermediate version changed
something; per-version diffs remain available, and the contract can carry an optional breakdown
without changing the default. Deriving rather than materialising costs a catalog lookup per read,
so the catalog must stay small — templates in the thousands would force a revisit. Server-side
rendering means wording changes need a deploy rather than a client release; in exchange there is
exactly one account of any change, worth more here than the flexibility.

Assumptions are listed in [`SUBMISSION.md`](SUBMISSION.md).
