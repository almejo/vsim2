package cl.almejo.vsim.automation.simulate;

import cl.almejo.vsim.core.value.Value;

import java.math.BigInteger;
import java.util.Locale;

/** How simulation values are written for the AI and read from it. */
public final class ValueText {

    private ValueText() {
    }

    /**
     * Reads {@code text} as a defined value {@code width} bits wide: {@code 0b…} binary, {@code 0x…} hexadecimal,
     * otherwise a decimal number (no sign); underscores between digits are allowed.
     *
     * @throws IllegalArgumentException with an English message for the AI when it is not a number or does not fit
     */
    public static Value parse(String text, int width) {
        String cleaned = text.strip().replace("_", "").toLowerCase(Locale.ROOT);
        int radix = 10;
        String digits = cleaned;
        if (cleaned.startsWith("0b")) {
            radix = 2;
            digits = cleaned.substring(2);
        } else if (cleaned.startsWith("0x")) {
            radix = 16;
            digits = cleaned.substring(2);
        }
        BigInteger number;
        try {
            if (digits.isEmpty() || digits.startsWith("-") || digits.startsWith("+")) {
                throw new NumberFormatException();
            }
            number = new BigInteger(digits, radix);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("\"" + text + "\" is not a value: write 0b1010, 0x3F or a decimal number");
        }
        if (number.bitLength() > width) {
            throw new IllegalArgumentException("\"" + text + "\" does not fit in " + width
                    + (width == 1 ? " bit" : " bits") + (radix == 10 ? " (binary is written 0b1010)" : ""));
        }
        return Value.of(width, number.longValue());
    }

    /** The bits, most significant first: {@code 0}, {@code 1} or {@code Z} for each. */
    public static String bits(Value value) {
        StringBuilder builder = new StringBuilder(value.width());
        for (int index = value.width() - 1; index >= 0; index--) {
            builder.append(switch (value.bit(index)) {
                case ZERO -> '0';
                case ONE -> '1';
                case HIGH_IMPEDANCE -> 'Z';
            });
        }
        return builder.toString();
    }

    /** The high-impedance mask, most significant bit first: {@code 1} where the bit is Z. */
    public static String highImpedanceMask(Value value) {
        StringBuilder builder = new StringBuilder(value.width());
        for (int index = value.width() - 1; index >= 0; index--) {
            builder.append((value.highImpedanceMask() >>> index & 1L) == 1L ? '1' : '0');
        }
        return builder.toString();
    }

    /** {@link #bits}, with {@code X!} in front for a conflict (two drivers disagree): the form of a table cell. */
    public static String cell(Value value) {
        return (value.conflict() ? "X!" : "") + bits(value);
    }
}
