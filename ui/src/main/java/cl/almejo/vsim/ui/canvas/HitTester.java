package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Optional;

/** Finds the element under a point given in (fractional) grid units. */
public final class HitTester {

    public static final double WIRE_TOLERANCE = 0.4;

    private final ComponentTypeRegistry registry;

    public HitTester(ComponentTypeRegistry registry) {
        this.registry = registry;
    }

    public Optional<DocumentElement> elementAt(CircuitDocument document, Point2D gridPoint) {
        List<Component> components = document.components();
        for (int index = components.size() - 1; index >= 0; index--) {
            Component component = components.get(index);
            Bounds bounds = registry.require(component.typeId()).bounds(component);
            if (gridPoint.getX() >= bounds.x() && gridPoint.getX() <= bounds.maximumX()
                    && gridPoint.getY() >= bounds.y() && gridPoint.getY() <= bounds.maximumY()) {
                return Optional.of(component);
            }
        }
        Wire closest = null;
        double closestDistance = WIRE_TOLERANCE;
        for (Wire wire : document.wires()) {
            double distance = Line2D.ptSegDist(wire.start().x(), wire.start().y(), wire.end().x(), wire.end().y(),
                    gridPoint.getX(), gridPoint.getY());
            if (distance <= closestDistance) {
                closest = wire;
                closestDistance = distance;
            }
        }
        return Optional.ofNullable(closest);
    }
}
