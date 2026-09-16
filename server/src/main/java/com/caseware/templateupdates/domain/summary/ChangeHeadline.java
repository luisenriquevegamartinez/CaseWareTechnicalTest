package com.caseware.templateupdates.domain.summary;

import java.util.List;

/**
 * At-a-glance counts, so a practitioner knows the size of the decision before reading it.
 *
 * <p>{@code notable} is tracked separately because it is the one number that must not be buried.
 * A large update with thirty wording tweaks and one lowered materiality threshold is, in
 * practice, a decision about the threshold.
 */
public record ChangeHeadline(int totalChanges, int added, int modified, int removed, int notable) {

    public static ChangeHeadline from(List<HumanChange> changes) {
        return new ChangeHeadline(
                changes.size(),
                count(changes, HumanChange.Kind.ADDED),
                count(changes, HumanChange.Kind.MODIFIED),
                count(changes, HumanChange.Kind.REMOVED),
                (int) changes.stream()
                        .filter(change -> change.significance() == HumanChange.Significance.NOTABLE)
                        .count());
    }

    private static int count(List<HumanChange> changes, HumanChange.Kind kind) {
        return (int) changes.stream().filter(change -> change.kind() == kind).count();
    }
}
