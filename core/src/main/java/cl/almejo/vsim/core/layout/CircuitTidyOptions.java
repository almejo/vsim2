package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.wiring.TidyOptions;

/**
 * What «Ordenar circuito» does, in this order: separate what overlaps, align parts in columns, straighten wires by
 * moving parts, then the wire rules of {@code wires} (spec §4, §5).
 */
public record CircuitTidyOptions(boolean separateOverlaps, boolean alignColumns, boolean straighten,
                                 TidyOptions wires) {

    /** Phase 1's behaviour: separate, then the default wire rules (no aligning, no straightening). */
    public static final CircuitTidyOptions DEFAULTS = new CircuitTidyOptions(true, TidyOptions.DEFAULTS);

    /** Everything on, as the dialog starts the first time. */
    public static final CircuitTidyOptions ALL = new CircuitTidyOptions(true, true, true, TidyOptions.DEFAULTS);

    /** Separating and the wire rules only (aligning and straightening off). */
    public CircuitTidyOptions(boolean separateOverlaps, TidyOptions wires) {
        this(separateOverlaps, false, false, wires);
    }
}
