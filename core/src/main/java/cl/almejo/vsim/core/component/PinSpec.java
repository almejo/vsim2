package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.value.Value;

import java.util.Objects;

/**
 * A pin of a component type; {@code position} is relative to the component origin before rotation. A pin of
 * {@link #ADAPTIVE_WIDTH} (a tunnel's) has no width of its own: it takes the width of the net it ends up in.
 */
public record PinSpec(String name, GridPoint position, PinDirection direction, int width) {

    /**
     * The width of a pin that adapts to its net. The netlist compiler leaves such pins out when it decides a net's
     * width (a net of only adaptive pins and wires is one bit wide), so they never cause a width mismatch; parts with
     * such pins are never simulated (they only join nets), so the engine never sees this width.
     */
    public static final int ADAPTIVE_WIDTH = 0;

    public PinSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(direction, "direction");
        if (width != ADAPTIVE_WIDTH && (width < 1 || width > Value.MAXIMUM_WIDTH)) {
            throw new IllegalArgumentException("pin width must be between 1 and 64: " + width);
        }
    }

    /** A pin with no width of its own (see {@link #ADAPTIVE_WIDTH}); it neither drives nor reads its net. */
    public static PinSpec adaptive(String name, int x, int y) {
        return new PinSpec(name, new GridPoint(x, y), PinDirection.INPUT, ADAPTIVE_WIDTH);
    }

    public boolean isAdaptiveWidth() {
        return width == ADAPTIVE_WIDTH;
    }

    public static PinSpec input(String name, int x, int y) {
        return new PinSpec(name, new GridPoint(x, y), PinDirection.INPUT, 1);
    }

    public static PinSpec output(String name, int x, int y) {
        return new PinSpec(name, new GridPoint(x, y), PinDirection.OUTPUT, 1);
    }

    public static PinSpec input(String name, int x, int y, int width) {
        return new PinSpec(name, new GridPoint(x, y), PinDirection.INPUT, width);
    }

    public static PinSpec output(String name, int x, int y, int width) {
        return new PinSpec(name, new GridPoint(x, y), PinDirection.OUTPUT, width);
    }

    public boolean isInput() {
        return direction != PinDirection.OUTPUT;
    }

    public boolean isDriver() {
        return direction != PinDirection.INPUT;
    }
}
