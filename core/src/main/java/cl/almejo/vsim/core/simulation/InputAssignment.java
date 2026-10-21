package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.value.Value;

import java.util.Objects;

/** One value for an input component (a switch or an input pin), see {@link SimulationThread#setInputsWithin}. */
public record InputAssignment(String componentId, Value value) {

    public InputAssignment {
        Objects.requireNonNull(componentId, "componentId");
        Objects.requireNonNull(value, "value");
    }
}
