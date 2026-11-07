package cl.almejo.vsim.automation.simulate;

import java.util.Objects;

/**
 * The last time a part paused the simulation by itself (a «Punto de parada» whose condition became true, a Terminal
 * that received its «Detener al recibir» text): the simulated {@code component} that asked (an element id, or a path
 * inside a user component), the simulation {@code time} it asked at (a decimal string) and the {@code reason} as the
 * status bar says it to the user («Punto de parada «cuenta»: entrada = 1001»).
 */
public record PartPause(String component, String time, String reason) {

    public PartPause {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(reason, "reason");
    }
}
