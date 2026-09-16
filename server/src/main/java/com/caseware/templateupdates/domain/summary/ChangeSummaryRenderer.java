package com.caseware.templateupdates.domain.summary;

import com.caseware.templateupdates.domain.DiffChange;
import com.caseware.templateupdates.domain.TemplateDiff;
import com.caseware.templateupdates.domain.TemplateValue;
import com.caseware.templateupdates.domain.summary.HumanChange.Kind;
import com.caseware.templateupdates.domain.summary.HumanChange.Significance;
import com.caseware.templateupdates.domain.summary.HumanChange.ValueChange;
import com.caseware.templateupdates.domain.summary.ValueFormatter.Unit;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns a raw template diff into the summary a practitioner reads.
 *
 * <p><strong>This runs on the server, and it is entirely rule-based.</strong> Both choices are
 * deliberate.
 *
 * <p>Server-side, because product templates are shared across firms: one rendering of
 * {@code (AUDIT-CA, v4, v5)} serves every customer sitting on that range, so the work is done
 * once rather than repeated in every browser. It also keeps the wording of a change to an audit
 * methodology in one place, where it can be versioned, tested, and reproduced when a firm asks
 * why a change was described the way it was.
 *
 * <p>Rule-based, because this is exactly where a language model should not be trusted. Every
 * number and structural fact here is derived from the diff by code. An LLM is genuinely useful
 * for drafting and critiquing these phrasing rules offline — and was used that way — but it never
 * runs in this path. A summary that reads "the materiality threshold moved to 4.0%" when the
 * template says 4.5% would be indistinguishable from a correct one and would be relied upon in a
 * professional judgement.
 *
 * <p>{@link #RENDERER_VERSION} is stamped onto every summary so that cached summaries can be
 * invalidated when the rules change, and so any rendered sentence can be reproduced later.
 */
public final class ChangeSummaryRenderer {

    public static final String RENDERER_VERSION = "1.0.0";

    private final Clock clock;

    public ChangeSummaryRenderer(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public ChangeSummaryRenderer() {
        this(Clock.systemUTC());
    }

    public ChangeSummary render(TemplateDiff diff) {
        Objects.requireNonNull(diff, "diff must not be null");

        List<Described> described = new ArrayList<>();
        for (int index = 0; index < diff.changes().size(); index++) {
            DiffChange change = diff.changes().get(index);
            TemplatePath path = TemplatePath.parse(change.path());
            described.add(new Described(path.groupKey(), describe("chg-" + (index + 1), path, change)));
        }

        return new ChangeSummary(
                diff.templateId(),
                diff.fromVersion(),
                diff.toVersion(),
                clock.instant(),
                RENDERER_VERSION,
                ChangeHeadline.from(described.stream().map(Described::change).toList()),
                group(described));
    }

    /** Groups by section, preserving the order sections first appear in the diff. */
    private List<ChangeGroup> group(List<Described> described) {
        Map<String, List<HumanChange>> bySection = new LinkedHashMap<>();
        for (Described entry : described) {
            bySection.computeIfAbsent(entry.groupKey(), key -> new ArrayList<>()).add(entry.change());
        }
        return bySection.entrySet().stream()
                .map(entry -> new ChangeGroup(
                        entry.getKey(),
                        ValueFormatter.sectionLabel(entry.getKey()),
                        entry.getValue()))
                .toList();
    }

    private HumanChange describe(String id, TemplatePath path, DiffChange change) {
        return switch (change) {
            case DiffChange.Added added -> describeAdded(id, path, added);
            case DiffChange.Replaced replaced -> describeReplaced(id, path, replaced);
            case DiffChange.Removed removed -> describeRemoved(id, path, removed);
        };
    }

    // --- Added -------------------------------------------------------------

    private HumanChange describeAdded(String id, TemplatePath path, DiffChange.Added added) {
        if (path instanceof TemplatePath.SectionSetting setting) {
            String name = ValueFormatter.humanise(setting.settingKey());
            String value = ValueFormatter.format(added.value(), ValueFormatter.unitOf(setting.settingKey()));
            return new HumanChange(
                    id,
                    Kind.ADDED,
                    path.elementType(),
                    "%s was added to %s, set to %s.".formatted(
                            name, ValueFormatter.sectionLabel(setting.sectionKey()), value),
                    Optional.empty(),
                    added.path(),
                    Significance.NOTABLE);
        }

        Optional<TemplateValue.Node> node = asNode(added.value());
        Optional<String> label = node.flatMap(TemplateValue.Node::label);
        boolean required = node.map(TemplateValue.Node::isRequired).orElse(false);

        StringBuilder sentence = new StringBuilder("A new ").append(path.elementType()).append(" was added");
        if (label.isPresent()) {
            sentence.append(": ").append(ValueFormatter.quote(label.get()));
        }
        sentence.append('.');

        node.flatMap(entry -> entry.sizeOf("items"))
                .ifPresent(count -> sentence.append(" It has ").append(count)
                        .append(count == 1 ? " item." : " items."));
        node.flatMap(entry -> entry.sizeOf("options"))
                .ifPresent(count -> sentence.append(" It offers ").append(count).append(" options."));

        if (required) {
            sentence.append(" Completing it is required.");
        }

        return new HumanChange(
                id,
                Kind.ADDED,
                path.elementType(),
                sentence.toString(),
                Optional.empty(),
                added.path(),
                required ? Significance.NOTABLE : Significance.NORMAL);
    }

    // --- Removed -----------------------------------------------------------

    private HumanChange describeRemoved(String id, TemplatePath path, DiffChange.Removed removed) {
        String description;
        Significance significance = Significance.NOTABLE;

        if (path instanceof TemplatePath.SectionElementField field) {
            // Only a field of an element went away, not the element itself.
            description = "%s was removed from %s %s.".formatted(
                    ValueFormatter.humanise(field.field()), field.elementType(), field.elementId());
            significance = Significance.NORMAL;
        } else if (path instanceof TemplatePath.SectionSetting setting) {
            description = "%s was removed from %s.".formatted(
                    ValueFormatter.humanise(setting.settingKey()),
                    ValueFormatter.sectionLabel(setting.sectionKey()));
        } else {
            Optional<String> label = asNode(removed.oldValue()).flatMap(TemplateValue.Node::label);
            description = label
                    .map(text -> "The %s %s was removed.".formatted(path.elementType(), ValueFormatter.quote(text)))
                    .orElseGet(() -> "A %s was removed.".formatted(path.elementType()));
        }

        return new HumanChange(
                id, Kind.REMOVED, path.elementType(), description, Optional.empty(),
                removed.path(), significance);
    }

    // --- Replaced ----------------------------------------------------------

    private HumanChange describeReplaced(String id, TemplatePath path, DiffChange.Replaced replaced) {
        Unit unit = unitFor(path);
        Optional<ValueChange> valueChange = scalarChange(replaced, unit);
        boolean numeric = isNumeric(replaced.oldValue()) && isNumeric(replaced.newValue());

        String description;
        Significance significance = numeric ? Significance.NOTABLE : Significance.NORMAL;

        if (path instanceof TemplatePath.SectionSetting setting) {
            description = "%s in %s changed from %s to %s.".formatted(
                    ValueFormatter.humanise(setting.settingKey()),
                    ValueFormatter.sectionLabel(setting.sectionKey()),
                    ValueFormatter.format(replaced.oldValue(), unit),
                    ValueFormatter.format(replaced.newValue(), unit));
            significance = Significance.NOTABLE;
        } else if (path instanceof TemplatePath.SectionElementField field && field.field().equals("label")) {
            // The before/after text is carried in valueChange, so the sentence stays short and the
            // client can show the full wording side by side.
            description = "The wording of %s %s was updated.".formatted(field.elementType(), field.elementId());
        } else if (path instanceof TemplatePath.SectionElementField field) {
            description = "%s for %s %s changed from %s to %s.".formatted(
                    ValueFormatter.humanise(field.field()),
                    field.elementType(),
                    field.elementId(),
                    ValueFormatter.format(replaced.oldValue(), unit),
                    ValueFormatter.format(replaced.newValue(), unit));
        } else if (path instanceof TemplatePath.TemplateMetadata metadata && metadata.field().equals("displayName")) {
            description = "The template name changed from %s to %s.".formatted(
                    ValueFormatter.quote(ValueFormatter.format(replaced.oldValue(), unit)),
                    ValueFormatter.quote(ValueFormatter.format(replaced.newValue(), unit)));
        } else {
            description = "%s changed from %s to %s.".formatted(
                    ValueFormatter.humanise(lastSegment(replaced.path())),
                    ValueFormatter.format(replaced.oldValue(), unit),
                    ValueFormatter.format(replaced.newValue(), unit));
        }

        return new HumanChange(
                id, Kind.MODIFIED, path.elementType(), description, valueChange,
                replaced.path(), significance);
    }

    // --- Helpers -----------------------------------------------------------

    private static Unit unitFor(TemplatePath path) {
        return switch (path) {
            case TemplatePath.SectionSetting setting -> ValueFormatter.unitOf(setting.settingKey());
            case TemplatePath.SectionElementField field -> ValueFormatter.unitOf(field.field());
            case TemplatePath.TemplateMetadata metadata -> ValueFormatter.unitOf(metadata.field());
            case TemplatePath.SectionElement ignored -> Unit.NONE;
            case TemplatePath.Unclassified ignored -> Unit.NONE;
        };
    }

    private static Optional<ValueChange> scalarChange(DiffChange.Replaced replaced, Unit unit) {
        if (replaced.oldValue() instanceof TemplateValue.Leaf && replaced.newValue() instanceof TemplateValue.Leaf) {
            return Optional.of(new ValueChange(
                    ValueFormatter.format(replaced.oldValue(), unit),
                    ValueFormatter.format(replaced.newValue(), unit)));
        }
        return Optional.empty();
    }

    private static boolean isNumeric(TemplateValue value) {
        return value instanceof TemplateValue.Leaf leaf && leaf.value() instanceof Number;
    }

    private static Optional<TemplateValue.Node> asNode(TemplateValue value) {
        return value instanceof TemplateValue.Node node ? Optional.of(node) : Optional.empty();
    }

    private static String lastSegment(String path) {
        int index = path.lastIndexOf('/');
        return index < 0 ? path : path.substring(index + 1);
    }

    /** Pairs a rendered change with the section it belongs to, while grouping. */
    private record Described(String groupKey, HumanChange change) {
    }
}
