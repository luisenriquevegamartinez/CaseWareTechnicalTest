package com.caseware.templateupdates.domain.summary;

import java.util.List;
import java.util.Objects;

/**
 * Changes grouped by the part of the engagement they affect.
 *
 * <p>Grouping is the main thing that makes an accumulated update reviewable. "Five changes"
 * is a number; "three in Planning, two in Materiality" is something a practitioner can reason
 * about against the work already done in those sections.
 *
 * @param sectionKey   stable key from the template structure, used for ordering and deep links
 * @param sectionLabel the section name as the practitioner knows it
 */
public record ChangeGroup(String sectionKey, String sectionLabel, List<HumanChange> changes) {

    public ChangeGroup {
        Objects.requireNonNull(sectionKey, "sectionKey must not be null");
        Objects.requireNonNull(sectionLabel, "sectionLabel must not be null");
        changes = List.copyOf(Objects.requireNonNull(changes, "changes must not be null"));
    }
}
