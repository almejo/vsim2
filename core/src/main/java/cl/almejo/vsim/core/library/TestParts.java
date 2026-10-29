package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The parts of a selection that are there to test it (switches, constants, clocks, LEDs, 7-segment displays, time
 * diagrams) and that «Crear componente» can turn into pins (followups 2026-10-12 and 2026-10-18). See
 * {@link ConvertedSelection} for what each becomes.
 */
public final class TestParts {

    /**
     * What a test part is; the first three become input pins when checked, the others output pins. A push button is a
     * {@link #SWITCH} here: it becomes an input pin, or a constant of its value when left unchecked.
     */
    public enum Kind {
        SWITCH, CONSTANT, CLOCK, LED, SEVEN_SEGMENTS_DISPLAY, TIME_DIAGRAM;

        public boolean becomesInput() {
            return this == SWITCH || this == CONSTANT || this == CLOCK;
        }
    }

    /**
     * One test part: its stripped label and, for a constant, its value as its box shows it ({@code value} is empty for
     * the others).
     */
    public record TestPart(ElementId id, String typeId, Kind kind, String label, String value) {
        public TestPart {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(typeId, "typeId");
            Objects.requireNonNull(kind, "kind");
            label = label == null ? "" : label.strip();
            value = value == null ? "" : value;
        }
    }

    /** Top to bottom, then left to right, like {@link PinOrder}; the id breaks ties so the order is stable. */
    static final Comparator<Component> BY_POSITION = Comparator
            .comparingInt((Component component) -> component.position().y())
            .thenComparingInt(component -> component.position().x())
            .thenComparing(component -> component.id().value());

    private static final Map<String, Kind> KINDS = Map.of(
            StandardComponentTypes.SWITCH, Kind.SWITCH,
            StandardComponentTypes.PUSH_BUTTON, Kind.SWITCH,
            StandardComponentTypes.CONSTANT, Kind.CONSTANT,
            StandardComponentTypes.CLOCK, Kind.CLOCK,
            StandardComponentTypes.LED, Kind.LED,
            StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, Kind.SEVEN_SEGMENTS_DISPLAY,
            StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY_DOUBLE, Kind.SEVEN_SEGMENTS_DISPLAY,
            StandardComponentTypes.TIME_DIAGRAM, Kind.TIME_DIAGRAM);

    private TestParts() {
    }

    public static Optional<Kind> kindOf(String typeId) {
        return Optional.ofNullable(KINDS.get(typeId));
    }

    /** The test parts among {@code ids} in position order; empty when there are none. */
    public static List<TestPart> of(CircuitDocument document, Collection<ElementId> ids,
                                    ComponentTypeRegistry registry) {
        return components(document, ids).stream()
                .map(component -> new TestPart(component.id(), component.typeId(),
                        KINDS.get(component.typeId()), component.label(), valueOf(component, registry)))
                .toList();
    }

    /** The test-part components among {@code ids}, in position order. */
    static List<Component> components(CircuitDocument document, Collection<ElementId> ids) {
        Set<ElementId> wanted = Set.copyOf(ids);
        return document.components().stream()
                .filter(component -> wanted.contains(component.id()))
                .filter(component -> KINDS.containsKey(component.typeId()))
                .sorted(BY_POSITION)
                .toList();
    }

    private static String valueOf(Component component, ComponentTypeRegistry registry) {
        if (!StandardComponentTypes.CONSTANT.equals(component.typeId())) {
            return "";
        }
        Map<String, Object> resolved = registry.typeOf(component).resolveParameters(component.parameters());
        int width = (Integer) resolved.get(StandardComponentTypes.WIDTH);
        return ConstantValue.display(ConstantValue.bitsOf((String) resolved.get("value"), width).orElseThrow(), width,
                (String) resolved.get(ConstantValue.FORMAT));
    }
}
