package cl.almejo.vsim.core.component.appearance;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;

/**
 * Where a pin of a drawn symbol starts its stub and which way the stub points: inwards from the box edge the pin is
 * nearest to (a side edge when it is as near as the top or the bottom). Shared by the painter (the stub and the pin's
 * name) and by the check that a stub reaches the outline.
 *
 * @param x          the pin, in the unit asked for
 * @param y          the pin, in the unit asked for
 * @param width      the box, in the same unit
 * @param height     the box, in the same unit
 * @param directionX 1 or -1 on a side edge (into the box), 0 on the top or the bottom
 * @param directionY 1 or -1 on the top or the bottom (into the box), 0 on a side edge
 */
public record PinStub(double x, double y, double width, double height, double directionX, double directionY) {

    /** The stub of the pin at {@code pin} of a {@code box}, both measured in {@code unit}s per grid unit. */
    public static PinStub of(GridPoint pin, GridSize box, double unit) {
        double x = pin.x() * unit;
        double y = pin.y() * unit;
        double width = box.width() * unit;
        double height = box.height() * unit;
        boolean onSide = Math.min(x, width - x) <= Math.min(y, height - y);
        double directionX = onSide ? (x <= width / 2 ? 1 : -1) : 0;
        double directionY = onSide ? 0 : (y <= height / 2 ? 1 : -1);
        return new PinStub(x, y, width, height, directionX, directionY);
    }

    /** Whether the pin is on the left or the right edge (its stub is horizontal). */
    public boolean onSide() {
        return directionX != 0;
    }
}
