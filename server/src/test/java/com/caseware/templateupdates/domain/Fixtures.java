package com.caseware.templateupdates.domain;

import com.caseware.templateupdates.port.DiffLookup;
import com.caseware.templateupdates.port.EngagementBaselineProjection;
import com.caseware.templateupdates.port.TemplateCatalog;
import com.caseware.templateupdates.port.TemplateDiffSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Test data transcribed from the supplied fixture pack under {@code /data}, plus in-memory
 * stand-ins for the three ports.
 *
 * <p>The diffs below follow the fixture format exactly, which is <em>not</em> RFC 6902 JSON
 * Patch: {@code add} carries a value, {@code replace} carries both old and new, and
 * {@code remove} carries the value that went away.
 */
final class Fixtures {

    static final TemplateId AUDIT_CA = TemplateId.of("AUDIT-CA");
    static final TemplateId REVIEW_CA = TemplateId.of("REVIEW-CA");

    private Fixtures() {
    }

    // --- Templates, from data/templates.json --------------------------------

    static ProductTemplate auditCa() {
        return new ProductTemplate(AUDIT_CA, "Canadian Audit Engagement", List.of(
                new PublishedVersion(TemplateVersion.of(3), Instant.parse("2026-05-12T13:00:00Z")),
                new PublishedVersion(TemplateVersion.of(4), Instant.parse("2026-07-07T13:00:00Z")),
                new PublishedVersion(TemplateVersion.of(5), Instant.parse("2026-08-18T13:00:00Z"))));
    }

    static ProductTemplate reviewCa() {
        return new ProductTemplate(REVIEW_CA, "Canadian Review Engagement", List.of(
                new PublishedVersion(TemplateVersion.of(6), Instant.parse("2026-05-20T13:00:00Z")),
                new PublishedVersion(TemplateVersion.of(7), Instant.parse("2026-07-21T13:00:00Z")),
                new PublishedVersion(TemplateVersion.of(8), Instant.parse("2026-08-25T13:00:00Z"))));
    }

    // --- Engagements, from data/engagements.json ----------------------------

    /** On the latest AUDIT-CA version. */
    static EngagementBaseline eng1001() {
        return new EngagementBaseline(EngagementId.of("ENG-1001"), "Northstar Manufacturing 2026",
                AUDIT_CA, TemplateVersion.of(5));
    }

    /** One AUDIT-CA version behind. */
    static EngagementBaseline eng1002() {
        return new EngagementBaseline(EngagementId.of("ENG-1002"), "Maple Ridge Foods 2026",
                AUDIT_CA, TemplateVersion.of(4));
    }

    /** Two REVIEW-CA versions behind — the accumulated case. */
    static EngagementBaseline eng1007() {
        return new EngagementBaseline(EngagementId.of("ENG-1007"), "Bluewater Hospitality 2026",
                REVIEW_CA, TemplateVersion.of(6));
    }

    // --- Diffs, from data/template-diff-*.json ------------------------------

    /** data/template-diff-audit-ca-v4-v5.json */
    static TemplateDiff auditCaV4ToV5() {
        return new TemplateDiff(AUDIT_CA, TemplateVersion.of(4), TemplateVersion.of(5),
                Instant.parse("2026-08-18T13:04:41Z"), List.of(
                new DiffChange.Replaced(
                        "/sections/planning/questions/3/label",
                        TemplateValue.of("Has management identified significant estimates?"),
                        TemplateValue.of("Has management identified significant accounting estimates "
                                + "and related estimation uncertainty?")),
                new DiffChange.Replaced(
                        "/sections/materiality/guidance/thresholdPercent",
                        TemplateValue.of(4.5),
                        TemplateValue.of(4.0)),
                new DiffChange.Added(
                        "/sections/completion/checklists/subsequent-events",
                        TemplateValue.node(fields(
                                "id", TemplateValue.of("CHK-SE-01"),
                                "label", TemplateValue.of("Subsequent events review"),
                                "items", TemplateValue.items(
                                        "Confirm inquiry with management",
                                        "Evaluate events requiring adjustment",
                                        "Document the conclusion"))))));
    }

    /**
     * data/template-diff-review-ca-v6-v8.json — the collapsed diff for an engagement two
     * versions behind.
     *
     * <p>Worth comparing against the consecutive pair: v6 to v7 moves the analytics tolerance
     * from 0.15 to 0.12, and v7 to v8 moves it from 0.12 to 0.10. Asked for the whole range at
     * once, the template service reports a single move from 0.15 to 0.10. The 0.12 was never
     * present in this engagement and never will be.
     */
    static TemplateDiff reviewCaV6ToV8() {
        return new TemplateDiff(REVIEW_CA, TemplateVersion.of(6), TemplateVersion.of(8),
                Instant.parse("2026-08-25T13:04:55Z"), List.of(
                new DiffChange.Replaced(
                        "/metadata/displayName",
                        TemplateValue.of("Canadian Review Engagement"),
                        TemplateValue.of("Canadian Review Engagement 2026")),
                new DiffChange.Added(
                        "/sections/inquiries/questions/12",
                        TemplateValue.node(fields(
                                "id", TemplateValue.of("Q-INQ-012"),
                                "type", TemplateValue.of("text"),
                                "label", TemplateValue.of("Describe any events after the reporting date "
                                        + "that may require adjustment or disclosure.")))),
                new DiffChange.Replaced(
                        "/sections/analytics/procedures/2/tolerance",
                        TemplateValue.of(0.15),
                        TemplateValue.of(0.1)),
                new DiffChange.Removed(
                        "/sections/inquiries/questions/4/helpText",
                        TemplateValue.of("Ask management to describe changes in accounting policies "
                                + "since the prior year.")),
                new DiffChange.Added(
                        "/sections/completion/checklists/going-concern",
                        TemplateValue.node(fields(
                                "id", TemplateValue.of("CHK-GC-01"),
                                "label", TemplateValue.of("Going concern evaluation"),
                                "items", TemplateValue.items(
                                        "Document management's assessment",
                                        "Evaluate contradictory evidence",
                                        "Record the practitioner's conclusion"))))));
    }

    private static Map<String, TemplateValue> fields(Object... keysAndValues) {
        Map<String, TemplateValue> map = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            map.put((String) keysAndValues[index], (TemplateValue) keysAndValues[index + 1]);
        }
        return map;
    }

    // --- In-memory ports ----------------------------------------------------

    static final class InMemoryProjection implements EngagementBaselineProjection {
        private final List<EngagementBaseline> baselines;

        InMemoryProjection(EngagementBaseline... baselines) {
            this.baselines = List.of(baselines);
        }

        @Override
        public Optional<EngagementBaseline> findBaseline(EngagementId engagementId) {
            return baselines.stream()
                    .filter(baseline -> baseline.engagementId().equals(engagementId))
                    .findFirst();
        }

        @Override
        public List<EngagementBaseline> findAllForFirm() {
            return baselines;
        }
    }

    static final class InMemoryCatalog implements TemplateCatalog {
        private final List<ProductTemplate> templates;

        InMemoryCatalog(ProductTemplate... templates) {
            this.templates = List.of(templates);
        }

        @Override
        public Optional<ProductTemplate> find(TemplateId templateId) {
            return templates.stream().filter(template -> template.id().equals(templateId)).findFirst();
        }
    }

    /** Records every range it is asked for, so tests can assert how the diff was requested. */
    static final class RecordingDiffSource implements TemplateDiffSource {
        private final Map<String, TemplateDiff> diffs = new LinkedHashMap<>();
        final List<String> requestedRanges = new ArrayList<>();

        RecordingDiffSource with(TemplateDiff diff) {
            diffs.put(key(diff.templateId(), diff.fromVersion(), diff.toVersion()), diff);
            return this;
        }

        @Override
        public DiffLookup diff(TemplateId templateId, TemplateVersion from, TemplateVersion to) {
            String range = key(templateId, from, to);
            requestedRanges.add(range);
            TemplateDiff diff = diffs.get(range);
            return diff == null ? new DiffLookup.Computing() : new DiffLookup.Available(diff);
        }

        private static String key(TemplateId templateId, TemplateVersion from, TemplateVersion to) {
            return "%s %s->%s".formatted(templateId, from, to);
        }
    }
}
