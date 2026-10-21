package cl.almejo.vsim.automation;

import java.util.Objects;

/**
 * One pin of one part as the AI names it everywhere (spec §10, A§5.1): the part's id, the pin's name and its index in
 * the pins of the <em>specialised</em> instance (a part's parameters can change its pins, so an index is only valid
 * with the version it was read at). The name says which pin it is; the index tells apart pins whose names repeat.
 */
public record PinReference(String elementId, String pinName, int index) {

    public PinReference {
        Objects.requireNonNull(elementId, "elementId");
        Objects.requireNonNull(pinName, "pinName");
        if (index < 0) {
            throw new IllegalArgumentException("a pin index is never negative: " + index);
        }
    }
}
