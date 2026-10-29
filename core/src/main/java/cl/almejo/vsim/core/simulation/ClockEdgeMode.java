package cl.almejo.vsim.core.simulation;

/**
 * Where an edge step stops: «Avanzar al próximo flanco de reloj» after the next edge of either kind of any clock
 * ({@link #ANY}, half a cycle per step); «Avanzar un ciclo de reloj» after the next falling one ({@link #FALLING},
 * where the flip-flops store).
 */
public enum ClockEdgeMode {
    ANY,
    FALLING;

    /** True when a step that saw {@code edge} stops in this mode (a fall at the same time as a rise counts). */
    boolean stopsAt(ClockEdge edge) {
        return switch (this) {
            case ANY -> true;
            case FALLING -> edge != ClockEdge.RISING;
        };
    }
}
