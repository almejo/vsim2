package cl.almejo.vsim.ui.paint;

/** «Cursor» of a Terminal (spec §8.5), by the value saved in the «cursor» parameter. */
public enum TerminalCursorShape {
    /** «Bloque lleno»: the cell inverted. */
    BLOCK("block"),
    /** «Bloque vacío»: a 1 px outline of the cell. */
    HOLLOW("hollow"),
    /** «Subrayado»: 2 px at the bottom. */
    UNDERLINE("underline"),
    /** «Barra»: 2 px at the left. */
    BAR("bar"),
    /** «Barra con topes (I‑beam)»: a 2 px bar with 6 px serifs at the top and bottom. */
    IBEAM("ibeam"),
    /** «Cursor de Automan»: a glowing seven-pointed star that never blinks. */
    AUTOMAN("automan"),
    /** «Oculto». */
    NONE("none");

    private final String value;

    TerminalCursorShape(String value) {
        this.value = value;
    }

    /** The shape saved as {@code value}; the bar (the VT100 profile's) for anything else. */
    public static TerminalCursorShape of(Object value) {
        for (TerminalCursorShape shape : values()) {
            if (shape.value.equals(String.valueOf(value))) {
                return shape;
            }
        }
        return BAR;
    }

    /** Whether «Parpadea» applies: Automan's star never blinks and a hidden cursor has nothing to blink. */
    public boolean blinks() {
        return this != AUTOMAN && this != NONE;
    }
}
