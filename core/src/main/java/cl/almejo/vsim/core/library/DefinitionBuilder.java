package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.io.CircuitFragment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a selection into a template (spec §5.6, overview "Decisions per track → M4-A"): inputs on the left, outputs on
 * the right, pins 2 units apart from y = 2, width 6, height 2 × (max(inputs, outputs) + 1).
 */
public final class DefinitionBuilder {

    public static final int BOX_WIDTH = 6;
    public static final int PIN_SPACING = 2;
    public static final int FIRST_PIN_Y = 2;

    private static final String INPUT_PREFIX = "in";
    private static final String OUTPUT_PREFIX = "out";

    private static final Comparator<Component> TOP_TO_BOTTOM = Comparator
            .comparingInt((Component component) -> component.position().y())
            .thenComparingInt(component -> component.position().x());

    private DefinitionBuilder() {
    }

    public static boolean hasInterfacePins(CircuitDocument document, Collection<ElementId> ids) {
        return !defaultPinOrder(document, ids).isEmpty();
    }

    /** The selected interface pins: inputs and outputs, each sorted by Y and then X. */
    public static PinOrder defaultPinOrder(CircuitDocument document, Collection<ElementId> ids) {
        List<Component> selected = selectedComponents(document, ids);
        return new PinOrder(sorted(selected, InterfacePins.INPUT_PIN), sorted(selected, InterfacePins.OUTPUT_PIN));
    }

    /**
     * Pin names, unique across both sides: the interface pin's stripped label, or {@code in1}, {@code in2}, … /
     * {@code out1}, … by position in {@code order} for blank labels. Labels are claimed first, so a blank pin never
     * takes a label's name; a repeated label becomes {@code a2}, {@code a3}, … (first free number) in {@code order}.
     * Pass the default order so names do not change when the user reorders.
     */
    public static Map<ElementId, String> pinNames(CircuitDocument document, PinOrder order) {
        List<ElementId> all = new ArrayList<>(order.inputs());
        all.addAll(order.outputs());
        Map<ElementId, String> labels = new HashMap<>();
        for (ElementId id : all) {
            labels.put(id, document.component(id).map(Component::label).orElse("").strip());
        }
        Set<String> reserved = new HashSet<>(labels.values());
        reserved.remove("");
        Set<String> taken = new HashSet<>();
        Map<ElementId, String> assigned = new HashMap<>();
        for (ElementId id : all) {
            String label = labels.get(id);
            if (!label.isEmpty()) {
                assigned.put(id, taken.add(label) ? label : firstFree(label, 2, reserved, taken));
            }
        }
        assignFallbacks(order.inputs(), INPUT_PREFIX, labels, reserved, taken, assigned);
        assignFallbacks(order.outputs(), OUTPUT_PREFIX, labels, reserved, taken, assigned);
        Map<ElementId, String> names = new LinkedHashMap<>();
        for (ElementId id : all) {
            names.put(id, assigned.get(id));
        }
        return names;
    }

    public static GridSize boxSize(int inputs, int outputs) {
        return new GridSize(BOX_WIDTH, PIN_SPACING * (Math.max(inputs, outputs) + 1));
    }

    /** Top-left of the selection's bounds: where the replacing instance goes. */
    public static GridPoint origin(CircuitDocument document, Collection<ElementId> ids, ComponentTypeRegistry registry) {
        Set<ElementId> wanted = Set.copyOf(ids);
        List<DocumentElement> selected = document.elements().stream()
                .filter(element -> wanted.contains(element.id()))
                .toList();
        return ElementBounds.union(selected, registry)
                .map(bounds -> new GridPoint(bounds.x(), bounds.y()))
                .orElse(GridPoint.ORIGIN);
    }

    /**
     * The definition made of the selected elements (body moved to the origin), with the definitions its nested
     * instances need as dependencies. {@code pinOrder} must list each selected interface pin once, on its side.
     */
    public static DefinitionBundle fromSelection(CircuitDocument document, Collection<ElementId> ids, String id,
                                                 String name, PinOrder pinOrder, ComponentTypeRegistry registry) {
        PinOrder found = defaultPinOrder(document, ids);
        if (found.isEmpty()) {
            throw new IllegalArgumentException("the selection has no interface pins");
        }
        if (!samePins(pinOrder.inputs(), found.inputs()) || !samePins(pinOrder.outputs(), found.outputs())) {
            throw new IllegalArgumentException("the pin order must list each selected interface pin once: " + pinOrder);
        }
        Map<ElementId, String> names = pinNames(document, found);
        List<DefinitionPin> pinout = new ArrayList<>();
        for (int index = 0; index < pinOrder.inputs().size(); index++) {
            ElementId source = pinOrder.inputs().get(index);
            pinout.add(new DefinitionPin(PinSpec.input(names.get(source), 0, FIRST_PIN_Y + PIN_SPACING * index,
                    widthOf(document, source)), source));
        }
        for (int index = 0; index < pinOrder.outputs().size(); index++) {
            ElementId source = pinOrder.outputs().get(index);
            pinout.add(new DefinitionPin(PinSpec.output(names.get(source), BOX_WIDTH,
                    FIRST_PIN_Y + PIN_SPACING * index, widthOf(document, source)), source));
        }
        CircuitFragment selected = CircuitFragment.of(document, ids, registry);
        CircuitFragment body = new CircuitFragment(selected.components(), selected.wires(), selected.labels());
        ComponentDefinition definition = new ComponentDefinition(id, name, "",
                boxSize(pinOrder.inputs().size(), pinOrder.outputs().size()), pinout, body);
        return new DefinitionBundle(definition, selected.definitions());
    }

    private static boolean samePins(List<ElementId> given, List<ElementId> expected) {
        return given.size() == expected.size() && new HashSet<>(given).equals(new HashSet<>(expected));
    }

    private static List<Component> selectedComponents(CircuitDocument document, Collection<ElementId> ids) {
        Set<ElementId> wanted = Set.copyOf(ids);
        return document.components().stream().filter(component -> wanted.contains(component.id())).toList();
    }

    private static List<ElementId> sorted(List<Component> components, String typeId) {
        return components.stream()
                .filter(component -> component.typeId().equals(typeId))
                .sorted(TOP_TO_BOTTOM)
                .map(Component::id)
                .toList();
    }

    /** Blank-labeled pins get {@code prefix} plus their position (1-based), or the next free number after it. */
    private static void assignFallbacks(List<ElementId> ids, String prefix, Map<ElementId, String> labels,
                                        Set<String> reserved, Set<String> taken, Map<ElementId, String> assigned) {
        for (int index = 0; index < ids.size(); index++) {
            ElementId id = ids.get(index);
            if (labels.get(id).isEmpty()) {
                assigned.put(id, firstFree(prefix, index + 1, reserved, taken));
            }
        }
    }

    /** The first {@code base + number} (from {@code firstNumber} up) neither reserved nor taken; marks it taken. */
    private static String firstFree(String base, int firstNumber, Set<String> reserved, Set<String> taken) {
        for (int number = firstNumber; ; number++) {
            String candidate = base + number;
            if (!reserved.contains(candidate) && !taken.contains(candidate)) {
                taken.add(candidate);
                return candidate;
            }
        }
    }

    /** The width of the selected interface pin {@code id}: the template pin it becomes is as wide. */
    private static int widthOf(CircuitDocument document, ElementId id) {
        return InterfacePins.widthOf(document.component(id).orElseThrow());
    }
}
