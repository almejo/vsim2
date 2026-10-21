package cl.almejo.vsim.automation;

import cl.almejo.vsim.core.component.ComponentType;

import java.util.Objects;

/**
 * One pin of one part as the AI names it everywhere (spec §10, A§5.1): the part's id, the pin's name and, for pins
 * whose names repeat on that part, which one of them ({@code index}: 0 for the first pin with that name, so almost
 * always 0). The pins are those of the <em>specialised</em> instance (a part's parameters can change them), so a
 * reference is only valid with the version it was read at.
 */
public record PinReference(String elementId, String pinName, int index) {

    public PinReference {
        Objects.requireNonNull(elementId, "elementId");
        Objects.requireNonNull(pinName, "pinName");
        if (index < 0) {
            throw new IllegalArgumentException("a pin index is never negative: " + index);
        }
    }

    /** The reference to pin number {@code pinIndex} of {@code type} on the part {@code elementId}. */
    public static PinReference of(String elementId, ComponentType type, int pinIndex) {
        String name = type.pin(pinIndex).name();
        int occurrence = 0;
        for (int index = 0; index < pinIndex; index++) {
            if (type.pin(index).name().equals(name)) {
                occurrence++;
            }
        }
        return new PinReference(elementId, name, occurrence);
    }

    /** The pin number of this reference in {@code type}, or -1 when the type has no such pin. */
    public int pinIndexIn(ComponentType type) {
        int seen = 0;
        for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
            if (type.pin(pinIndex).name().equals(pinName) && seen++ == index) {
                return pinIndex;
            }
        }
        return -1;
    }
}
