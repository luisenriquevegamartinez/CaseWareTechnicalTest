# Submission Notes

## Assumptions Made

- **Each engagement's recorded `templateVersion` is its baseline**, with no prior apply/decline
  history, as the brief and fixture README state.
- **The diff service can compare any two versions**, not only consecutive ones. The brief says
  "two different versions", and `template-diff-review-ca-v6-v8.json` demonstrates it. The design
  depends on this: the consecutive chain is never folded by hand.
- **The supplied diff format is not RFC 6902 JSON Patch.** `add` carries `value`, `replace` carries
  `oldValue`/`newValue`, `remove` carries `oldValue`. RFC 6902 has no `oldValue` at all — it is a
  forward-only instruction set. The domain models the real format, because "the threshold changed
  from 4.5% to 4.0%" cannot be written without the old value.
- **Template metadata is small enough to cache.** The whole read path depends on it: a few
  templates updated weekly. Templates in the thousands would force a different design.
- **Hooks exist on both systems**, as granted: template publish, and engagement created / applied /
  declined.
- **Section display names** are not present in the fixtures, so the renderer derives them from
  section keys (`riskAssessment` → "Risk assessment"). In production these would come from the
  template's own section metadata, and the derivation is the fallback.
- **One fixture inconsistency, treated as abridgement:** the v4→v5 diff adds the
  `subsequent-events` checklist *with* an `items` array, while `template-fragment-audit-ca-v5.json`
  shows it *without*. The fragment is labelled a small example, so I assumed it is abridged rather
  than contradictory.
- **Java 21 rather than 25.** Everything used — records, sealed interfaces, pattern matching for
  switch — exists in 21, and nothing would be written differently on 25. Targeting the widely
  installed LTS means the excerpt compiles wherever it is reviewed.
- **Fixtures are transcribed from `/data`, not parsed.** Both excerpts restate the sample data in
  their own language rather than reading the JSON, keeping a domain excerpt free of a JSON
  dependency it does not otherwise need. The Angular fixture's change descriptions are the exact
  strings the Java renderer produces for those ranges, which is the coherence check between the two.
- **`SUMMARY_UNAVAILABLE` carries an `incidentId` in the contract but not in the domain.** The
  domain reports the reason; the correlation id is attached at the API boundary, where such
  identifiers belong.

## AI Usage

I used Claude throughout, conversationally and agentically. The architecture is mine; the
throughput is not.

### Where AI helped

- **Reading the fixtures exhaustively and fast.** It surfaced the difference between the
  consecutive REVIEW-CA chain and the collapsed v6→v8 diff — six changes versus five, with the
  intermediate tolerance of 0.12 absent from the collapsed form. I recognised that as the core of
  the accumulation question; having it handed to me in the first few minutes is what left room to
  think about it rather than find it.
- **Drafting volume against decisions I had already made.** Once the contract was settled, the
  domain classes, the renderer's phrasing rules and the Angular components were largely generated
  and then edited. The same for the ASCII diagram and the first pass of this document.
- **Being a second reader.** It caught real defects in my own prose and types, listed below.
- **Verification, which matters more than generation.** Every claim in this submission was checked
  by running something: `javac -Xlint:all` and the JUnit runner for Part 2, `tsc` under `strict`
  and vitest for Part 3.

### Where I corrected, rewrote, or ignored AI output

- **I rejected a fan-out-on-publish design that I had sketched first**, and that the model was
  happy to build. Marking affected engagements when a template is published reads naturally, but
  engagement data is per-customer-database: it is one write per tenant, it can fail halfway, and it
  produces a flag that can lie. Behind-ness is a comparison between a baseline and a publication
  history, both already cheap. I derived it instead, and publishing now touches no tenant data.
- **I rejected folding the consecutive diff chain.** The plausible-looking approach is to apply
  v6→v7 then v7→v8 and merge. That means re-solving add-then-remove, replace chains and path
  collisions — and the brief already grants comparison of any two versions.
- **`versionsBehind` had a self-contradictory definition** in my first contract draft: "strictly
  between baseline and latest, inclusive of latest". Between excludes the endpoints. Since the Java
  test was going to be written from that sentence, I pinned it as `baseline < v <= latest`.
- **The `sourcePath` rationale contradicted itself.** I had written that shipping raw diffs would
  "expose template internals", while every change carried a raw diff path. The real reason is
  different: a client that interprets paths scatters the phrasing rules across every consumer. I
  kept the field, for traceability, and fixed the justification.
- **`UNKNOWN` was nearly decoration.** Baseline and engagement name arrive in the same event, so if
  a row exists a baseline exists. The state only earns its place on day one of an existing product,
  when the projection is empty and backfilling costs a minute per file. Finding the scenario is
  what turned it into a load-bearing state and put backfill into the design document.
- **The change-summary wording is mine, rule by rule.** Generated first drafts were fluent and
  wrong in the way that matters: "the threshold was reduced" where the number belongs, plurals that
  did not agree, percentages rendered as `4` instead of `4.0%`.
- **The first design document ran close to three pages** against a stated two-page maximum. I cut
  it by compression, not by dropping decisions.

### How I would guide other engineers using AI on this system

- **Settle the contract before generating anything.** Most of the value here came from the types
  existing first; both implementations then derived from one vocabulary. Generating a server and a
  client independently produces two plausible systems that disagree.
- **Read the fixture before accepting code that parses it.** The diff format here looks like JSON
  Patch and is not. An assistant asked to "parse this diff" will reach for RFC 6902 and produce
  code that compiles, passes a shallow test, and silently drops every `oldValue`.
- **Draw a line around what a model may originate.** Structure, scaffolding and tests: yes. Any
  number, threshold or methodology statement a practitioner will rely on: no.
- **Make the assistant verify, not assert.** "It compiles" is worth nothing until it has compiled.
  Every claim in this submission is backed by a command that ran.

### Where AI should not be trusted in this domain

In the change summary itself, and in anything downstream of it. A hallucinated materiality
threshold is indistinguishable from a correct one and would be relied on in a professional
judgement that has to be defensible years later. That is why the renderer is deterministic, why
every number is derived from the diff by code, and why `rendererVersion` is stamped on each summary
so any sentence can be reproduced.

A language model is genuinely useful one layer back: drafting and critiquing the phrasing rules
offline, where a human reviews them and the output is frozen. It should not be in the request path.

The same caution applies to the domain itself. I do not have audit expertise, so the judgement
about which changes are "notable" — threshold moves, removals of required procedures — is a
plausible first cut that needs a subject-matter expert, not an assistant, to confirm.

## Approximate Time Spent

**Approximately 2 hours**, roughly: 45 minutes on the brief, fixtures and settling the approach;
25 on the contract; 15 on the Java domain and its tests; 15 on the Angular excerpt; 10 on the design
document; the remainder on these notes and verification.

## What I Would Do Next

In priority order, and roughly why:

1. **Projection correctness under event loss and reordering.** A silently wrong badge is the worst
   failure this system has, and it is the least visible. I would add monotonic per-engagement event
   versioning and the reconciliation job described in the design document before anything else.
2. **Contract conformance tests**, so the Java model and the client state cannot drift from
   `api-contract.ts`. Today the shared types are a convention held up by review.
3. **Resolve element labels for field-level changes.** "Help text was removed from question 4" is
   truthful but thin; given the baseline template content, the renderer could say which question.
4. **Take the phrasing rules to an audit SME.** The `NOTABLE` classification is the part of this
   most likely to be wrong in a way no test would catch.
5. **The decision path end to end** — idempotency on retry, and the `409 SUPERSEDED` flow, which is
   currently specified in the contract but exercised only in the client store.

What I would *not* add without being asked: filtering, sorting, bulk actions, or a per-version
breakdown of accumulated changes. The last one is tempting and I deliberately left it out — the net
effect is the correct default, and the contract can carry an optional breakdown later without
changing it.
