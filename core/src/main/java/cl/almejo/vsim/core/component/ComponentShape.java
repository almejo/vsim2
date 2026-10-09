package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.geometry.GridSize;

import java.util.List;
import java.util.Objects;

/** The size and pins of a component type for one set of parameters. */
public record ComponentShape(GridSize size, List<PinSpec> pins) {
    public ComponentShape {
        Objects.requireNonNull(size, "size");
        pins = List.copyOf(pins);
    }
}
