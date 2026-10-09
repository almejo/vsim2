package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.value.Value;

/** How a bus value is written on the canvas, in tooltips and in the component tree. */
public final class BusValueText {

    private BusValueText() {
    }

    /** {@code 0x} and the hex digits of {@code value}: {@code Z} for a digit all in Z, {@code ?} for one partly in Z. */
    public static String of(Value value) {
        return "0x" + value.hex();
    }
}
