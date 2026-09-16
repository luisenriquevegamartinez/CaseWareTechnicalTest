package com.caseware.templateupdates.domain.summary;

import com.caseware.templateupdates.domain.TemplateId;
import com.caseware.templateupdates.domain.TemplateVersion;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The human-readable account of what an update would change, as shown to a practitioner.
 *
 * <p>Keyed by {@code (templateId, fromVersion, toVersion)} and nothing else. Because product
 * templates are shared across firms, one rendering serves every customer on that range.
 *
 * <p>Everything here is produced by deterministic rules over the raw diff. No generative model
 * originates a value: in audit, a materiality threshold that reads 4.0% when the template says
 * 4.5% is a defensibility problem, not a wording problem. An LLM is useful for proposing and
 * reviewing phrasing rules offline; the rules it helps write are then frozen, versioned by
 * {@code rendererVersion}, and executed the same way every time.
 */
public record ChangeSummary(
        TemplateId templateId,
        TemplateVersion fromVersion,
        TemplateVersion toVersion,
        Instant generatedAt,
        String rendererVersion,
        ChangeHeadline headline,
        List<ChangeGroup> groups) {

    public ChangeSummary {
        Objects.requireNonNull(templateId, "templateId must not be null");
        Objects.requireNonNull(fromVersion, "fromVersion must not be null");
        Objects.requireNonNull(toVersion, "toVersion must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        Objects.requireNonNull(rendererVersion, "rendererVersion must not be null");
        Objects.requireNonNull(headline, "headline must not be null");
        groups = List.copyOf(Objects.requireNonNull(groups, "groups must not be null"));
    }

    /** Every change across all groups, flattened. Convenience for tests and exports. */
    public List<HumanChange> allChanges() {
        return groups.stream().flatMap(group -> group.changes().stream()).toList();
    }
}
