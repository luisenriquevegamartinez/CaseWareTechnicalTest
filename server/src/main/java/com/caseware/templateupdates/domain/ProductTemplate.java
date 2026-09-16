package com.caseware.templateupdates.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A product template and its publication history.
 *
 * <p>Shared across all customer firms: the same {@code AUDIT-CA} history backs every firm's
 * engagements. This is what makes the derived answers cheap — the catalog is small enough to
 * cache, so deciding whether an engagement is behind never touches tenant storage.
 */
public record ProductTemplate(TemplateId id, String displayName, List<PublishedVersion> publishedVersions) {

    public ProductTemplate {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(publishedVersions, "publishedVersions must not be null");
        if (publishedVersions.isEmpty()) {
            throw new IllegalArgumentException("template " + id + " must have at least one published version");
        }
        publishedVersions = publishedVersions.stream()
                .sorted(Comparator.comparing(PublishedVersion::version))
                .toList();
    }

    /** The most recently published version. */
    public TemplateVersion latestVersion() {
        return publishedVersions.get(publishedVersions.size() - 1).version();
    }

    public PublishedVersion latestPublishedVersion() {
        return publishedVersions.get(publishedVersions.size() - 1);
    }

    /**
     * Every version published after {@code baseline}, oldest first — that is, versions
     * {@code v} where {@code baseline < v <= latest}.
     *
     * <p>The size of this list is the engagement's "versions behind" count. An engagement on
     * AUDIT-CA v3 with a latest of v5 is two versions behind, because v4 and v5 were both
     * published after it.
     */
    public List<PublishedVersion> versionsPublishedAfter(TemplateVersion baseline) {
        return publishedVersions.stream()
                .filter(published -> published.version().isAfter(baseline))
                .toList();
    }
}
