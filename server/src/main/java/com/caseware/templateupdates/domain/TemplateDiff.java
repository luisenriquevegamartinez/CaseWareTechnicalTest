package com.caseware.templateupdates.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The raw technical difference between two versions of one product template.
 *
 * <p>{@code fromVersion} and {@code toVersion} need not be consecutive. Where several updates
 * have accumulated, the diff is requested once for the whole range — baseline straight to
 * latest — rather than folding a chain of consecutive diffs.
 *
 * <p>That is not a shortcut, it is the correct answer. In the supplied fixtures REVIEW-CA moves
 * a tolerance 0.15 to 0.12 in v7 and 0.12 to 0.10 in v8. An engagement sitting on v6 will never
 * hold 0.12: the value it has today is 0.15 and the value it would get is 0.10. Presenting the
 * intermediate step would ask a practitioner to evaluate a state that cannot occur. The versions
 * that were skipped are still reported alongside the summary, as context.
 */
public record TemplateDiff(
        TemplateId templateId,
        TemplateVersion fromVersion,
        TemplateVersion toVersion,
        Instant generatedAt,
        List<DiffChange> changes) {

    public TemplateDiff {
        Objects.requireNonNull(templateId, "templateId must not be null");
        Objects.requireNonNull(fromVersion, "fromVersion must not be null");
        Objects.requireNonNull(toVersion, "toVersion must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        if (!toVersion.isAfter(fromVersion)) {
            throw new IllegalArgumentException(
                    "a diff must move forward: " + fromVersion + " to " + toVersion);
        }
        changes = List.copyOf(Objects.requireNonNull(changes, "changes must not be null"));
    }
}
