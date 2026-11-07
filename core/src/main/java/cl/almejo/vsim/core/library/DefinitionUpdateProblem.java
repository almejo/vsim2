package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.netlist.PinRef;

import java.util.Objects;

/**
 * What an edit inside a component could not carry into the body of a definition that contains it (spec §6.2 c): a
 * pin of the piece {@code pin.componentId()} of {@code definitionId}'s body. The editor lists it under every place
 * that definition is used, with its route («U2 › U3: pin X sin conectar»).
 *
 * @param definitionId the definition whose body holds the piece
 * @param pin          the piece (an id of that body) and the index of its pin in the edited definition's new pinout
 * @param kind         what happened to the pin
 */
public record DefinitionUpdateProblem(String definitionId, PinRef pin, Kind kind) {

    /** What could not follow. */
    public enum Kind {
        /** The pin's wires could not follow where it moved: it was left unconnected. */
        LOOSENED,
        /** A new pin's spot touched a wire or a pin there: it went to another spot of the piece's edge. */
        PLACED_ELSEWHERE
    }

    public DefinitionUpdateProblem {
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(pin, "pin");
        Objects.requireNonNull(kind, "kind");
    }
}
