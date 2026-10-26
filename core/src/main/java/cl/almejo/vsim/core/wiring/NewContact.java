package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.Objects;
import java.util.Optional;

/**
 * A point where a move joins things that were not on one net before (in VSim a pin or a wire end on a wire's interior
 * connects). When a pin is there, {@code componentId} and {@code pinName} name it (preferring one that stays); a
 * contact between wires only has both empty. For the label next to the dot, {@code movingPinName} is the name of a
 * named pin that moves to this point and {@code metPinName} the name of a named pin of another part that stays here;
 * each is empty when there is no such pin (or its name is blank).
 */
public record NewContact(GridPoint point, Optional<ElementId> componentId, Optional<String> pinName,
                         Optional<String> movingPinName, Optional<String> metPinName) {

    public NewContact {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(componentId, "componentId");
        Objects.requireNonNull(pinName, "pinName");
        Objects.requireNonNull(movingPinName, "movingPinName");
        Objects.requireNonNull(metPinName, "metPinName");
    }
}
