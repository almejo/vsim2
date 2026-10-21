package cl.almejo.vsim.automation;

import java.util.Objects;

/**
 * One pin of a placed part as the AI names it (spec §10, A§5.1): the part's element id, the pin's name and its index
 * in the part as its parameters make it (an 8-bit adder has other pins than a 1-bit one). Never pixel coordinates.
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
