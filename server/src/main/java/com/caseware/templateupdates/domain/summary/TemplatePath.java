package com.caseware.templateupdates.domain.summary;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * A diff path, classified into the shapes the renderer knows how to describe.
 *
 * <p>Paths are structural, not arbitrary: template content is organised into sections holding
 * collections of elements, plus named settings. Recognising that structure is what turns
 * {@code /sections/materiality/guidance/thresholdPercent} into a sentence about a materiality
 * threshold instead of a path printed on screen.
 *
 * <p>Anything unrecognised becomes {@link Unclassified} and is described in deliberately generic
 * terms. Degrading to a vaguer sentence is correct; guessing at a structure the template does not
 * have would produce a confident wrong one.
 */
public sealed interface TemplatePath {

    /** Collections whose next path segment identifies an element. */
    Set<String> ELEMENT_COLLECTIONS = Set.of("questions", "checklists", "procedures");

    /** Groups whose next path segment is a named setting rather than an element id. */
    Set<String> SETTING_GROUPS = Set.of("guidance", "scoring");

    String raw();

    /** Key used to group changes; {@code "metadata"} for template-level changes. */
    String groupKey();

    /** The kind of thing that changed, in the practitioner's language. */
    String elementType();

    /** A whole element, e.g. {@code /sections/planning/questions/7}. */
    record SectionElement(String raw, String sectionKey, String collection, String elementId)
            implements TemplatePath {
        @Override
        public String groupKey() {
            return sectionKey;
        }

        @Override
        public String elementType() {
            return describeCollection(collection);
        }
    }

    /** One field of an element, e.g. {@code /sections/planning/questions/3/label}. */
    record SectionElementField(String raw, String sectionKey, String collection, String elementId, String field)
            implements TemplatePath {
        @Override
        public String groupKey() {
            return sectionKey;
        }

        @Override
        public String elementType() {
            return describeCollection(collection);
        }
    }

    /** A named setting, e.g. {@code /sections/materiality/guidance/thresholdPercent}. */
    record SectionSetting(String raw, String sectionKey, String group, String settingKey)
            implements TemplatePath {
        @Override
        public String groupKey() {
            return sectionKey;
        }

        @Override
        public String elementType() {
            return group.equals("scoring") ? "scoring setting" : "guidance setting";
        }
    }

    /** A template-level field, e.g. {@code /metadata/displayName}. */
    record TemplateMetadata(String raw, String field) implements TemplatePath {
        @Override
        public String groupKey() {
            return "metadata";
        }

        @Override
        public String elementType() {
            return "template detail";
        }
    }

    /** A path whose structure the renderer does not recognise. */
    record Unclassified(String raw) implements TemplatePath {
        @Override
        public String groupKey() {
            return "other";
        }

        @Override
        public String elementType() {
            return "item";
        }
    }

    static TemplatePath parse(String raw) {
        List<String> segments = Arrays.stream(raw.split("/"))
                .filter(segment -> !segment.isBlank())
                .toList();

        if (segments.size() >= 2 && segments.get(0).equals("metadata")) {
            return new TemplateMetadata(raw, segments.get(1));
        }

        if (segments.size() >= 3 && segments.get(0).equals("sections")) {
            String sectionKey = segments.get(1);
            String third = segments.get(2);

            if (ELEMENT_COLLECTIONS.contains(third) && segments.size() >= 4) {
                String elementId = segments.get(3);
                return segments.size() >= 5
                        ? new SectionElementField(raw, sectionKey, third, elementId, segments.get(segments.size() - 1))
                        : new SectionElement(raw, sectionKey, third, elementId);
            }

            if (SETTING_GROUPS.contains(third) && segments.size() >= 4) {
                return new SectionSetting(raw, sectionKey, third, segments.get(3));
            }

            if (segments.size() == 3) {
                // An element sitting directly under a section, e.g. a legacy risk matrix.
                return new SectionElement(raw, sectionKey, "", third);
            }
        }

        return new Unclassified(raw);
    }

    private static String describeCollection(String collection) {
        return switch (collection) {
            case "questions" -> "question";
            case "checklists" -> "checklist";
            case "procedures" -> "procedure";
            default -> "item";
        };
    }
}
