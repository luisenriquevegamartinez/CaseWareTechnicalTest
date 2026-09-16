package com.caseware.templateupdates.domain;

import java.util.Objects;

/**
 * A single entry in a raw template diff.
 *
 * <p><strong>This is deliberately not RFC 6902 JSON Patch.</strong> The diff format supplied
 * with this exercise is a custom shape: {@code add} carries {@code value}, {@code replace}
 * carries {@code oldValue} and {@code newValue}, and {@code remove} carries {@code oldValue}.
 * RFC 6902 has no {@code oldValue} at all — it is a forward-only instruction set, not a
 * description of a change. Modelling the real format is what makes a readable summary
 * possible: "the threshold changed from 4.5% to 4.0%" cannot be written without the old value.
 *
 * <p>A sealed interface rather than an {@code op} enum plus three nullable fields, so each
 * variant carries exactly the values that operation has. {@code Added} has no {@code oldValue}
 * field to accidentally read.
 */
public sealed interface DiffChange {

    /** JSON-pointer-style location of the change within the template. */
    String path();

    record Added(String path, TemplateValue value) implements DiffChange {
        public Added {
            Objects.requireNonNull(path, "path must not be null");
            Objects.requireNonNull(value, "value must not be null");
        }
    }

    record Replaced(String path, TemplateValue oldValue, TemplateValue newValue) implements DiffChange {
        public Replaced {
            Objects.requireNonNull(path, "path must not be null");
            Objects.requireNonNull(oldValue, "oldValue must not be null");
            Objects.requireNonNull(newValue, "newValue must not be null");
        }
    }

    record Removed(String path, TemplateValue oldValue) implements DiffChange {
        public Removed {
            Objects.requireNonNull(path, "path must not be null");
            Objects.requireNonNull(oldValue, "oldValue must not be null");
        }
    }
}
