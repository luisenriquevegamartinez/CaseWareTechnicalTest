package com.caseware.templateupdates.domain;

import java.util.Objects;

/** Identifier of an engagement file, e.g. {@code ENG-1003}. */
public record EngagementId(String value) {

    public EngagementId {
        Objects.requireNonNull(value, "engagement id must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("engagement id must not be blank");
        }
    }

    public static EngagementId of(String value) {
        return new EngagementId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
