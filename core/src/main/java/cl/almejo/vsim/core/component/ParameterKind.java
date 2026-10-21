package cl.almejo.vsim.core.component;

/**
 * How a parameter's value is read and edited. {@code COLOR} is a {@code CHOICE} among colour names (an LED's
 * «red», «green», …): stored and checked the same way, but the editor shows it as colour swatches.
 */
public enum ParameterKind {
    INTEGER, TEXT, CHOICE, COLOR
}
