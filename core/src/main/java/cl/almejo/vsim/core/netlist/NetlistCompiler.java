package cl.almejo.vsim.core.netlist;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Computes which pins and wires are electrically joined (union-find over grid points). */
public final class NetlistCompiler {

    private final ComponentTypeRegistry registry;

    public NetlistCompiler(ComponentTypeRegistry registry) {
        this.registry = registry;
    }

    public Netlist compile(CircuitDocument document) {
        Map<GridPoint, List<PinRef>> pinsAtPoint = new LinkedHashMap<>();
        Map<PinRef, Integer> pinWidths = new HashMap<>();
        for (Component component : document.components()) {
            ComponentType type = registry.require(component.typeId());
            for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                PinRef pin = new PinRef(component.id(), pinIndex);
                pinsAtPoint.computeIfAbsent(type.pinLocation(component, pinIndex), point -> new ArrayList<>()).add(pin);
                pinWidths.put(pin, type.pin(pinIndex).width());
            }
        }

        Set<GridPoint> pointsOfInterest = new HashSet<>(pinsAtPoint.keySet());
        for (Wire wire : document.wires()) {
            pointsOfInterest.add(wire.start());
            pointsOfInterest.add(wire.end());
        }

        UnionFind unionFind = new UnionFind();
        pointsOfInterest.forEach(unionFind::add);
        for (Wire wire : document.wires()) {
            for (GridPoint point : pointsOfInterest) {
                if (wire.contains(point)) {
                    unionFind.union(wire.start(), point);
                }
            }
        }

        Map<GridPoint, NetBuilder> builders = new LinkedHashMap<>();
        for (GridPoint point : pointsOfInterest) {
            builders.computeIfAbsent(unionFind.find(point), root -> new NetBuilder()).points.add(point);
        }
        pinsAtPoint.forEach((point, pins) -> builders.get(unionFind.find(point)).pins.addAll(pins));
        for (Wire wire : document.wires()) {
            builders.get(unionFind.find(wire.start())).wires.add(wire.id());
        }

        List<Net> nets = new ArrayList<>();
        for (NetBuilder builder : builders.values()) {
            nets.add(builder.build(pinWidths));
        }
        nets.sort(Comparator.comparing(net -> net.id().value()));
        return Netlist.of(nets);
    }

    private static final class NetBuilder {
        private final TreeSet<GridPoint> points = new TreeSet<>();
        private final Set<PinRef> pins = new HashSet<>();
        private final Set<ElementId> wires = new HashSet<>();

        Net build(Map<PinRef, Integer> pinWidths) {
            Set<Integer> widths = new HashSet<>();
            pins.forEach(pin -> widths.add(pinWidths.get(pin)));
            int width = widths.stream().mapToInt(Integer::intValue).max().orElse(1);
            GridPoint first = points.first();
            NetId id = new NetId("n" + first.x() + "_" + first.y());
            return new Net(id, pins, wires, points, width, widths.size() > 1);
        }
    }

    private static final class UnionFind {
        private final Map<GridPoint, GridPoint> parents = new HashMap<>();

        void add(GridPoint point) {
            parents.putIfAbsent(point, point);
        }

        GridPoint find(GridPoint point) {
            GridPoint root = point;
            while (!parents.get(root).equals(root)) {
                root = parents.get(root);
            }
            GridPoint current = point;
            while (!current.equals(root)) {
                GridPoint next = parents.get(current);
                parents.put(current, root);
                current = next;
            }
            return root;
        }

        void union(GridPoint first, GridPoint second) {
            GridPoint firstRoot = find(first);
            GridPoint secondRoot = find(second);
            if (!firstRoot.equals(secondRoot)) {
                parents.put(secondRoot, firstRoot);
            }
        }
    }
}
