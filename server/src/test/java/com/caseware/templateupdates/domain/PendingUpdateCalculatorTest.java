package com.caseware.templateupdates.domain;

import com.caseware.templateupdates.domain.summary.ChangeSummaryRenderer;
import com.caseware.templateupdates.domain.summary.HumanChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Three focused tests covering the states an engagement can be in: current, one version behind,
 * and several versions behind with updates accumulated.
 */
class PendingUpdateCalculatorTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-16T10:00:00Z"), ZoneOffset.UTC);

    private final Fixtures.RecordingDiffSource diffSource = new Fixtures.RecordingDiffSource()
            .with(Fixtures.auditCaV4ToV5())
            .with(Fixtures.reviewCaV6ToV8());

    private final PendingUpdateCalculator calculator = new PendingUpdateCalculator(
            new Fixtures.InMemoryProjection(Fixtures.eng1001(), Fixtures.eng1002(), Fixtures.eng1007()),
            new Fixtures.InMemoryCatalog(Fixtures.auditCa(), Fixtures.reviewCa()),
            diffSource,
            new ChangeSummaryRenderer(FIXED_CLOCK));

    @Test
    @DisplayName("an engagement on the latest version has nothing pending, and costs no diff")
    void engagementOnLatestVersionIsUpToDate() {
        PendingUpdateState state = calculator.evaluate(EngagementId.of("ENG-1001"));

        PendingUpdateState.UpToDate upToDate =
                assertInstanceOf(PendingUpdateState.UpToDate.class, state);
        assertEquals(TemplateVersion.of(5), upToDate.currentVersion());

        // Being up to date is decided by comparing the recorded baseline against the template's
        // publication history. No diff is requested, and nothing reads the engagement file.
        assertTrue(diffSource.requestedRanges.isEmpty());
    }

    @Test
    @DisplayName("an engagement one version behind gets a readable summary of that single step")
    void engagementOneVersionBehindIsSummarised() {
        PendingUpdateState state = calculator.evaluate(EngagementId.of("ENG-1002"));

        PendingUpdateState.UpdateAvailable available =
                assertInstanceOf(PendingUpdateState.UpdateAvailable.class, state);
        assertEquals(TemplateVersion.of(4), available.baselineVersion());
        assertEquals(1, available.versionsBehind());
        assertEquals(TemplateVersion.of(5), available.latestVersion());

        List<HumanChange> changes = available.summary().allChanges();
        assertEquals(3, changes.size());
        assertEquals(3, available.summary().headline().totalChanges());

        // The materiality threshold is the change a practitioner must not miss, so it is rendered
        // with its unit and flagged, not left as a bare number among wording tweaks.
        HumanChange threshold = changeAt(changes, "/sections/materiality/guidance/thresholdPercent");
        assertEquals("Threshold in Materiality changed from 4.5% to 4.0%.", threshold.description());
        assertEquals(HumanChange.Significance.NOTABLE, threshold.significance());
        assertEquals("4.5%", threshold.valueChange().orElseThrow().before());
        assertEquals("4.0%", threshold.valueChange().orElseThrow().after());

        HumanChange checklist = changeAt(changes, "/sections/completion/checklists/subsequent-events");
        assertEquals(
                "A new checklist was added: “Subsequent events review”. It has 3 items.",
                checklist.description());
    }

    @Test
    @DisplayName("accumulated updates are summarised as one net change, never as a replayed chain")
    void accumulatedUpdatesAreSummarisedAsNetEffect() {
        PendingUpdateState state = calculator.evaluate(EngagementId.of("ENG-1007"));

        PendingUpdateState.UpdateAvailable available =
                assertInstanceOf(PendingUpdateState.UpdateAvailable.class, state);
        assertEquals(2, available.versionsBehind());
        assertEquals(TemplateVersion.of(8), available.latestVersion());

        // The skipped versions are still reported, as context for how much has piled up.
        assertEquals(
                List.of(TemplateVersion.of(7), TemplateVersion.of(8)),
                available.accumulatedVersions().stream().map(PublishedVersion::version).toList());

        // One request for the whole range. The consecutive chain is never folded by hand.
        assertEquals(List.of("REVIEW-CA v6->v8"), diffSource.requestedRanges);

        List<HumanChange> changes = available.summary().allChanges();
        assertEquals(5, changes.size());

        // The heart of the accumulation decision. Replaying v6->v7->v8 would show the analytics
        // tolerance moving 0.15 to 0.12 and then 0.12 to 0.10: six changes, one of them describing
        // a value this engagement will never hold. The net effect is a single move to 0.10.
        HumanChange tolerance = changeAt(changes, "/sections/analytics/procedures/2/tolerance");
        assertEquals("Tolerance for procedure 2 changed from 0.15 to 0.1.", tolerance.description());
        assertFalse(
                changes.stream().anyMatch(change -> change.description().contains("0.12")),
                "no superseded intermediate value should reach the practitioner");

        // Grouped by section, so a practitioner can weigh the update against the work already done
        // rather than reading a flat list of five unrelated sentences.
        assertEquals(
                List.of("Template details", "Inquiries", "Analytics", "Completion"),
                available.summary().groups().stream()
                        .map(group -> group.sectionLabel())
                        .toList());
    }

    private static HumanChange changeAt(List<HumanChange> changes, String sourcePath) {
        return changes.stream()
                .filter(change -> change.sourcePath().equals(sourcePath))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no change rendered for " + sourcePath));
    }
}
