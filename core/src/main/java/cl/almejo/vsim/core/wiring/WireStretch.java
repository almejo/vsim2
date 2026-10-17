package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Groups of wires the select tool picks with one gesture (M6.8): the <em>stretch</em> of a wire (a double click) and
 * every wire geometrically connected to it (a triple click). Both follow {@link WireGraph}'s connection semantics and
 * look only at wires and pins, never through components. Wires are whole: a junction on a wire's interior never splits
 * it.
 */
public final class WireStretch {

    private WireStretch() {
    }

    /**
     * The wire {@code wireId} plus every wire chained to it through plain corners or straight joins, in route order
     * from one end to the other. A point continues the chain only when exactly two wire ends meet there, no other wire
     * passes through it and no pin is on it; the chain stops at a pin, a junction, a free end, or when it comes back
     * to the start (a loop). Empty when {@code wireId} is not a wire of {@code document}.
     */
    public static Set<ElementId> of(CircuitDocument document, ComponentTypeRegistry registry, ElementId wireId) {
        Optional<Wire> clicked = document.wire(wireId);
        if (clicked.isEmpty()) {
            return Set.of();
        }
        WireGraph graph = WireGraph.of(document.wires(), document, registry);
        Set<ElementId> visited = new HashSet<>(Set.of(wireId));
        List<ElementId> before = follow(graph, clicked.get(), clicked.get().start(), visited);
        List<ElementId> after = follow(graph, clicked.get(), clicked.get().end(), visited);
        Collections.reverse(before);
        Set<ElementId> stretch = new LinkedHashSet<>(before);
        stretch.add(wireId);
        stretch.addAll(after);
        return Collections.unmodifiableSet(stretch);
    }

    /**
     * Every wire connected to {@code wireId} through wire ends, T junctions on interiors and pins shared by wires
     * (crossings do not connect), in document order. Empty when {@code wireId} is not a wire of {@code document}.
     */
    public static Set<ElementId> connected(CircuitDocument document, ComponentTypeRegistry registry,
                                           ElementId wireId) {
        Optional<Wire> clicked = document.wire(wireId);
        if (clicked.isEmpty()) {
            return Set.of();
        }
        WireGraph graph = WireGraph.of(document.wires(), document, registry);
        Set<ElementId> reached = new HashSet<>(Set.of(wireId));
        Deque<Wire> pending = new ArrayDeque<>(List.of(clicked.get()));
        while (!pending.isEmpty()) {
            Wire current = pending.removeFirst();
            for (GridPoint point : graph.connectionPointsOn(current)) {
                for (Wire neighbour : graph.wiresThrough(point)) {
                    if (reached.add(neighbour.id())) {
                        pending.addLast(neighbour);
                    }
                }
            }
        }
        Set<ElementId> inDocumentOrder = new LinkedHashSet<>();
        for (Wire wire : document.wires()) {
            if (reached.contains(wire.id())) {
                inDocumentOrder.add(wire.id());
            }
        }
        return Collections.unmodifiableSet(inDocumentOrder);
    }

    /** The wires chained to {@code from} beyond its end {@code point}, nearest first; marks them in {@code visited}. */
    private static List<ElementId> follow(WireGraph graph, Wire from, GridPoint point, Set<ElementId> visited) {
        List<ElementId> chain = new ArrayList<>();
        Wire current = from;
        GridPoint joint = point;
        while (true) {
            Optional<Wire> next = continuation(graph, current, joint);
            if (next.isEmpty() || !visited.add(next.get().id())) {
                return chain;
            }
            chain.add(next.get().id());
            current = next.get();
            joint = current.start().equals(joint) ? current.end() : current.start();
        }
    }

    /**
     * The other wire at {@code joint} when it is a plain corner or straight join of {@code wire}: no pin there, and
     * exactly two wires through it, both ending there.
     */
    private static Optional<Wire> continuation(WireGraph graph, Wire wire, GridPoint joint) {
        if (!graph.pinsAt(joint).isEmpty()) {
            return Optional.empty();
        }
        List<Wire> through = graph.wiresThrough(joint);
        if (through.size() != 2 || through.stream().anyMatch(other -> WireGraph.isInterior(other, joint))) {
            return Optional.empty();
        }
        return through.stream().filter(other -> !other.id().equals(wire.id())).findFirst();
    }
}
