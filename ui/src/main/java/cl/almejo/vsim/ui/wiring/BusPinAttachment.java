package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.canvas.ConnectionPoints.PinHit;

import java.util.Optional;

/**
 * Where a bus splitter (on an output pin) or a bus joiner (on an input pin) goes so that its bus pin sits
 * {@link #DISTANCE} grid units outward from a component's bus pin, its bits pointing away, joined by one straight wire.
 *
 * @param typeId    {@code bus-splitter} or {@code bus-joiner}
 * @param width     the pin's width, which is the part's width
 * @param position  the part's origin
 * @param rotation  the part's rotation
 * @param wireStart the component pin
 * @param wireEnd   the new part's bus pin
 */
public record BusPinAttachment(String typeId, int width, GridPoint position, Rotation rotation, GridPoint wireStart,
                               GridPoint wireEnd) {

    /** Grid units between the component's pin and the new part's bus pin. */
    public static final int DISTANCE = 2;

    private static final int MINIMUM_WIDTH = 2;
    private static final int MAXIMUM_WIDTH = 32;

    /** The plan for {@code pin}; empty for a pin of 1 bit, wider than a splitter goes, bidirectional or adaptive. */
    public static Optional<BusPinAttachment> plan(ComponentTypeRegistry registry, PinHit pin) {
        ComponentType type = registry.typeOf(pin.component());
        PinSpec spec = type.pin(pin.pinIndex());
        if (spec.isAdaptiveWidth() || spec.width() < MINIMUM_WIDTH || spec.width() > MAXIMUM_WIDTH
                || spec.direction() == PinDirection.BIDIRECTIONAL) {
            return Optional.empty();
        }
        GridPoint outward = outward(type.bounds(pin.component()), pin.point());
        boolean splitting = spec.direction() == PinDirection.OUTPUT;
        GridPoint busPin = pin.point().translate(outward.x() * DISTANCE, outward.y() * DISTANCE);
        // the part's local +x holds the bits of a splitter and the bus of a joiner: bits away = +x outward / inward
        GridPoint bitsDirection = splitting ? outward : new GridPoint(-outward.x(), -outward.y());
        Rotation rotation = rotationOf(bitsDirection);
        GridPoint local = splitting ? GridPoint.ORIGIN : new GridPoint(2, 0);
        GridPoint turned = rotation.apply(local);
        GridPoint position = new GridPoint(busPin.x() - turned.x(), busPin.y() - turned.y());
        return Optional.of(new BusPinAttachment(
                splitting ? StandardComponentTypes.BUS_SPLITTER : StandardComponentTypes.BUS_JOINER,
                spec.width(), position, rotation, pin.point(), busPin));
    }

    /** The unit vector pointing away from the side of {@code bounds} nearest to {@code pin}. */
    static GridPoint outward(Bounds bounds, GridPoint pin) {
        int left = Math.abs(pin.x() - bounds.x());
        int right = Math.abs(bounds.maximumX() - pin.x());
        int top = Math.abs(pin.y() - bounds.y());
        int bottom = Math.abs(bounds.maximumY() - pin.y());
        int nearest = Math.min(Math.min(left, right), Math.min(top, bottom));
        if (nearest == left) {
            return new GridPoint(-1, 0);
        }
        if (nearest == right) {
            return new GridPoint(1, 0);
        }
        return nearest == top ? new GridPoint(0, -1) : new GridPoint(0, 1);
    }

    private static Rotation rotationOf(GridPoint direction) {
        for (Rotation rotation : Rotation.values()) {
            if (rotation.apply(new GridPoint(1, 0)).equals(direction)) {
                return rotation;
            }
        }
        throw new IllegalArgumentException("not a unit direction: " + direction);
    }
}
