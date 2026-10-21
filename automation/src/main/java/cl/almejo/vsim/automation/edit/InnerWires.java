package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The wires that belong with some parts the AI names (IA.1 Task 7): a person selects a stage with a rectangle and
 * gets its wires too, but the AI names parts by id and a summary read does not list wires, so «these parts» would
 * leave a component or a truth table with its parts unconnected. The wires of every net that joins pins of two or
 * more of the targeted parts and no pin outside them come along; a net that also reaches a part outside is left as
 * the targets say (its wires would cross the boundary) and named, so the caller can warn.
 */
public final class InnerWires {

    /**
     * {@code elements}: the targets plus the inner wires; {@code addedWires}: the wires added; {@code mixedNetPins}:
     * pins of targeted parts on nets that also reach a part outside and none of whose wires was targeted, by part id
     * and pin index.
     */
    public record Scope(Set<ElementId> elements, Set<ElementId> addedWires, List<PinRef> mixedNetPins) {

        public Scope {
            elements = Collections.unmodifiableSet(new LinkedHashSet<>(elements));
            addedWires = Collections.unmodifiableSet(new LinkedHashSet<>(addedWires));
            mixedNetPins = List.copyOf(mixedNetPins);
        }
    }

    private InnerWires() {
    }

    /** {@code targets} with the wires of the nets that run only between targeted parts. */
    public static Scope of(CircuitDocument document, ComponentTypeRegistry registry, Set<ElementId> targets) {
        Set<ElementId> elements = new LinkedHashSet<>(targets);
        Set<ElementId> added = new LinkedHashSet<>();
        Set<PinRef> mixed = new TreeSet<>(Comparator.comparing((PinRef pin) -> pin.componentId().value())
                .thenComparingInt(PinRef::pinIndex));
        for (Net net : new NetlistCompiler(registry).compile(document).nets()) {
            Set<ElementId> inside = new LinkedHashSet<>();
            boolean reachesOutside = false;
            for (PinRef pin : net.pins()) {
                if (targets.contains(pin.componentId())) {
                    inside.add(pin.componentId());
                } else {
                    reachesOutside = true;
                }
            }
            if (inside.size() < 2 || net.wires().isEmpty()) {
                continue;
            }
            if (!reachesOutside) {
                for (ElementId wire : net.wires()) {
                    if (elements.add(wire)) {
                        added.add(wire);
                    }
                }
            } else if (net.wires().stream().noneMatch(targets::contains)) {
                net.pins().stream().filter(pin -> targets.contains(pin.componentId()))
                        .forEach(mixed::add);
            }
        }
        return new Scope(elements, added, new ArrayList<>(mixed));
    }
}
