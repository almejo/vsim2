package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.awt.geom.Point2D;
import java.util.Optional;

/** Points where a wire can start: component pins and grid points on existing wires. */
public final class ConnectionPoints {

    public static final double PIN_RADIUS = 0.6;

    private final ComponentTypeRegistry registry;

    public ConnectionPoints(ComponentTypeRegistry registry) {
        this.registry = registry;
    }

    /** A pin location together with the component that owns it. */
    public record PinHit(GridPoint point, Component component) {
    }

    /** The closest pin within {@link #PIN_RADIUS} grid units of {@code gridPoint}, with its owner. */
    public Optional<PinHit> pinNear(CircuitDocument document, Point2D gridPoint) {
        PinHit closest = null;
        double closestDistance = PIN_RADIUS;
        for (Component component : document.components()) {
            ComponentType type = registry.typeOf(component);
            for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                GridPoint pin = type.pinLocation(component, pinIndex);
                double distance = gridPoint.distance(pin.x(), pin.y());
                if (distance <= closestDistance) {
                    closest = new PinHit(pin, component);
                    closestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(closest);
    }

    /** The grid point of {@code wire} nearest to {@code gridPoint}. */
    public static GridPoint pointOnWire(Wire wire, Point2D gridPoint) {
        if (wire.isHorizontal()) {
            long x = Math.round(gridPoint.getX());
            return new GridPoint((int) Math.max(wire.start().x(), Math.min(wire.end().x(), x)), wire.start().y());
        }
        long y = Math.round(gridPoint.getY());
        return new GridPoint(wire.start().x(), (int) Math.max(wire.start().y(), Math.min(wire.end().y(), y)));
    }
}
