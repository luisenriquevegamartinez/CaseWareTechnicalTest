package com.caseware.templateupdates.domain.summary;

import com.caseware.templateupdates.domain.TemplateValue;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Turns raw template values into the strings a practitioner reads.
 *
 * <p>Units are inferred from the field name — a key ending in {@code Percent} is a percentage,
 * one ending in {@code Months} is a duration — which keeps formatting deterministic and
 * reviewable rather than hard-coded per field. A threshold rendered as {@code 4} instead of
 * {@code 4.0%} is not a cosmetic defect in this domain, so the rules live in one place where
 * they can be tested.
 */
final class ValueFormatter {

    private ValueFormatter() {
    }

    enum Unit {
        PERCENT, MONTHS, DAYS, NONE
    }

    /** Infers the unit from a field or setting key, e.g. {@code thresholdPercent} to PERCENT. */
    static Unit unitOf(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.endsWith("percent")) {
            return Unit.PERCENT;
        }
        if (lower.endsWith("months")) {
            return Unit.MONTHS;
        }
        if (lower.endsWith("days")) {
            return Unit.DAYS;
        }
        return Unit.NONE;
    }

    /**
     * Strips the unit suffix and splits camelCase into words, so {@code reassessmentFrequencyMonths}
     * becomes "Reassessment frequency".
     */
    static String humanise(String key) {
        String withoutUnit = key
                .replaceAll("(?i)(Percent|Months|Days)$", "")
                .replaceAll("[-_]", " ");
        String spaced = withoutUnit
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (spaced.isEmpty()) {
            return key;
        }
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /** Section keys become section labels, e.g. {@code riskAssessment} to "Risk assessment". */
    static String sectionLabel(String sectionKey) {
        return switch (sectionKey) {
            case "metadata" -> "Template details";
            case "other" -> "Other changes";
            default -> humanise(sectionKey);
        };
    }

    static String format(TemplateValue value, Unit unit) {
        if (value instanceof TemplateValue.Leaf leaf) {
            Object raw = leaf.value();
            if (raw instanceof Number number) {
                return formatNumber(number, unit);
            }
            if (raw instanceof Boolean flag) {
                return flag ? "yes" : "no";
            }
            return raw.toString();
        }
        // Structured content has no single-line representation; callers describe it instead.
        return "updated content";
    }

    private static String formatNumber(Number number, Unit unit) {
        BigDecimal decimal = new BigDecimal(number.toString()).stripTrailingZeros();
        String plain = decimal.toPlainString();
        return switch (unit) {
            // Keep one decimal on whole percentages: "4.0%" reads as a threshold, "4%" as a rounding.
            case PERCENT -> (decimal.scale() <= 0 ? plain + ".0" : plain) + "%";
            case MONTHS -> plain + (plain.equals("1") ? " month" : " months");
            case DAYS -> plain + (plain.equals("1") ? " day" : " days");
            case NONE -> plain;
        };
    }

    static String quote(String text) {
        return "“" + text + "”";
    }
}
