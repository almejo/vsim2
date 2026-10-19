package cl.almejo.vsim.core.logic;

/** One output cell of a truth table: 0, 1 or «no importa» (X). */
public enum LogicValue {
    ZERO,
    ONE,
    DONT_CARE;

    /** The next value when a cell is clicked: 0 → 1 → X → 0. */
    public LogicValue next() {
        return switch (this) {
            case ZERO -> ONE;
            case ONE -> DONT_CARE;
            case DONT_CARE -> ZERO;
        };
    }
}
