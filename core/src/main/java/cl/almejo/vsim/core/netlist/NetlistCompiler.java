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
import java.util.stream.Stream;

/**
 * Computes which pins and wires are electrically joined (union-find over grid points).
 * Each net is named {@code "n:" + anchor}, where the anchor is the smallest of {@code "w:" + wireId} and
 * {@code "p:" + componentId + "#" + pinIndex} over its wires and pins, so the id survives edits that keep it. Anchors
 * order pins before wires, then by the numeric part of the element id (so {@code c10} comes after {@code c9}), then by
 * pin index.
 */
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
            return new Net(new NetId("n:" + anchor()), pins, wires, points, width, widths.size() > 1);
        }

        private String anchor() {
            return Stream.concat(
                            wires.stream().map(wire -> new Anchor("w:", wire.value(), 0)),
                            pins.stream().map(pin -> new Anchor("p:", pin.componentId().value(), pin.pinIndex())))
                    .min(ANCHOR_ORDER)
                    .map(Anchor::text)
                    .orElseThrow(() -> new IllegalStateException("net without pins or wires at " + points));
        }
    }

    /** Orders anchors: pins before wires, then by the numeric part of the element id, then by pin index. */
    private static final Comparator<Anchor> ANCHOR_ORDER = Comparator
            .comparing(Anchor::kind)
            .thenComparing(Anchor::idNumber)
            .thenComparing(Anchor::elementId)
            .thenComparingInt(Anchor::pinIndex);

    private record Anchor(String kind, String elementId, int pinIndex) {

        /** The digits of an id such as {@code c10}; ids without digits sort before all numbered ones. */
        long idNumber() {
            String digits = elementId.replaceAll("\\D", "");
            return digits.isEmpty() ? -1 : Long.parseLong(digits);
        }

        String text() {
            return kind + elementId + ("p:".equals(kind) ? "#" + pinIndex : "");
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
