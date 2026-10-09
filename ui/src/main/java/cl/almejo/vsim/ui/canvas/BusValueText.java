package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.value.Value;

/** How a bus value is written on the canvas, in tooltips and in the component tree. */
public final class BusValueText {

    private BusValueText() {
    }

    /** The marker for a shorted (conflicting) value, whose bits mean nothing. */
    public static final String CONFLICT = "X";

    /**
     * {@code 0x} and the hex digits of {@code value}: {@code Z} for a digit all in Z, {@code ?} for one partly in Z;
     * {@link #CONFLICT} when the drivers disagree.
     */
    public static String of(Value value) {
        if (value.conflict()) {
            return CONFLICT;
        }
        return "0x" + value.hex();
    }
}
