package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.value.Value;

import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/**
 * Numbers typed and shown on buses: the text of a «Constante» or of a value typed into a bus input pin
 * ({@code 0b1010}, {@code 0xFF}, a decimal number or, since M7, a negative decimal read in two's complement), and the
 * «Mostrar como» formats a value is written in. Spaces around the text and {@code _} separators are accepted;
 * {@link #normalize} gives the canonical text that is stored.
 */
public final class ConstantValue {

    public static final int MAXIMUM_WIDTH = 32;

    public static final String AUTO = "auto";
    public static final String BINARY = "binary";
    public static final String HEX = "hex";
    public static final String DECIMAL = "decimal";
    /** M7: decimal with sign, the bits read in two's complement. */
    public static final String SIGNED = "signed";

    /** The name of the «Mostrar como» parameter. */
    public static final String FORMAT = "format";
    /**
     * «Mostrar como»: shared by the Constante, the input and output pins and the blocks that show a value. It is
     * display-only: changing it keeps the running behaviour (a register's contents, a pin's typed value).
     */
    public static final ParameterSpec FORMAT_PARAMETER = ParameterSpec.choice(FORMAT, AUTO,
            List.of(AUTO, BINARY, HEX, DECIMAL, SIGNED)).asDisplayOnly();

    private static final Pattern BINARY_TEXT = Pattern.compile("0[bB]([01]+)");
    private static final Pattern HEX_TEXT = Pattern.compile("0[xX]([0-9a-fA-F]+)");
    private static final Pattern DECIMAL_TEXT = Pattern.compile("-?[0-9]+");

    private ConstantValue() {
    }

    /** Whether {@code text} is a number in one of the notations (whatever its size). */
    public static boolean isValidText(String text) {
        return text != null && parseUnbounded(text) != null;
    }

    /**
     * Whether {@code text} is a valid number that fits {@code width} bits: unsigned up to {@code 2^width - 1}, or a
     * negative decimal down to {@code -2^(width-1)}.
     */
    public static boolean fitsText(String text, int width) {
        BigInteger value = text == null ? null : parseUnbounded(text);
        return value != null && fitsWidth(value, width);
    }

    /**
     * The bits {@code text} writes at {@code width} (1..64), a negative decimal in two's complement. Empty when the
     * text is not a number or does not fit the width.
     */
    public static OptionalLong bitsOf(String text, int width) {
        if (width < 1 || width > Value.MAXIMUM_WIDTH) {
            throw new IllegalArgumentException("width must be between 1 and " + Value.MAXIMUM_WIDTH + ": " + width);
        }
        BigInteger value = text == null ? null : parseUnbounded(text);
        if (value == null || !fitsWidth(value, width)) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(value.longValue() & Value.maskFor(width));
    }

    /**
     * The canonical text: lowercase prefix, uppercase hex digits, no separators, no extra leading zeros; a negative
     * decimal keeps its sign ({@code -0} is {@code 0}).
     */
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

    /**
     * The value as the body of a component shows it: binary padded to the width, hex with {@code 0x}, decimal, or
     * signed decimal (two's complement). Only the lowest {@code width} bits of {@code bits} count.
     */
    public static String display(long bits, int width, String format) {
        long masked = bits & Value.maskFor(width);
        return switch (resolveFormat(format, width)) {
            case BINARY -> {
                String digits = Long.toBinaryString(masked);
                yield "0".repeat(Math.max(0, width - digits.length())) + digits;
            }
            case HEX -> "0x" + Long.toHexString(masked).toUpperCase(Locale.ROOT);
            case DECIMAL -> Long.toUnsignedString(masked);
            case SIGNED -> Long.toString(signed(masked, width));
            default -> throw new IllegalArgumentException("unknown format: " + format);
        };
    }

    /**
     * A text in {@code format} that {@link #bitsOf} reads back as {@code bits}: what a value editor starts with
     * ({@link #display} writes binary without its {@code 0b}, which would read as a decimal).
     */
    public static String editableText(long bits, int width, String format) {
        String shown = display(bits, width, format);
        return BINARY.equals(resolveFormat(format, width)) ? "0b" + shown : shown;
    }

    /** The characters of the longest text {@link #display} gives at this width and format. */
    public static int longestDisplay(int width, String format) {
        return switch (resolveFormat(format, width)) {
            case BINARY -> width;
            case HEX -> 2 + (width + 3) / 4;
            case DECIMAL -> Long.toUnsignedString(Value.maskFor(width)).length();
            case SIGNED -> Long.toString(signed(1L << (width - 1), width)).length();
            default -> throw new IllegalArgumentException("unknown format: " + format);
        };
    }

    /** The lowest {@code width} bits of {@code bits} read in two's complement. */
    public static long signed(long bits, int width) {
        int unused = Long.SIZE - width;
        return (bits << unused) >> unused;
    }

    private static boolean fitsWidth(BigInteger value, int width) {
        // the bit length of a negative BigInteger leaves the sign out: -2^(width-1) has width - 1
        return value.signum() < 0 ? value.bitLength() <= width - 1 : value.bitLength() <= width;
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
