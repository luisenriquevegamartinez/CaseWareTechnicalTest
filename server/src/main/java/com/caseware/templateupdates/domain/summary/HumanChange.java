package com.caseware.templateupdates.domain.summary;

import java.util.Objects;
import java.util.Optional;

/**
 * One change, described in language a practitioner can act on.
 *
 * <p>{@code sourcePath} is carried for traceability: when a firm asks why a change was described
 * a particular way, the sentence can be tied back to the exact diff entry it came from. It is a
 * correlation string for support and export contexts, never something a client parses.
 */
public record HumanChange(
        String id,
        Kind kind,
        String elementType,
        String description,
        Optional<ValueChange> valueChange,
        String sourcePath,
        Significance significance) {

    public HumanChange {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(elementType, "elementType must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(valueChange, "valueChange must not be null");
        Objects.requireNonNull(sourcePath, "sourcePath must not be null");
        Objects.requireNonNull(significance, "significance must not be null");
    }

    public enum Kind {
        ADDED, MODIFIED, REMOVED
    }

    public enum Significance {
        NORMAL,
        /** Warrants attention: a threshold moved, or required content was removed. */
        NOTABLE
    }

    /**
     * Before and after, pre-formatted with their units so that number formatting lives in one
     * place rather than being re-implemented by every client.
     */
    public record ValueChange(String before, String after) {
        public ValueChange {
            Objects.requireNonNull(before, "before must not be null");
            Objects.requireNonNull(after, "after must not be null");
        }
    }
}
