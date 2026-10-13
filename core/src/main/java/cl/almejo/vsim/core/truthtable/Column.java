package cl.almejo.vsim.core.truthtable;

import cl.almejo.vsim.core.value.Value;

import java.util.Objects;

/**
 * One column of a truth table: an input (a switch or an input pin) or an output (an LED or an output pin) of the
 * evaluated circuit. {@code componentId} is the component's id in that circuit; {@code width} is its pin width in bits.
 */
public record Column(String name, String componentId, int width) {

    public Column {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(componentId, "componentId");
        if (width < 1 || width > Value.MAXIMUM_WIDTH) {
            throw new IllegalArgumentException("column width must be between 1 and 64: " + width);
        }
    }
}
