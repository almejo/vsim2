package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Flattens U detours (spec 2.2.4): three wires s1–s2–s3 joined only to each other at the two corners, s1 and s3
 * perpendicular to s2 and on the same side of it. The middle wire moves toward the opening by the length of the
 * shorter leg: that leg disappears and the other one shortens (with equal legs both go). Allowed only when the moved
 * middle touches nothing new: no pin, no other wire (end, interior or crossing) and no component body (its bounds,
 * edges included). The only points it may share with something else are the outer ends of the legs it lands on,
 * which were already joined to the U. The merge rule usually joins the result with its neighbours afterwards.
 */
final class DetourRule implements TidyRule {

    @Override
    public boolean apply(TidyState state) {
        boolean changed = false;
        List<Bounds> bodies = bodies(state.document(), state.registry());
        while (flattenPass(state, bodies)) {
            changed = true;
        }
        return changed;
    }

    /** One scan over the wires as they were when it started; a flattened U does not restart the scan. */
    private static boolean flattenPass(TidyState state, List<Bounds> bodies) {
        boolean changed = false;
        for (Wire snapshot : List.copyOf(state.wires())) {
            Optional<Wire> middle = state.wire(snapshot.id());
            if (middle.isEmpty()) {
                continue;
            }
            Optional<Detour> detour = detourAround(state, middle.get());
            if (detour.isPresent() && touchesNothingNew(state, detour.get(), bodies)
                    && state.tryChange(detour.get().removed(), detour.get().added(), detour.get().dropped())) {
                state.countDetours(1);
                changed = true;
            }
        }
        return changed;
    }

    private static List<Bounds> bodies(CircuitDocument document, ComponentTypeRegistry registry) {
        List<Bounds> bodies = new ArrayList<>();
        for (Component component : document.components()) {
            bodies.add(registry.typeOf(component).bounds(component));
        }
        return bodies;
    }

    /** One U found around {@code middle}, with the wires that replace it. */
    private record Detour(Wire firstLeg, Wire middle, Wire secondLeg, Wire movedMiddle, Optional<Wire> firstRest,
                          Optional<Wire> secondRest) {

        List<Wire> removed() {
            return List.of(firstLeg, middle, secondLeg);
        }

        List<Wire> added() {
            List<Wire> added = new ArrayList<>();
            firstRest.ifPresent(added::add);
            added.add(movedMiddle);
            secondRest.ifPresent(added::add);
            return added;
        }

        /** A leg that disappears hands its probes to the moved middle. */
        Map<ElementId, ElementId> dropped() {
            Map<ElementId, ElementId> dropped = new HashMap<>();
            if (firstRest.isEmpty()) {
                dropped.put(firstLeg.id(), middle.id());
            }
            if (secondRest.isEmpty()) {
                dropped.put(secondLeg.id(), middle.id());
            }
            return dropped;
        }
    }

    private static Optional<Detour> detourAround(TidyState state, Wire middle) {
        WireGraph graph = state.graph();
        Optional<Wire> firstLeg = legAt(graph, middle, middle.start());
        Optional<Wire> secondLeg = legAt(graph, middle, middle.end());
        if (firstLeg.isEmpty() || secondLeg.isEmpty() || firstLeg.get().id().equals(secondLeg.get().id())) {
            return Optional.empty();
        }
        GridPoint firstCorner = middle.start();
        GridPoint secondCorner = middle.end();
        GridPoint firstOuter = otherEnd(firstLeg.get(), firstCorner);
        GridPoint secondOuter = otherEnd(secondLeg.get(), secondCorner);
        GridPoint firstDirection = direction(firstCorner, firstOuter);
        if (!firstDirection.equals(direction(secondCorner, secondOuter))) {
            return Optional.empty();
        }
        int shift = Math.min(distance(firstCorner, firstOuter), distance(secondCorner, secondOuter));
        // the stretches that disappear may join nothing but the U itself
        if (!onlyJoinedAtCorner(graph, middle, Set.of(firstCorner, secondCorner))
                || !onlyJoinedAtCorner(graph, firstLeg.get(), firstCorner, firstDirection, shift)
                || !onlyJoinedAtCorner(graph, secondLeg.get(), secondCorner, firstDirection, shift)) {
            return Optional.empty();
        }
        GridPoint movedFirst = firstCorner.translate(firstDirection.x() * shift, firstDirection.y() * shift);
        GridPoint movedSecond = secondCorner.translate(firstDirection.x() * shift, firstDirection.y() * shift);
        return Optional.of(new Detour(firstLeg.get(), middle, secondLeg.get(),
                new Wire(middle.id(), movedFirst, movedSecond),
                rest(firstLeg.get(), firstOuter, movedFirst), rest(secondLeg.get(), secondOuter, movedSecond)));
    }

    /** The only other wire at {@code corner}: perpendicular to {@code middle}, ending there, and no pin there. */
    private static Optional<Wire> legAt(WireGraph graph, Wire middle, GridPoint corner) {
        if (!graph.pinsAt(corner).isEmpty()) {
            return Optional.empty();
        }
        List<Wire> through = graph.wiresThrough(corner);
        if (through.size() != 2) {
            return Optional.empty();
        }
        Wire leg = through.get(0).id().equals(middle.id()) ? through.get(1) : through.get(0);
        if (leg.id().equals(middle.id()) || leg.isHorizontal() == middle.isHorizontal()
                || WireGraph.isInterior(leg, corner) || WireGraph.isInterior(middle, corner)) {
            return Optional.empty();
        }
        return Optional.of(leg);
    }

    private static boolean onlyJoinedAtCorner(WireGraph graph, Wire wire, Set<GridPoint> corners) {
        return corners.containsAll(graph.connectionPointsOn(wire));
    }

    /** No joint on {@code leg} from its corner up to (not including) the point {@code shift} units away. */
    private static boolean onlyJoinedAtCorner(WireGraph graph, Wire leg, GridPoint corner, GridPoint direction,
                                              int shift) {
        for (GridPoint joint : graph.connectionPointsOn(leg)) {
            int along = (joint.x() - corner.x()) * direction.x() + (joint.y() - corner.y()) * direction.y();
            if (!joint.equals(corner) && along < shift) {
                return false;
            }
        }
        return true;
    }

    /** What is left of a leg once its corner moves to {@code movedCorner}; nothing when that is its outer end. */
    private static Optional<Wire> rest(Wire leg, GridPoint outer, GridPoint movedCorner) {
        return outer.equals(movedCorner) ? Optional.empty() : Optional.of(leg.withEndpoints(outer, movedCorner));
    }

    private static boolean touchesNothingNew(TidyState state, Detour detour, List<Bounds> bodies) {
        Wire moved = detour.movedMiddle();
        Set<ElementId> own = Set.of(detour.firstLeg().id(), detour.middle().id(), detour.secondLeg().id());
        Set<GridPoint> alreadyJoined = Set.of(otherEnd(detour.firstLeg(), detour.middle().start()),
                otherEnd(detour.secondLeg(), detour.middle().end()));
        WireGraph graph = state.graph();
        for (GridPoint point : pointsOf(moved)) {
            if (alreadyJoined.contains(point)) {
                continue;
            }
            if (!graph.pinsAt(point).isEmpty()) {
                return false;
            }
            for (Wire other : graph.wiresThrough(point)) {
                if (!own.contains(other.id())) {
                    return false;
                }
            }
            for (Bounds body : bodies) {
                if (body.contains(point)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<GridPoint> pointsOf(Wire wire) {
        List<GridPoint> points = new ArrayList<>();
        GridPoint step = direction(wire.start(), wire.end());
        for (GridPoint point = wire.start(); !point.equals(wire.end()); point = point.plus(step)) {
            points.add(point);
        }
        points.add(wire.end());
        return points;
    }

    private static GridPoint otherEnd(Wire wire, GridPoint end) {
        return wire.start().equals(end) ? wire.end() : wire.start();
    }

    private static GridPoint direction(GridPoint from, GridPoint to) {
        return new GridPoint(Integer.signum(to.x() - from.x()), Integer.signum(to.y() - from.y()));
    }

    private static int distance(GridPoint from, GridPoint to) {
        return Math.abs(to.x() - from.x()) + Math.abs(to.y() - from.y());
    }
}
