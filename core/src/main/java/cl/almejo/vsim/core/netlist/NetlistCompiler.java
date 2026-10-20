package cl.almejo.vsim.core.netlist;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.tunnel.Tunnels;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Computes which pins and wires are electrically joined (union-find over grid points).
 * Each net is named {@code "n:" + anchor}, where the anchor is the smallest of {@code "w:" + wireId} and
 * {@code "p:" + componentId + "#" + pinIndex} over its wires and pins, so the id survives edits that keep it. Anchors
 * order pins before wires, then by the numeric part of the element id (so {@code c10} comes after {@code c9}), then by
 * pin index. Tunnel pins have no width of their own ({@link cl.almejo.vsim.core.component.PinSpec#ADAPTIVE_WIDTH}):
 * a net's width comes from its other pins, and is one bit when it has none.
 */
public final class NetlistCompiler {

    private final ComponentTypeRegistry registry;

    public NetlistCompiler(ComponentTypeRegistry registry) {
        this.registry = registry;
    }

    /**
     * The nets of {@code document}: what its wires and pins join, plus every tunnel joined to the other tunnels of the
     * same name in this document (so names never cross a component boundary: each body is compiled on its own).
     */
    public Netlist compile(CircuitDocument document) {
        Connections connections = connect(document);
        connections.joinTunnelsByName(document);
        return connections.nets(document);
    }

    /** The nets as drawn: like {@link #compile} without joining tunnels by name (to tell a tunnel's wire side). */
    public Netlist compileAsDrawn(CircuitDocument document) {
        return connect(document).nets(document);
    }

    private Connections connect(CircuitDocument document) {
        Connections connections = new Connections();
        for (Component component : document.components()) {
            ComponentType type = registry.typeOf(component);
            for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                PinRef pin = new PinRef(component.id(), pinIndex);
                GridPoint location = type.pinLocation(component, pinIndex);
                connections.pinsAtPoint.computeIfAbsent(location, point -> new ArrayList<>()).add(pin);
                if (!type.pin(pinIndex).isAdaptiveWidth()) {
                    connections.pinWidths.put(pin, type.pin(pinIndex).width());
                }
                if (Tunnels.isTunnel(component.typeId())) {
                    connections.tunnelPins.put(component.id(), location);
                }
            }
        }

        Set<GridPoint> pointsOfInterest = connections.pointsOfInterest;
        pointsOfInterest.addAll(connections.pinsAtPoint.keySet());
        for (Wire wire : document.wires()) {
            pointsOfInterest.add(wire.start());
            pointsOfInterest.add(wire.end());
        }

        UnionFind unionFind = connections.unionFind;
        pointsOfInterest.forEach(unionFind::add);
        // points of interest by row and by column, so each wire finds the ones it contains with a range lookup
        Map<Integer, TreeMap<Integer, GridPoint>> rows = new HashMap<>();
        Map<Integer, TreeMap<Integer, GridPoint>> columns = new HashMap<>();
        for (GridPoint point : pointsOfInterest) {
            rows.computeIfAbsent(point.y(), row -> new TreeMap<>()).put(point.x(), point);
            columns.computeIfAbsent(point.x(), column -> new TreeMap<>()).put(point.y(), point);
        }
        for (Wire wire : document.wires()) {
            NavigableMap<Integer, GridPoint> contained = wire.isHorizontal()
                    ? rows.get(wire.start().y()).subMap(wire.start().x(), true, wire.end().x(), true)
                    : columns.get(wire.start().x()).subMap(wire.start().y(), true, wire.end().y(), true);
            for (GridPoint point : contained.values()) {
                unionFind.union(wire.start(), point);
            }
        }
        return connections;
    }

    /** The grid points joined so far, with the pins on them; {@link #nets} turns them into nets. */
    private static final class Connections {
        final Map<GridPoint, List<PinRef>> pinsAtPoint = new LinkedHashMap<>();
        /** Widths of the pins that have one (adaptive pins are left out, see {@link NetBuilder#build}). */
        final Map<PinRef, Integer> pinWidths = new HashMap<>();
        final Map<ElementId, GridPoint> tunnelPins = new LinkedHashMap<>();
        final Set<GridPoint> pointsOfInterest = new HashSet<>();
        final UnionFind unionFind = new UnionFind();

        void joinTunnelsByName(CircuitDocument document) {
            Map<String, GridPoint> firstOfName = new HashMap<>();
            tunnelPins.forEach((tunnelId, location) -> {
                String name = Tunnels.nameOf(document.component(tunnelId).orElseThrow());
                GridPoint first = firstOfName.putIfAbsent(name, location);
                if (first != null) {
                    unionFind.union(first, location);
                }
            });
        }

        Netlist nets(CircuitDocument document) {
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
    }

    private static final class NetBuilder {
        private final TreeSet<GridPoint> points = new TreeSet<>();
        private final Set<PinRef> pins = new HashSet<>();
        private final Set<ElementId> wires = new HashSet<>();

        Net build(Map<PinRef, Integer> pinWidths) {
            Set<Integer> widths = new HashSet<>();
            pins.stream().map(pinWidths::get).filter(Objects::nonNull).forEach(widths::add);
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
