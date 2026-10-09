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
 * that stands for it. A bridge never touches anything else (another pin, a wire end, a wire under its bend); a pin
 * without such a route gets no bridge.
 */
public final class PinBridges {

    private PinBridges() {
    }

    /**
     * The bridges for replacing {@code replaced} by an instance of {@code definition} (unrotated) at
     * {@code instancePosition}, in pinout order, with fresh wire ids from {@code document}.
     */
    public static List<Wire> wires(CircuitDocument document, Collection<ElementId> replaced,
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
        List<GridPoint> sources = new ArrayList<>();
        List<GridPoint> targets = new ArrayList<>();
        for (DefinitionPin pin : definition.pinout()) {
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
        List<Integer> connected = new ArrayList<>();
        for (int index = 0; index < sources.size(); index++) {
            GridPoint source = sources.get(index);
            if (!source.equals(targets.get(index)) && touchesOutside(source, outsidePins, outsideWires)
                    && !sameWire(source, targets.get(index), outsideWires)) {
                connected.add(index);
                obstacles.add(source);
            }
        }

        List<Wire> placed = new ArrayList<>(outsideWires);
        List<Wire> bridges = new ArrayList<>();
        for (int index : connected) {
            GridPoint source = sources.get(index);
            GridPoint target = targets.get(index);
            route(source, target, obstacles, placed).ifPresent(points -> {
                for (int point = 0; point + 1 < points.size(); point++) {
                    Wire bridge = new Wire(document.newWireId(), points.get(point), points.get(point + 1));
                    bridges.add(bridge);
                    placed.add(bridge);
                }
                obstacles.addAll(points);
            });
        }
        return bridges;
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
