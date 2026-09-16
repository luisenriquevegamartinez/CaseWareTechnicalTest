package com.caseware.templateupdates.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A fragment of template content appearing inside a diff entry.
 *
 * <p>Template content is arbitrary structured JSON. Rather than passing {@code Object} around
 * and casting at the point of use, the three shapes the renderer actually distinguishes are
 * made explicit: a leaf value it can format, an object it can read a label from, and a list it
 * can count. Anything the renderer cannot classify degrades to a leaf, which is the safe
 * outcome — it produces a vaguer sentence, never a wrong one.
 */
public sealed interface TemplateValue {

    /** A scalar leaf: text, number or boolean. */
    record Leaf(Object value) implements TemplateValue {
        public Leaf {
            Objects.requireNonNull(value, "leaf value must not be null");
        }
    }

    /** A structured element, e.g. a question or a checklist. */
    record Node(Map<String, TemplateValue> fields) implements TemplateValue {
        public Node {
            fields = Map.copyOf(Objects.requireNonNull(fields, "fields must not be null"));
        }

        /** The element's user-facing label, when it carries one. */
        public Optional<String> label() {
            return text("label");
        }

        public Optional<String> text(String field) {
            return Optional.ofNullable(fields.get(field))
                    .filter(Leaf.class::isInstance)
                    .map(value -> ((Leaf) value).value())
                    .map(Object::toString);
        }

        public boolean isRequired() {
            return Optional.ofNullable(fields.get("required"))
                    .filter(Leaf.class::isInstance)
                    .map(value -> ((Leaf) value).value())
                    .filter(Boolean.class::isInstance)
                    .map(Boolean.class::cast)
                    .orElse(false);
        }

        /** Number of entries in a list-valued field, e.g. a checklist's {@code items}. */
        public Optional<Integer> sizeOf(String field) {
            return Optional.ofNullable(fields.get(field))
                    .filter(Items.class::isInstance)
                    .map(value -> ((Items) value).values().size());
        }
    }

    /** An ordered list, e.g. a checklist's items or a question's options. */
    record Items(List<TemplateValue> values) implements TemplateValue {
        public Items {
            values = List.copyOf(Objects.requireNonNull(values, "values must not be null"));
        }
    }

    // --- Construction helpers, used by fixtures and by the diff adapter. ---

    static TemplateValue of(Object scalar) {
        return new Leaf(scalar);
    }

    static TemplateValue node(Map<String, TemplateValue> fields) {
        return new Node(fields);
    }

    static TemplateValue items(String... values) {
        return new Items(List.of(values).stream().map(TemplateValue::of).toList());
    }
}
