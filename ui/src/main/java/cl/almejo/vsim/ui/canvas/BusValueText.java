package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.i18n.Messages;

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
     * {@code value} in a probe's format: bytes as characters ({@code 'f'}, {@code LF}) for {@link Probe#FORMAT_ASCII}
     * when every bit is defined, else {@link #of(Value)}.
     */
    public static String of(Value value, String format) {
        if (!Probe.FORMAT_HEX.equals(format) && value.isFullyDefined() && !value.conflict()) {
            return ConstantValue.display(value.bits(), value.width(), Probe.valueFormatOf(format),
                    Probe.fractionBitsOf(format));
        }
        return of(value);
    }

    private static final String SEPARATOR = " · ";

    /**
     * Every other way {@code value} can be read, for the tooltip of a bus (the hex and binary come first): decimal,
     * signed, octal, the character when it is one byte of 7 or 8 bits and the float when the width has an IEEE 754
     * format (16, 32 or 64), joined by dots. Empty for a value that is not fully defined.
     */
    public static String otherFormats(Value value) {
        if (!value.isFullyDefined() || value.conflict()) {
            return "";
        }
        long bits = value.bits();
        int width = value.width();
        java.util.List<String> parts = new java.util.ArrayList<>();
        parts.add(Messages.format("bus.tooltip.decimal", ConstantValue.display(bits, width, ConstantValue.DECIMAL)));
        parts.add(Messages.format("bus.tooltip.signed", ConstantValue.display(bits, width, ConstantValue.SIGNED)));
        parts.add(Messages.format("bus.tooltip.octal", ConstantValue.display(bits, width, ConstantValue.OCTAL)));
        if (width == 7 || width == Byte.SIZE) {
            parts.add(Messages.format("bus.tooltip.character", ConstantValue.display(bits, width, ConstantValue.ASCII)));
        }
        if (width == 16 || width == 32 || width == 64) {
            parts.add(Messages.format("bus.tooltip.float", ConstantValue.display(bits, width, ConstantValue.FLOAT)));
        }
        return String.join(SEPARATOR, parts);
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
