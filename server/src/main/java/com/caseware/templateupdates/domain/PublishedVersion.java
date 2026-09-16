package com.caseware.templateupdates.domain;

import java.time.Instant;
import java.util.Objects;

/** One entry in a template's publication history. */
public record PublishedVersion(TemplateVersion version, Instant publishedAt) {

    public PublishedVersion {
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(publishedAt, "publishedAt must not be null");
    }
}
