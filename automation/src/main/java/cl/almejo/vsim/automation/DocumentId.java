package cl.almejo.vsim.automation;

import java.util.Objects;

/**
 * An open circuit as the AI names it: opaque, stable while that circuit stays open, the same for every view of it
 * (two windows of one circuit share one id). Closing the circuit ends the id; it is never given to another circuit.
 */
public record DocumentId(String value) {

    public DocumentId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("a document id is never blank");
        }
    }
}
