package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.wiring.TidyOptions;

/** What «Ordenar circuito» does: separate what overlaps, then the wire rules of {@code wires}. */
public record CircuitTidyOptions(boolean separateOverlaps, TidyOptions wires) {

    public static final CircuitTidyOptions DEFAULTS = new CircuitTidyOptions(true, TidyOptions.DEFAULTS);
}
