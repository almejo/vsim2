package cl.almejo.vsim.core.component;

/**
 * The palette sections, in the order the palette lists them (user components come last). {@code INTERFACE} is only
 * for the flattener's internal probe types and never appears in the palette.
 */
public enum ComponentCategory {
    GATES, WIRING, ARITHMETIC, PLEXERS, SEQUENTIAL, MEMORY, INPUT_OUTPUT, DEBUG, INTERFACE, CUSTOM
}
