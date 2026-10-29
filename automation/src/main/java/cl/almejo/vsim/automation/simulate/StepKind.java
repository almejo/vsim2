package cl.almejo.vsim.automation.simulate;

/**
 * How far one {@code step} goes; its {@code amount} is a count for the first three and simulation time for the last.
 */
public enum StepKind {
    /** {@code amount} times «Avanzar»: every event of the next pending time. */
    NEXT_EVENT,
    /** {@code amount} times «Avanzar al próximo flanco de reloj»: to right after a clock's next edge, either kind. */
    NEXT_CLOCK_EDGE,
    /** {@code amount} times «Avanzar un ciclo de reloj»: to right after a clock's next falling edge. */
    NEXT_CLOCK_CYCLE,
    /** «Avanzar N t»: {@code amount} units of simulation time. */
    DURATION
}
