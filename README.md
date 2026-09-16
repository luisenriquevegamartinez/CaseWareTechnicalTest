# Supporting Fixtures

The fixtures are illustrative and do not prescribe the architecture.

- `data/templates.json` defines template versions and publication times.
- `data/engagements.json` contains representative engagements.
- `data/template-diff-*.json` contains custom raw diff examples. These are **not RFC 6902 JSON Patch**: `add` uses `value`, `replace` uses `oldValue`/`newValue`, and `remove` uses `oldValue`.
- `data/template-fragment-audit-ca-v5.json` is a small example of template content.
- `DESIGN.md` and `SUBMISSION.md` are optional skeletons.

For the targeted implementation, treat each engagement's `templateVersion` as its current baseline; the fixtures do not model prior apply/decline history.

Consecutive diffs are provided for engagements that are behind the latest version. A collapsed REVIEW-CA v6→v8 diff is also included to show that non-consecutive versions can be compared directly; it does not prescribe how accumulated updates should be represented.
