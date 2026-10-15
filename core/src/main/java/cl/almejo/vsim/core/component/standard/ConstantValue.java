package cl.almejo.vsim.core.component.standard;

import java.math.BigInteger;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The text of a «Constante»: {@code 0b1010}, {@code 0xFF} or a decimal number, unsigned, at most 32 bits. Spaces
 * around the text and {@code _} separators are accepted; {@link #normalize} gives the canonical text that is stored.
 */
public final class ConstantValue {

    public static final int MAXIMUM_WIDTH = 32;

    public static final String AUTO = "auto";
    public static final String BINARY = "binary";
    public static final String HEX = "hex";
    public static final String DECIMAL = "decimal";

    private static final Pattern BINARY_TEXT = Pattern.compile("0[bB]([01]+)");
    private static final Pattern HEX_TEXT = Pattern.compile("0[xX]([0-9a-fA-F]+)");
    private static final Pattern DECIMAL_TEXT = Pattern.compile("[0-9]+");

    private ConstantValue() {
    }

    /** Whether {@code text} is a number in one of the three notations (whatever its size). */
    public static boolean isValidText(String text) {
        return text != null && parseUnbounded(text) != null;
    }

    /** Whether {@code text} is a valid number that fits {@code width} bits. */
    public static boolean fitsText(String text, int width) {
        BigInteger value = text == null ? null : parseUnbounded(text);
        return value != null && value.bitLength() <= width;
    }

    /**
     * The value of {@code text}.
     *
     * @throws IllegalArgumentException when it is empty, negative, garbage or wider than 32 bits
     */
    public static long parse(String text) {
        BigInteger value = text == null ? null : parseUnbounded(text);
        if (value == null) {
            throw new IllegalArgumentException("not a number (use 0b..., 0x... or a decimal): " + text);
        }
        if (value.bitLength() > MAXIMUM_WIDTH) {
            throw new IllegalArgumentException("does not fit in " + MAXIMUM_WIDTH + " bits: " + text);
        }
        return value.longValueExact();
    }

    public static boolean fits(long value, int width) {
        return value >= 0 && (width >= Long.SIZE || value >>> width == 0);
    }

    /** The canonical text: lowercase prefix, uppercase hex digits, no separators, no extra leading zeros. */
    public static String normalize(String text) {
        BigInteger value = text == null ? null : parseUnbounded(text);
        if (value == null) {
            throw new IllegalArgumentException("not a number (use 0b..., 0x... or a decimal): " + text);
        }
        String cleaned = clean(text);
        if (BINARY_TEXT.matcher(cleaned).matches()) {
            return "0b" + value.toString(2);
        }
        if (HEX_TEXT.matcher(cleaned).matches()) {
            return "0x" + value.toString(16).toUpperCase(Locale.ROOT);
        }
        return value.toString();
    }

    /** {@code auto} becomes {@code binary} up to 4 bits and {@code hex} from 5 bits; the others stay. */
    public static String resolveFormat(String format, int width) {
        if (AUTO.equals(format)) {
            return width <= 4 ? BINARY : HEX;
        }
        return format;
    }

    /** The value as the body of the component shows it: binary padded to the width, hex with {@code 0x}, decimal. */
    public static String display(long value, int width, String format) {
        return switch (resolveFormat(format, width)) {
            case BINARY -> {
                String digits = Long.toBinaryString(value);
                yield "0".repeat(Math.max(0, width - digits.length())) + digits;
            }
            case HEX -> "0x" + Long.toHexString(value).toUpperCase(Locale.ROOT);
            case DECIMAL -> Long.toString(value);
            default -> throw new IllegalArgumentException("unknown format: " + format);
        };
    }

    /** The characters of the longest text {@link #display} gives at this width and format. */
    public static int longestDisplay(int width, String format) {
        return switch (resolveFormat(format, width)) {
            case BINARY -> width;
            case HEX -> 2 + (width + 3) / 4;
            case DECIMAL -> Long.toString((1L << width) - 1).length();
            default -> throw new IllegalArgumentException("unknown format: " + format);
        };
    }

    private static String clean(String text) {
        return text.trim().replace("_", "");
    }

    /** The number {@code text} writes, or null when it is not a number. Never throws. */
    private static BigInteger parseUnbounded(String text) {
        String cleaned = clean(text);
        var binary = BINARY_TEXT.matcher(cleaned);
        if (binary.matches()) {
            return new BigInteger(binary.group(1), 2);
        }
        var hex = HEX_TEXT.matcher(cleaned);
        if (hex.matches()) {
            return new BigInteger(hex.group(1), 16);
        }
        if (DECIMAL_TEXT.matcher(cleaned).matches()) {
            return new BigInteger(cleaned);
        }
        return null;
    }
}
