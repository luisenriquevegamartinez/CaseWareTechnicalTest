package com.caseware.templateupdates.domain;

import java.util.Objects;

/**
 * Identifier of a product template, e.g. {@code AUDIT-CA}.
 *
 * <p>A typed identifier rather than a bare {@code String}: engagement ids, template ids and
 * section keys are all strings, and the compiler should be the thing that stops them being
 * passed in the wrong order.
 */
public record TemplateId(String value) {

    public TemplateId {
        Objects.requireNonNull(value, "template id must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("template id must not be blank");
        }
    }

    public static TemplateId of(String value) {
        return new TemplateId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
