package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds where wires cross without connecting, so the canvas can leave a gap in the vertical one. Pure: no drawing.
 * Every horizontal wire is checked against every vertical wire, which is fine for educational circuits. Each vertical
 * wire gets at most one crossing per point: overlapping collinear horizontal wires (a loaded file may have them)
 * would otherwise cross it twice at the same point.
 */
public final class WireCrossings {

    private static final Comparator<Crossing> ORDER = Comparator.comparing(Crossing::point)
            .thenComparing(crossing -> crossing.horizontalWire().value())
            .thenComparing(crossing -> crossing.verticalWire().value());

    private WireCrossings() {
    }

    /** The crossings of {@code document}, ordered by point (x, then y), then by wire ids; one per point and vertical wire. */
    public static List<Crossing> of(CircuitDocument document, ComponentTypeRegistry registry) {
        Set<GridPoint> pointsOfInterest = new HashSet<>();
        List<Wire> horizontals = new ArrayList<>();
        List<Wire> verticals = new ArrayList<>();
        for (Wire wire : document.wires()) {
            pointsOfInterest.add(wire.start());
            pointsOfInterest.add(wire.end());
            if (wire.isHorizontal()) {
                horizontals.add(wire);
            } else {
                verticals.add(wire);
            }
        }
        for (Component component : document.components()) {
            ComponentType type = registry.typeOf(component);
            for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                pointsOfInterest.add(type.pinLocation(component, pinIndex));
            }
        }
        List<Crossing> crossings = new ArrayList<>();
        for (Wire horizontal : horizontals) {
            for (Wire vertical : verticals) {
                GridPoint point = new GridPoint(vertical.start().x(), horizontal.start().y());
                boolean insideHorizontal = point.x() > horizontal.start().x() && point.x() < horizontal.end().x();
                boolean insideVertical = point.y() > vertical.start().y() && point.y() < vertical.end().y();
                if (insideHorizontal && insideVertical && !pointsOfInterest.contains(point)) {
                    crossings.add(new Crossing(point, horizontal.id(), vertical.id()));
                }
            }
        }
        crossings.sort(ORDER);
        Set<List<Object>> seen = new HashSet<>();
        return crossings.stream()
                .filter(crossing -> seen.add(List.of(crossing.point(), crossing.verticalWire())))
                .toList();
    }
}
