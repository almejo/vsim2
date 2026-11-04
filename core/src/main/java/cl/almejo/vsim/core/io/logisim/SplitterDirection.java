package cl.almejo.vsim.core.io.logisim;

/**
 * Which way the signals cross a Logisim splitter, which itself has no direction: the nets step finds it bit by bit
 * (gap analysis §2.2) and the mapper makes a «Separador de bus» or a «Unión de bus» from it.
 */
public enum SplitterDirection {
    /** The wide end is driven from outside and the ends are read: a «Separador de bus». */
    SPLIT,
    /** The ends are driven and the wide end is read: a «Unión de bus». */
    JOIN
}
