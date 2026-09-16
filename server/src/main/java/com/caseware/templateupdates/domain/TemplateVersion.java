package com.caseware.templateupdates.domain;

/**
 * A published version of a product template.
 *
 * <p>Ordered, but deliberately not treated as a dense sequence: "two versions behind" is
 * answered by counting entries in the template's publication history, never by subtracting
 * version numbers. A template may in principle skip or retire a number, and arithmetic on
 * identifiers would quietly produce a wrong answer if it ever did.
 */
public record TemplateVersion(int value) implements Comparable<TemplateVersion> {

    public TemplateVersion {
        if (value < 1) {
            throw new IllegalArgumentException("template version must be positive, was " + value);
        }
    }

    public static TemplateVersion of(int value) {
        return new TemplateVersion(value);
    }

    public boolean isAfter(TemplateVersion other) {
        return compareTo(other) > 0;
    }

    public boolean isAtOrAfter(TemplateVersion other) {
        return compareTo(other) >= 0;
    }

    @Override
    public int compareTo(TemplateVersion other) {
        return Integer.compare(value, other.value);
    }

    @Override
    public String toString() {
        return "v" + value;
    }
}
