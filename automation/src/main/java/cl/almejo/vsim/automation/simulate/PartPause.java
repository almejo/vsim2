package cl.almejo.vsim.automation.simulate;

import java.util.Objects;

/**
 * The last time a part paused the simulation by itself (a «Punto de parada» whose condition became true, a Terminal
 * that received its «Detener al recibir» text): the simulated {@code component} that asked (an element id, or a path
 * inside a user component), the simulation {@code time} it asked at (a decimal string) and the {@code reason} as the
 * status bar says it to the user («Punto de parada «cuenta»: entrada = 1001»). When the engine itself stopped at one of
 * its limits (a zero-delay loop, an event past the largest time, a clock-edge step that found no edge) the
 * {@code component} is empty and the reason says what happened.
 */
public record PartPause(String component, String time, String reason) {

    public PartPause {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(reason, "reason");
    }
}
