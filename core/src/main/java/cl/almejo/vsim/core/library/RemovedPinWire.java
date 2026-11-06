package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.Objects;
import java.util.Optional;

/**
 * A wire end left loose because the pin it went to was removed from its component by an edit inside it (spec §6.1:
 * «its outside wires are left loose and listed in Problemas»): the end at {@code position} touches no other pin.
 *
 * @param containingDefinition the definition whose body holds the piece and the wire; empty for the circuit itself
 * @param piece                the piece whose pin went (an id of that body, or of the circuit)
 * @param pinName              the name the removed pin had
 * @param position             where the loose wire end is, in that body's (or the circuit's) coordinates
 */
public record RemovedPinWire(Optional<String> containingDefinition, ElementId piece, String pinName,
                             GridPoint position) {

    public RemovedPinWire {
        Objects.requireNonNull(containingDefinition, "containingDefinition");
        Objects.requireNonNull(piece, "piece");
        Objects.requireNonNull(pinName, "pinName");
        Objects.requireNonNull(position, "position");
    }
}
