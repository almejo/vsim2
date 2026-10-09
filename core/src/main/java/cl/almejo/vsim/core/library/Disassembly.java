package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.io.CircuitFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What "Desarmar" does to an instance (spec §5.6): its body comes back unrotated with the body's top-left at the
 * instance's top-left, and {@link PinBridges} bridges join each instance pin that had an outside connection to the
 * pasted interface pin that stands for it. The body may not touch anything outside except through those joins: when
 * a pasted pin or wire end lands on an outside wire or pin of another net (or an outside one lands on a pasted wire),
 * the instance must not be disassembled. Outside nets are never joined.
 */
public final class Disassembly {

    /**
     * Where to paste the body ({@code offset} added to its positions), the bridge wires to add, the pins left
     * unconnected (pinout order) and whether disassembling would join the body to something foreign.
     */
    public record Plan(GridPoint offset, List<Wire> bridges, List<String> unconnectedPins, boolean blocked) {
        public Plan {
            bridges = List.copyOf(bridges);
            unconnectedPins = List.copyOf(unconnectedPins);
        }
    }

    private Disassembly() {
    }

    /** The plan for {@code instance} of {@code definition}, with fresh wire ids from {@code document}. */
    public static Plan plan(CircuitDocument document, Component instance, ComponentDefinition definition,
                            ComponentTypeRegistry registry) {
        ComponentType type = registry.require(instance.typeId());
        Bounds bodyBounds = definition.body().bounds(registry);
        Bounds instanceBounds = type.bounds(instance);
        GridPoint offset = new GridPoint(instanceBounds.x() - bodyBounds.x(), instanceBounds.y() - bodyBounds.y());
        CircuitFragment body = definition.body().translate(offset.x(), offset.y());

        List<Wire> outsideWires = document.wires();
        Set<GridPoint> outsidePins = new HashSet<>();
        for (Component component : document.components()) {
            if (!component.id().equals(instance.id())) {
                outsidePins.addAll(PinBridges.pinPoints(component, registry));
            }
        }
        Set<GridPoint> pastedPoints = new HashSet<>();
        for (Component component : body.components()) {
            pastedPoints.addAll(PinBridges.pinPoints(component, registry));
        }
        body.wires().forEach(wire -> {
            pastedPoints.add(wire.start());
            pastedPoints.add(wire.end());
        });

        List<String> names = new ArrayList<>();
        List<GridPoint> sources = new ArrayList<>();
        List<GridPoint> targets = new ArrayList<>();
        List<DefinitionPin> pinout = definition.pinout();
        for (int index = 0; index < pinout.size(); index++) {
            DefinitionPin pin = pinout.get(index);
            names.add(pin.spec().name());
            sources.add(type.pinLocation(instance, index));
            Component pasted = body.components().stream()
                    .filter(component -> component.id().equals(pin.source()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("no interface pin " + pin.source()));
            targets.add(registry.require(pasted.typeId()).pinLocation(pasted, 0));
        }
        PinBridges.Bridging bridging = PinBridges.bridges(document, names, sources, targets, outsideWires,
                outsidePins, pastedPoints, body.wires());

        Nets intended = new Nets();
        Set<GridPoint> outsidePoints = new HashSet<>(outsidePins);
        outsideWires.forEach(wire -> {
            outsidePoints.add(wire.start());
            outsidePoints.add(wire.end());
        });
        intended.join(true, outsideWires, outsidePoints);
        intended.join(false, body.wires(), pastedPoints);
        for (int index = 0; index < names.size(); index++) {
            if (!bridging.unconnectedPins().contains(names.get(index))
                    && !bridging.blockedPins().contains(names.get(index))) {
                intended.union(new Node(true, sources.get(index)), new Node(false, targets.get(index)));
            }
        }
        boolean blocked = !bridging.blockedPins().isEmpty()
                || touchesForeign(intended, outsideWires, outsidePoints, body.wires(), pastedPoints);
        return new Plan(offset, bridging.wires(), bridging.unconnectedPins(), blocked);
    }

    /** True when a contact between the body and the outside joins nets that should stay apart. */
    private static boolean touchesForeign(Nets intended, List<Wire> outsideWires, Set<GridPoint> outsidePoints,
                                          List<Wire> pastedWires, Set<GridPoint> pastedPoints) {
        for (GridPoint point : pastedPoints) {
            if (outsidePoints.contains(point) && !intended.same(new Node(true, point), new Node(false, point))) {
                return true;
            }
            for (Wire wire : outsideWires) {
                if (wire.contains(point) && !intended.same(new Node(true, wire.start()), new Node(false, point))) {
                    return true;
                }
            }
        }
        for (GridPoint point : outsidePoints) {
            for (Wire wire : pastedWires) {
                if (wire.contains(point) && !intended.same(new Node(true, point), new Node(false, wire.start()))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A connection point on one side: the outside circuit or the pasted body. */
    private record Node(boolean outside, GridPoint point) {
    }

    /** The nets as they should be: each side's own, plus each kept pin joining its outside net to its body net. */
    private static final class Nets {
        private final Map<Node, Node> parents = new HashMap<>();

        void join(boolean outside, List<Wire> wires, Set<GridPoint> points) {
            for (Wire wire : wires) {
                for (GridPoint point : points) {
                    if (wire.contains(point)) {
                        union(new Node(outside, wire.start()), new Node(outside, point));
                    }
                }
            }
        }

        void union(Node first, Node second) {
            parents.put(find(first), find(second));
        }

        boolean same(Node first, Node second) {
            return find(first).equals(find(second));
        }

        private Node find(Node node) {
            Node current = node;
            while (parents.containsKey(current) && !parents.get(current).equals(current)) {
                current = parents.get(current);
            }
            return current;
        }
    }
}
