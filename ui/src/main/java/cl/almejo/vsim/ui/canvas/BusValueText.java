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

    /**
     * The bits of {@code value}, most significant first, each {@code 0}, {@code 1} or {@code Z}, grouped in fours from
     * the least significant end; {@link #CONFLICT} when the drivers disagree.
     */
    public static String binary(Value value) {
        if (value.conflict()) {
            return CONFLICT;
        }
        StringBuilder text = new StringBuilder();
        for (int index = value.width() - 1; index >= 0; index--) {
            text.append(switch (value.bit(index)) {
                case ZERO -> '0';
                case ONE -> '1';
                case HIGH_IMPEDANCE -> 'Z';
            });
            if (index > 0 && index % 4 == 0) {
                text.append(' ');
            }
        }
        return text.toString();
    }
}
