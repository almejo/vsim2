package cl.almejo.vsim.core.component.appearance;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.Objects;

/** The connection point of a definition's source pin, in whole grid units relative to the box. */
public record PinPlacement(ElementId source, GridPoint offset) {
    public PinPlacement {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(offset, "offset");
    }
}
