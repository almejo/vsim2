package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Computes the same {@link Connectivity} as {@link Connectivity#of} for many candidate wire lists over one document,
 * without building a scratch document and compiling a full netlist each time: the pins are located once, and each
 * call indexes the points of interest (pins and wire ends) by row and column, so every wire finds the points it
 * contains with a range lookup instead of testing all of them. Same connection semantics as {@code NetlistCompiler}:
 * a pin or a wire end joins every wire that contains that point; crossing interiors do not join.
 */
final class ConnectivityChecker {

    /** The wires and pins of one net that has at least one wire. */
    record WireNet(Set<ElementId> wires, Set<PinRef> pins) {
    }

    private final CircuitDocument document;
    private final Map<GridPoint, List<PinRef>> pinsAtPoint;
    private final Set<PinRef> existingPins = new HashSet<>();

    ConnectivityChecker(CircuitDocument document, Map<GridPoint, List<PinRef>> pinsAtPoint) {
        this.document = document;
        this.pinsAtPoint = pinsAtPoint;
        pinsAtPoint.values().forEach(existingPins::addAll);
    }

    /** The signature of {@code wires} in the document, with probes on a wire id of {@code probeRetargets} moved. */
    Connectivity of(List<Wire> wires, Map<ElementId, ElementId> probeRetargets) {
        Grouping grouping = new Grouping(wires);
        Map<Integer, Set<PinRef>> pinsByRoot = grouping.pinsByRoot();
        Set<Set<PinRef>> pinNets = new HashSet<>();
        pinsByRoot.values().forEach(pins -> pinNets.add(Set.copyOf(pins)));
        Map<ElementId, Integer> wireRoots = new HashMap<>();
        for (Wire wire : wires) {
            wireRoots.put(wire.id(), grouping.rootOf(wire.start()));
        }
        Map<PinRef, Integer> pinRoots = new HashMap<>();
        pinsByRoot.forEach((root, pins) -> pins.forEach(pin -> pinRoots.put(pin, root)));

        Map<String, Set<PinRef>> probePins = new HashMap<>();
        Map<Integer, Set<String>> pinlessGroups = new HashMap<>();
        Set<String> unresolved = new HashSet<>();
        for (Probe probe : document.probes()) {
            Integer root = switch (probe.target()) {
                case ProbeTarget.PinTarget pin -> {
                    PinRef reference = new PinRef(pin.componentId(), pin.pinIndex());
                    yield document.component(pin.componentId()).isPresent() && existingPins.contains(reference)
                            ? pinRoots.get(reference) : null;
                }
                case ProbeTarget.WireTarget wire ->
                        wireRoots.get(probeRetargets.getOrDefault(wire.wireId(), wire.wireId()));
            };
            if (root == null) {
                unresolved.add(probe.id());
                continue;
            }
            Set<PinRef> pins = pinsByRoot.get(root);
            if (pins == null) {
                pinlessGroups.computeIfAbsent(root, key -> new HashSet<>()).add(probe.id());
            } else {
                probePins.put(probe.id(), Set.copyOf(pins));
            }
        }
        return new Connectivity(pinNets, probePins, Set.copyOf(pinlessGroups.values()), unresolved);
    }

    /** Every net of {@code wires} that has a wire, ordered by the position of its first wire in the list. */
    List<WireNet> nets(List<Wire> wires) {
        Grouping grouping = new Grouping(wires);
        Map<Integer, Set<PinRef>> pinsByRoot = grouping.pinsByRoot();
        Map<Integer, Set<ElementId>> wiresByRoot = new LinkedHashMap<>();
        for (Wire wire : wires) {
            wiresByRoot.computeIfAbsent(grouping.rootOf(wire.start()), root -> new HashSet<>()).add(wire.id());
        }
        List<WireNet> nets = new ArrayList<>();
        wiresByRoot.forEach((root, wireIds) -> nets.add(new WireNet(wireIds, pinsByRoot.getOrDefault(root, Set.of()))));
        return nets;
    }

    /** Union-find over the points of interest of one wire list. */
    private final class Grouping {

        private final Map<GridPoint, Integer> indexOf = new HashMap<>();
        private final List<GridPoint> points = new ArrayList<>();
        private final int[] parents;

        Grouping(List<Wire> wires) {
            pinsAtPoint.keySet().forEach(this::register);
            for (Wire wire : wires) {
                register(wire.start());
                register(wire.end());
            }
            parents = new int[points.size()];
            for (int index = 0; index < parents.length; index++) {
                parents[index] = index;
            }
            Map<Integer, TreeMap<Integer, Integer>> rows = new HashMap<>();
            Map<Integer, TreeMap<Integer, Integer>> columns = new HashMap<>();
            for (int index = 0; index < points.size(); index++) {
                GridPoint point = points.get(index);
                rows.computeIfAbsent(point.y(), row -> new TreeMap<>()).put(point.x(), index);
                columns.computeIfAbsent(point.x(), column -> new TreeMap<>()).put(point.y(), index);
            }
            for (Wire wire : wires) {
                int start = indexOf.get(wire.start());
                TreeMap<Integer, Integer> line = wire.isHorizontal()
                        ? rows.get(wire.start().y()) : columns.get(wire.start().x());
                int from = wire.isHorizontal() ? wire.start().x() : wire.start().y();
                int to = wire.isHorizontal() ? wire.end().x() : wire.end().y();
                for (int index : line.subMap(from, true, to, true).values()) {
                    union(start, index);
                }
            }
        }

        private void register(GridPoint point) {
            if (!indexOf.containsKey(point)) {
                indexOf.put(point, points.size());
                points.add(point);
            }
        }

        int rootOf(GridPoint point) {
            return find(indexOf.get(point));
        }

        /** The pins of every net that has pins, by the root of the net. */
        Map<Integer, Set<PinRef>> pinsByRoot() {
            Map<Integer, Set<PinRef>> pinsByRoot = new HashMap<>();
            pinsAtPoint.forEach((point, pins) -> {
                if (!pins.isEmpty()) {
                    pinsByRoot.computeIfAbsent(rootOf(point), root -> new HashSet<>()).addAll(pins);
                }
            });
            return pinsByRoot;
        }

        private int find(int index) {
            int root = index;
            while (parents[root] != root) {
                root = parents[root];
            }
            int current = index;
            while (parents[current] != root) {
                int next = parents[current];
                parents[current] = root;
                current = next;
            }
            return root;
        }

        private void union(int first, int second) {
            int firstRoot = find(first);
            int secondRoot = find(second);
            if (firstRoot != secondRoot) {
                parents[secondRoot] = firstRoot;
            }
        }
    }
}
