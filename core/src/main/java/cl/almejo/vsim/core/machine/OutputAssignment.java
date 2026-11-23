package cl.almejo.vsim.core.machine;

import java.util.Objects;

/** One output given a value by a state (Moore, {@code Z=1}) or by a transition (Mealy, {@code x / Z=1}). */
public record OutputAssignment(String output, boolean value) {

    public OutputAssignment {
        Objects.requireNonNull(output, "output");
        if (output.isBlank()) {
            throw new IllegalArgumentException("an output assignment needs an output name");
        }
        output = output.strip();
    }
}
