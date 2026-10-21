package cl.almejo.vsim.automation.check;

import cl.almejo.vsim.automation.check.TextMeasure.TextBox;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The measurable layout checks of a stage (spec §6): no part overlapping another part, no text (a part's label or a
 * note) covering a part's body or another text, no wire running through a part's body. It measures; it never moves
 * anything. Texts are placed by a {@link TextMeasure} (the editor's font in the application).
 */
public final class LayoutCheck {

    /** The kinds of layout fault. */
    public enum FaultKind {
        /** Two parts' bodies share some area. */
        PART_OVERLAP,
        /** A label or note covers the body of a part (not the part the label belongs to). */
        TEXT_OVER_PART,
        /** Two texts (labels or notes) cover each other. */
        TEXT_OVERLAP,
        /** A wire runs through the inside of a part's body (ending on its edge, at a pin, is fine). */
        WIRE_THROUGH_BODY
    }

    /** One fault: its kind, the two elements involved (in a stable order) and a short English description. */
    public record Fault(FaultKind kind, List<String> elementIds, String message) {
        public Fault {
            Objects.requireNonNull(kind, "kind");
            elementIds = List.copyOf(elementIds);
            Objects.requireNonNull(message, "message");
        }
    }

    private record Text(String ownerId, String name, TextBox box) {
    }

    private LayoutCheck() {
    }

    /**
     * Every fault of {@code document} that involves at least one element of {@code scope} (an empty scope: the whole
     * circuit's top level).
     */
    public static List<Fault> faults(CircuitDocument document, ComponentTypeRegistry registry, TextMeasure measure,
                                     Set<String> scope) {
        Objects.requireNonNull(scope, "scope");
        List<Fault> faults = new ArrayList<>();
        List<Component> components = document.components();
        List<Bounds> bodies = components.stream().map(component -> registry.typeOf(component).bounds(component))
                .toList();
        for (int first = 0; first < components.size(); first++) {
            for (int second = first + 1; second < components.size(); second++) {
                if (shareArea(bodies.get(first), bodies.get(second))) {
                    add(faults, scope, FaultKind.PART_OVERLAP, components.get(first), components.get(second),
                            name(components.get(first)) + " overlaps " + name(components.get(second)));
                }
            }
        }
        List<Text> texts = texts(document, registry, measure);
        for (int first = 0; first < texts.size(); first++) {
            Text text = texts.get(first);
            for (int second = first + 1; second < texts.size(); second++) {
                Text other = texts.get(second);
                if (text.box().overlaps(other.box())) {
                    add(faults, scope, new Fault(FaultKind.TEXT_OVERLAP, List.of(text.ownerId(), other.ownerId()),
                            "text «" + text.name() + "» covers text «" + other.name() + "»"));
                }
            }
            for (int index = 0; index < components.size(); index++) {
                Component component = components.get(index);
                if (!component.id().value().equals(text.ownerId())
                        && text.box().overlaps(TextBox.of(bodies.get(index)))) {
                    add(faults, scope, new Fault(FaultKind.TEXT_OVER_PART,
                            List.of(text.ownerId(), component.id().value()),
                            "text «" + text.name() + "» covers " + name(component)));
                }
            }
        }
        for (int index = 0; index < components.size(); index++) {
            for (Wire wire : document.wires()) {
                if (runsThrough(wire, bodies.get(index))) {
                    add(faults, scope, new Fault(FaultKind.WIRE_THROUGH_BODY,
                            List.of(wire.id().value(), components.get(index).id().value()),
                            "wire " + wire.id().value() + " runs through the body of " + name(components.get(index))));
                }
            }
        }
        return List.copyOf(faults);
    }

    private static List<Text> texts(CircuitDocument document, ComponentTypeRegistry registry, TextMeasure measure) {
        List<Text> texts = new ArrayList<>();
        for (Component component : document.components()) {
            measure.componentLabel(registry.typeOf(component), component)
                    .ifPresent(box -> texts.add(new Text(component.id().value(), component.label(), box)));
        }
        for (TextLabel label : document.labels()) {
            texts.add(new Text(label.id().value(), label.text(), measure.freeText(label)));
        }
        return texts;
    }

    private static boolean shareArea(Bounds first, Bounds second) {
        return first.x() < second.maximumX() && second.x() < first.maximumX()
                && first.y() < second.maximumY() && second.y() < first.maximumY();
    }

    /** A wire on the body's inside: across it strictly between its edges (start and end are in order). */
    private static boolean runsThrough(Wire wire, Bounds body) {
        if (wire.isHorizontal()) {
            int y = wire.start().y();
            return y > body.y() && y < body.maximumY()
                    && wire.start().x() < body.maximumX() && wire.end().x() > body.x();
        }
        int x = wire.start().x();
        return x > body.x() && x < body.maximumX()
                && wire.start().y() < body.maximumY() && wire.end().y() > body.y();
    }

    private static void add(List<Fault> faults, Set<String> scope, FaultKind kind, Component first, Component second,
                            String message) {
        add(faults, scope, new Fault(kind, List.of(first.id().value(), second.id().value()), message));
    }

    private static void add(List<Fault> faults, Set<String> scope, Fault fault) {
        if (scope.isEmpty() || fault.elementIds().stream().anyMatch(scope::contains)) {
            faults.add(fault);
        }
    }

    private static String name(Component component) {
        return component.id().value() + " (" + component.typeId()
                + (component.label().isBlank() ? "" : " «" + component.label() + "»") + ")";
    }
}
