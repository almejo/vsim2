package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.NetlistCompiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Simultaneous wire routing for a pin map. It preserves old junctions and routes all affected ends against the
 * same final pin positions. Unroutable wires stay untouched; callers validate disconnections before accepting.
 */
final class PinMapFollow implements ContactProbe.Layout {
    /** Bound the small detour search; failure is reported, never solved by joining a foreign net. */
    private static final int MAXIMUM_DETOUR = 12;
    private static final ElementId ROUTE_SEGMENT_ID = new ElementId("shape-route-check");

    private final CircuitDocument document;
    private final Supplier<ElementId> newWireIds;
    private final Map<PinRef, GridPoint> pinsBefore = new LinkedHashMap<>();
    private final Map<PinRef, GridPoint> pinsAfter = new LinkedHashMap<>();
    private final Map<PinRef, String> pinNames = new HashMap<>();
    private final Map<GridPoint, GridPoint> movedEnds = new LinkedHashMap<>();
    private final Map<ElementId, GridPoint> wireRoots = new HashMap<>();
    private final List<Wire> placed = new ArrayList<>();
    private final List<Wire> replaced = new ArrayList<>();
    private final List<Wire> added = new ArrayList<>();
    private final Set<ElementId> removed = new LinkedHashSet<>();
    private final PointNets original;
    private final WireGraph graph;

    private PinMapFollow(CircuitDocument document, ComponentTypeRegistry registry, PinMoves moves,
                         Supplier<ElementId> newWireIds) {
        this.document = Objects.requireNonNull(document, "document");
        this.newWireIds = Objects.requireNonNull(newWireIds, "newWireIds");
        Map<GridPoint, List<PinRef>> pinsAt = new HashMap<>();
        for (Component component : document.components()) {
            ComponentType type = registry.typeOf(component);
            for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                PinRef pin = new PinRef(component.id(), pinIndex);
                GridPoint point = type.pinLocation(component, pinIndex);
                pinsBefore.put(pin, point);
                pinsAfter.put(pin, moves.after().getOrDefault(pin, point));
                pinNames.put(pin, type.pin(pinIndex).name());
                pinsAt.computeIfAbsent(point, ignored -> new ArrayList<>()).add(pin);
            }
        }
        if (!pinsBefore.keySet().containsAll(moves.after().keySet())) {
            throw new IllegalArgumentException("pin map contains a pin not present in the circuit");
        }
        original = new PointNets(pinsBefore, document.wires());
        // NetlistCompiler adds virtual tunnel links. They are real electrical connections even when their two
        // drawn nets do not meet, and touching the same named tunnel must not be reported as a foreign join.
        for (Net net : new NetlistCompiler(registry).compile(document).nets()) {
            if (!net.pins().isEmpty()) {
                GridPoint root = pinsBefore.get(net.pins().iterator().next());
                net.pins().forEach(pin -> original.union(root, pinsBefore.get(pin)));
            }
        }
        graph = WireGraph.of(document.wires(), pinsAt);
        document.wires().forEach(wire -> wireRoots.put(wire.id(), original.find(wire.start())));
        pinsAt.forEach((point, pins) -> {
            GridPoint target = pinsAfter.get(pins.get(0));
            boolean sameDestination = pins.stream().allMatch(pin -> pinsAfter.get(pin).equals(target));
            boolean interiorJunction = graph.wiresThrough(point).stream()
                    .anyMatch(wire -> WireGraph.isInterior(wire, point));
            if (!point.equals(target) && sameDestination && !interiorJunction) {
                movedEnds.put(point, target);
            }
        });
    }

    static FollowPlan plan(CircuitDocument document, ComponentTypeRegistry registry, PinMoves moves,
                           Supplier<ElementId> newWireIds) {
        if (moves.after().isEmpty()) {
            return FollowPlan.EMPTY;
        }
        return new PinMapFollow(document, registry, moves, newWireIds).build();
    }

    private FollowPlan build() {
        List<Wire> affected = document.wires().stream().filter(this::hasMovedEnd).toList();
        document.wires().stream().filter(wire -> !hasMovedEnd(wire)).forEach(placed::add);
        for (Wire wire : affected) {
            reroute(wire);
        }
        for (Map.Entry<PinRef, GridPoint> entry : pinsBefore.entrySet()) {
            GridPoint source = entry.getValue();
            GridPoint target = pinsAfter.get(entry.getKey());
            if (!source.equals(target) && !movedEnds.containsKey(source) && connectedBefore(entry.getKey())) {
                bridge(entry.getKey(), source, target);
            }
        }
        Map<GridPoint, List<PinRef>> pinsAt = new HashMap<>();
        pinsAfter.forEach((pin, point) -> pinsAt.computeIfAbsent(point, ignored -> new ArrayList<>()).add(pin));
        Set<GridPoint> candidates = new LinkedHashSet<>(pinsAfter.values());
        placed.forEach(wire -> {
            candidates.add(wire.start());
            candidates.add(wire.end());
        });
        List<NewContact> contacts = ContactProbe.contactsAt(candidates, WireGraph.of(placed, pinsAt), this);
        return new FollowPlan(Set.of(), replaced, added, removed, contacts);
    }

    private boolean hasMovedEnd(Wire wire) {
        return movedEnds.containsKey(wire.start()) || movedEnds.containsKey(wire.end());
    }

    private void reroute(Wire wire) {
        List<GridPoint> anchors = new ArrayList<>();
        anchors.add(movedEnds.getOrDefault(wire.start(), wire.start()));
        graph.connectionPointsOn(wire).stream()
                .filter(point -> WireGraph.isInterior(wire, point)).sorted().forEach(anchors::add);
        anchors.add(movedEnds.getOrDefault(wire.end(), wire.end()));
        GridPoint net = wireRoots.get(wire.id());
        List<GridPoint> path = new ArrayList<>();
        for (int index = 0; index + 1 < anchors.size(); index++) {
            List<GridPoint> leg = route(anchors.get(index), anchors.get(index + 1), net);
            if (leg.isEmpty()) {
                placed.add(wire);
                return;
            }
            if (path.isEmpty()) {
                path.add(leg.get(0));
            }
            path.addAll(leg.subList(1, leg.size()));
        }
        List<Wire> segments = segments(path, wire.id());
        if (segments.isEmpty()) {
            removed.add(wire.id());
            return;
        }
        replaced.add(segments.get(0));
        addSegments(segments, net);
        added.addAll(segments.subList(1, segments.size()));
    }

    private boolean connectedBefore(PinRef pin) {
        GridPoint source = pinsBefore.get(pin);
        if (document.wires().stream().anyMatch(wire -> wire.contains(source))) {
            return true;
        }
        return pinsBefore.entrySet().stream().anyMatch(other -> !other.getKey().equals(pin)
                && original.find(other.getValue()).equals(original.find(source)));
    }

    private void bridge(PinRef pin, GridPoint source, GridPoint target) {
        PointNets current = new PointNets(pinsAfter, placed);
        GridPoint root = original.find(source);
        boolean alreadyConnected = pinsBefore.entrySet().stream().anyMatch(other -> !other.getKey().equals(pin)
                && original.find(other.getValue()).equals(root)
                && current.find(pinsAfter.get(other.getKey())).equals(current.find(target)));
        if (alreadyConnected) {
            return;
        }
        List<GridPoint> points = route(source, target, root);
        if (points.isEmpty()) {
            return;
        }
        List<Wire> segments = segments(points, newWireIds.get());
        added.addAll(segments);
        addSegments(segments, root);
    }

    private void addSegments(List<Wire> segments, GridPoint root) {
        for (Wire segment : segments) {
            placed.add(segment);
            wireRoots.put(segment.id(), root);
        }
    }

    private List<Wire> segments(List<GridPoint> points, ElementId firstId) {
        List<Wire> result = new ArrayList<>();
        for (int index = 0; index + 1 < points.size(); index++) {
            if (!points.get(index).equals(points.get(index + 1))) {
                ElementId id = result.isEmpty() ? firstId : newWireIds.get();
                result.add(new Wire(id, points.get(index), points.get(index + 1)));
            }
        }
        return result;
    }

    private List<GridPoint> route(GridPoint source, GridPoint target, GridPoint root) {
        List<List<GridPoint>> candidates = new ArrayList<>();
        if (source.equals(target)) {
            return List.of(source);
        }
        if (source.x() == target.x() || source.y() == target.y()) {
            candidates.add(List.of(source, target));
        }
        candidates.add(List.of(source, new GridPoint(source.x(), target.y()), target));
        candidates.add(List.of(source, new GridPoint(target.x(), source.y()), target));
        for (int distance = 1; distance <= MAXIMUM_DETOUR; distance++) {
            for (int sign : List.of(-1, 1)) {
                int across = source.x() + sign * distance;
                int down = source.y() + sign * distance;
                candidates.add(List.of(source, new GridPoint(across, source.y()),
                        new GridPoint(across, target.y()), target));
                candidates.add(List.of(source, new GridPoint(source.x(), down),
                        new GridPoint(target.x(), down), target));
            }
        }
        return candidates.stream().map(PinMapFollow::withoutRepeatedPoints)
                .filter(points -> isClear(points, root)).findFirst().orElse(List.of());
    }

    private static List<GridPoint> withoutRepeatedPoints(List<GridPoint> points) {
        List<GridPoint> result = new ArrayList<>();
        for (GridPoint point : points) {
            if (result.isEmpty() || !result.getLast().equals(point)) {
                result.add(point);
            }
        }
        return result;
    }

    private boolean isClear(List<GridPoint> points, GridPoint root) {
        for (int index = 0; index + 1 < points.size(); index++) {
            Wire segment = new Wire(ROUTE_SEGMENT_ID, points.get(index), points.get(index + 1));
            boolean touchesPin = pinsAfter.entrySet().stream().anyMatch(pin -> segment.contains(pin.getValue())
                    && !root.equals(original.find(pinsBefore.get(pin.getKey()))));
            if (touchesPin) {
                return false;
            }
            for (Wire existing : placed) {
                if (!root.equals(wireRoots.get(existing.id())) && joins(segment, existing)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean joins(Wire first, Wire second) {
        return first.contains(second.start()) || first.contains(second.end())
                || second.contains(first.start()) || second.contains(first.end());
    }

    @Override
    public Object netOfWire(ElementId wireId) {
        return wireRoots.get(wireId);
    }

    @Override
    public Object netOfPin(PinRef pin) {
        return original.find(pinsBefore.get(pin));
    }

    @Override
    public boolean isMoved(PinRef pin) {
        return !pinsBefore.get(pin).equals(pinsAfter.get(pin));
    }

    @Override
    public long orderOf(ElementId componentId) {
        return document.orderOf(componentId);
    }

    @Override
    public ElementId reportedComponentId(ElementId componentId) {
        return componentId;
    }

    @Override
    public String pinName(PinRef pin) {
        return pinNames.get(pin);
    }
}
