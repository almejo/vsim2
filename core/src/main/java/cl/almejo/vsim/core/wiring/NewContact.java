package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.Objects;
import java.util.Optional;

/**
 * A point where a move joins things that were not on one net before (in VSim a pin or a wire end on a wire's interior
 * connects). When a pin is there, {@code componentId} and {@code pinName} name it; a contact between wires only has
 * both empty.
 */
public record NewContact(GridPoint point, Optional<ElementId> componentId, Optional<String> pinName) {

    public NewContact {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(componentId, "componentId");
        Objects.requireNonNull(pinName, "pinName");
    }
}
