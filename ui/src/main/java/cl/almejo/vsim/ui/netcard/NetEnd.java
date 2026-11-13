package cl.almejo.vsim.ui.netcard;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.Objects;
import java.util.Optional;

/**
 * One end of a net on the signal card: a part's pin that drives or reads it, «AND U1 · out». {@code valueText} is the
 * value that pin imposes, known only when several drivers fight (a short circuit); {@code position} is where the
 * pin is drawn, for the ring of the net's halo. A part inside a component's body is never an end: the component's
 * own pin is.
 */
public record NetEnd(ElementId componentId, int pinIndex, String partText, String pinText,
                     Optional<String> valueText, GridPoint position) {

    public NetEnd {
        Objects.requireNonNull(componentId, "componentId");
        Objects.requireNonNull(partText, "partText");
        Objects.requireNonNull(pinText, "pinText");
        Objects.requireNonNull(valueText, "valueText");
        Objects.requireNonNull(position, "position");
    }

    /** The same end with the value its pin imposes. */
    NetEnd withValue(String value) {
        return new NetEnd(componentId, pinIndex, partText, pinText, Optional.of(value), position);
    }
}
