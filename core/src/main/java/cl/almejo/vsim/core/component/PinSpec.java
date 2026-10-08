package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.value.Value;

import java.util.Objects;

/** A pin of a component type; {@code position} is relative to the component origin before rotation. */
public record PinSpec(String name, GridPoint position, PinDirection direction, int width) {

    public PinSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(direction, "direction");
        if (width < 1 || width > Value.MAXIMUM_WIDTH) {
            throw new IllegalArgumentException("pin width must be between 1 and 64: " + width);
        }
    }

    public static PinSpec input(String name, int x, int y) {
        return new PinSpec(name, new GridPoint(x, y), PinDirection.INPUT, 1);
    }

    public static PinSpec output(String name, int x, int y) {
        return new PinSpec(name, new GridPoint(x, y), PinDirection.OUTPUT, 1);
    }

    public boolean isInput() {
        return direction != PinDirection.OUTPUT;
    }

    public boolean isDriver() {
        return direction != PinDirection.INPUT;
    }
}
