package cl.almejo.vsim.automation.simulate;

import java.util.Objects;

/**
 * What happened to one value of a {@code set_inputs}, in the order asked: {@code reason} (English) says why it was not
 * set, or that the event budget cut its effects short; empty when it was simply set.
 */
public record InputResult(String elementId, String value, boolean accepted, String reason) {

    public InputResult {
        Objects.requireNonNull(elementId, "elementId");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(reason, "reason");
    }
}
