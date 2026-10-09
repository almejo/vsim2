package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps the outside connections of a selection that an instance replaces. Wires and pins outside the selection
 * touched its interface pins; the instance's pins sit elsewhere (on its box), so each interface pin that had an
 * outside connection gets a bridge: a straight or one-bend wire from the interface pin's point to the instance pin
 * that stands for it. A bridge never touches anything else (another pin, a wire end, a wire under its bend).
 * <p>
 * A pin is reported as unconnected, and gets no bridge, when no such route exists, when its instance pin lands on
 * something outside that was not its own connection, or when its outside connection sits where another instance pin
 * lands. Bridges therefore never join two outside nets; an instance pin dropped onto a foreign net is reported.
 */
public final class PinBridges {

    /** The bridge wires to add, and the definition pins whose outside connection could not be kept safely. */
    public record Bridging(List<Wire> wires, List<String> unconnectedPins) {
        public Bridging {
            wires = List.copyOf(wires);
            unconnectedPins = List.copyOf(unconnectedPins);
        }
    }

    private PinBridges() {
    }

    /**
     * The bridges for replacing {@code replaced} by an instance of {@code definition} (unrotated) at
     * {@code instancePosition}, in pinout order, with fresh wire ids from {@code document}, and the names of the
     * pins left unconnected (pinout order).
     */
    public static Bridging wires(CircuitDocument document, Collection<ElementId> replaced,
                                 ComponentDefinition definition, GridPoint instancePosition,
                                 ComponentTypeRegistry registry) {
        Set<ElementId> removed = Set.copyOf(replaced);
        List<Wire> outsideWires = document.wires().stream().filter(wire -> !removed.contains(wire.id())).toList();
        Set<GridPoint> outsidePins = new HashSet<>();
        for (Component component : document.components()) {
            if (!removed.contains(component.id())) {
                outsidePins.addAll(pinPoints(component, registry));
            }
        }
        List<String> names = new ArrayList<>();
        List<GridPoint> sources = new ArrayList<>();
        List<GridPoint> targets = new ArrayList<>();
        for (DefinitionPin pin : definition.pinout()) {
            names.add(pin.spec().name());
            sources.add(document.component(pin.source())
                    .map(source -> registry.require(source.typeId()).pinLocation(source, 0))
                    .orElseThrow(() -> new IllegalArgumentException("no interface pin " + pin.source())));
            targets.add(instancePosition.plus(pin.spec().position()));
        }

        Set<GridPoint> obstacles = new HashSet<>(outsidePins);
        outsideWires.forEach(wire -> {
            obstacles.add(wire.start());
            obstacles.add(wire.end());
        });
        obstacles.addAll(targets);
        boolean[] connected = new boolean[sources.size()];
        boolean[] conflicting = new boolean[sources.size()];
        for (int index = 0; index < sources.size(); index++) {
            GridPoint source = sources.get(index);
            GridPoint target = targets.get(index);
            if (source.equals(target) || sameWire(source, target, outsideWires)) {
                continue; // the outside already reaches the instance pin on its own
            }
            connected[index] = touchesOutside(source, outsidePins, outsideWires);
            conflicting[index] = touchesOutside(target, outsidePins, outsideWires)
                    || connected[index] && isAnotherTarget(source, index, targets);
            if (connected[index]) {
                obstacles.add(source);
            }
        }

        List<Wire> placed = new ArrayList<>(outsideWires);
        List<Wire> bridges = new ArrayList<>();
        List<String> unconnected = new ArrayList<>();
        for (int index = 0; index < sources.size(); index++) {
            if (conflicting[index]) {
                unconnected.add(names.get(index));
                continue;
            }
            if (!connected[index]) {
                continue;
            }
            Optional<List<GridPoint>> route = route(sources.get(index), targets.get(index), obstacles, placed);
            if (route.isEmpty()) {
                unconnected.add(names.get(index));
                continue;
            }
            List<GridPoint> points = route.get();
            for (int point = 0; point + 1 < points.size(); point++) {
                Wire bridge = new Wire(document.newWireId(), points.get(point), points.get(point + 1));
                bridges.add(bridge);
                placed.add(bridge);
            }
            obstacles.addAll(points);
        }
        return new Bridging(bridges, unconnected);
    }

    /** True when {@code point} is where an instance pin other than {@code index} sits. */
    private static boolean isAnotherTarget(GridPoint point, int index, List<GridPoint> targets) {
        for (int other = 0; other < targets.size(); other++) {
            if (other != index && targets.get(other).equals(point)) {
                return true;
            }
        }
        return false;
    }

    private static List<GridPoint> pinPoints(Component component, ComponentTypeRegistry registry) {
        ComponentType type = registry.require(component.typeId());
        List<GridPoint> points = new ArrayList<>();
        for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
            points.add(type.pinLocation(component, pinIndex));
        }
        return points;
    }

    private static boolean touchesOutside(GridPoint point, Set<GridPoint> outsidePins, List<Wire> outsideWires) {
        return outsidePins.contains(point) || outsideWires.stream().anyMatch(wire -> wire.contains(point));
    }

    /** True when one outside wire already runs over both points: they stay joined without a bridge. */
    private static boolean sameWire(GridPoint first, GridPoint second, List<Wire> outsideWires) {
        return outsideWires.stream().anyMatch(wire -> wire.contains(first) && wire.contains(second));
    }

    /** The points of the first clear route (straight, else vertical-then-horizontal, else horizontal-then-vertical). */
    private static Optional<List<GridPoint>> route(GridPoint source, GridPoint target, Set<GridPoint> obstacles,
                                                   List<Wire> placed) {
        List<List<GridPoint>> candidates = source.x() == target.x() || source.y() == target.y()
                ? List.of(List.of(source, target))
                : List.of(List.of(source, new GridPoint(source.x(), target.y()), target),
                List.of(source, new GridPoint(target.x(), source.y()), target));
        return candidates.stream().filter(points -> isClear(points, obstacles, placed)).findFirst();
    }

    /** No obstacle on the route but its two ends, and no wire under its bend. */
    private static boolean isClear(List<GridPoint> points, Set<GridPoint> obstacles, List<Wire> placed) {
        GridPoint source = points.get(0);
        GridPoint target = points.get(points.size() - 1);
        for (int index = 0; index + 1 < points.size(); index++) {
            Wire segment = new Wire(new ElementId("bridge"), points.get(index), points.get(index + 1));
            boolean touches = obstacles.stream()
                    .anyMatch(point -> !point.equals(source) && !point.equals(target) && segment.contains(point));
            if (touches) {
                return false;
            }
        }
        for (int index = 1; index + 1 < points.size(); index++) {
            GridPoint bend = points.get(index);
            if (placed.stream().anyMatch(wire -> wire.contains(bend))) {
                return false;
            }
        }
        return true;
    }
}
