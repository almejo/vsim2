package cl.almejo.vsim.automation.simulate;

import java.util.Objects;

/**
 * A value the AI gives an input component (a switch, a push button or an input pin of the circuit's top level), as
 * text: {@code 0b1010} binary, {@code 0x3F} hexadecimal or a plain decimal number such as {@code 1}; see
 * {@link ValueText}.
 */
public record InputValue(String elementId, String value) {

    public InputValue {
        Objects.requireNonNull(elementId, "elementId");
        Objects.requireNonNull(value, "value");
    }
}
