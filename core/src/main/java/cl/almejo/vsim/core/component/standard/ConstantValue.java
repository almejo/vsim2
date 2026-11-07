package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.value.Value;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
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
    public static final String OCTAL = "octal";
    /**
     * Bytes as characters: printable ASCII quoted ({@code 'A'}), control bytes by name ({@code LF}), anything else
     * hex. {@link #bitsOf(String, int, String, int)} reads all three back in this format.
     */
    public static final String ASCII = "ascii";
    /**
     * IEEE 754 of the value's own width: half (16), single (32) or double (64). At any other width there is no float
     * to show, so the value is shown in hex ({@link #resolveFormat}).
     */
    public static final String FLOAT = "float";
    /** Two's complement with {@link #FRACTION} bits after the point. */
    public static final String FIXED = "fixed";

    /** The name of the «Mostrar como» parameter. */
    public static final String FORMAT = "format";
    /**
     * «Mostrar como»: shared by the Constante, the input and output pins and the blocks that show a value. It is
     * display-only: changing it keeps the running behaviour (a register's contents, a pin's typed value).
     */
    public static final ParameterSpec FORMAT_PARAMETER = formatChoice().asDisplayOnly();
    /** The name of the «Bits de fracción» parameter. */
    public static final String FRACTION = "fraction";
    /** «Bits de fracción», shown only with {@link #FIXED}; display-only like {@link #FORMAT_PARAMETER}. */
    public static final ParameterSpec FRACTION_PARAMETER = fractionSpec().asDisplayOnly();
    /**
     * The Constante's own «Mostrar como» and «Bits de fracción»: not display-only, because its typed value is read in
     * the format ({@code 1.5} is other bits as a float than as a fixed-point number), so changing them rebuilds it.
     */
    public static final ParameterSpec READING_FORMAT_PARAMETER = formatChoice();
    public static final ParameterSpec READING_FRACTION_PARAMETER = fractionSpec();

    private static ParameterSpec formatChoice() {
        return ParameterSpec.choice(FORMAT, AUTO,
                List.of(AUTO, BINARY, HEX, OCTAL, DECIMAL, SIGNED, ASCII, FLOAT, FIXED));
    }

    private static ParameterSpec fractionSpec() {
        return ParameterSpec.integer(FRACTION, 0, 0, Value.MAXIMUM_WIDTH).visibleWhen(FORMAT, FIXED);
    }

    private static final Pattern BINARY_TEXT = Pattern.compile("0[bB]([01]+)");
    private static final Pattern HEX_TEXT = Pattern.compile("0[xX]([0-9a-fA-F]+)");
    private static final Pattern OCTAL_TEXT = Pattern.compile("0[oO]([0-7]+)");
    private static final Pattern DECIMAL_TEXT = Pattern.compile("-?[0-9]+");
    private static final Pattern CHARACTER_TEXT = Pattern.compile("'(.)'");
    private static final Pattern FRACTIONAL_TEXT =
            Pattern.compile("[+-]?([0-9]+\\.?[0-9]*|\\.[0-9]+)([eE][+-]?[0-9]+)?");
    private static final Pattern SPECIAL_FLOAT_TEXT = Pattern.compile("([+-]?)(inf|nan)", Pattern.CASE_INSENSITIVE);

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

    /** Whether {@code text} reads as a number in {@code format} (see {@link #bitsOf(String, int, String, int)}). */
    public static boolean isValidText(String text, String format) {
        if (isValidText(text)) {
            return true;
        }
        if (text == null) {
            return false;
        }
        String cleaned = clean(text);
        return switch (format == null ? AUTO : format) {
            case ASCII -> controlByName(cleaned) >= 0;
            case FLOAT -> FRACTIONAL_TEXT.matcher(cleaned).matches() || SPECIAL_FLOAT_TEXT.matcher(cleaned).matches();
            case FIXED -> FRACTIONAL_TEXT.matcher(cleaned).matches();
            default -> false;
        };
    }

    /**
     * The bits {@code text} writes at {@code width} in {@code format}: the notations of {@link #bitsOf(String, int)}
     * plus, in {@link #FLOAT} (only at 16, 32 and 64 bits) and {@link #FIXED}, a number such as {@code 1.5}
     * ({@code inf} and {@code nan} too in float) read into the part's own format, and in {@link #ASCII} a control name
     * ({@code LF}). A decimal in float or fixed is the number, not the bits; {@code 0x...}, {@code 0b...} and
     * {@code 0o...} are always the bits. Empty when the text is not valid, does not fit, or is a float too big for
     * the width.
     */
    public static OptionalLong bitsOf(String text, int width, String format, int fractionBits) {
        if (text == null) {
            return OptionalLong.empty();
        }
        String effective = resolveFormat(format == null ? AUTO : format, width);
        String cleaned = clean(text);
        boolean prefixed = BINARY_TEXT.matcher(cleaned).matches() || HEX_TEXT.matcher(cleaned).matches()
                || OCTAL_TEXT.matcher(cleaned).matches();
        if (FLOAT.equals(effective) && !prefixed) {
            return floatBits(cleaned, width);
        }
        if (FIXED.equals(effective) && !prefixed) {
            return fixedBits(cleaned, width, fractionBits);
        }
        if (ASCII.equals(effective) && controlByName(cleaned) >= 0) {
            return OptionalLong.of(controlByName(cleaned));
        }
        return bitsOf(text, width);
    }

    /** As {@link #fitsText(String, int)} for the notations of {@link #bitsOf(String, int, String, int)}. */
    public static boolean fitsText(String text, int width, String format, int fractionBits) {
        return bitsOf(text, width, format, fractionBits).isPresent();
    }

    private static OptionalLong floatBits(String cleaned, int width) {
        var special = SPECIAL_FLOAT_TEXT.matcher(cleaned);
        if (special.matches()) {
            boolean notANumber = special.group(2).equalsIgnoreCase("nan");
            double value = notANumber ? Double.NaN
                    : cleaned.startsWith("-") ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            return OptionalLong.of(switch (width) {
                case 16 -> Short.toUnsignedLong(Float.floatToFloat16((float) value));
                case 32 -> Integer.toUnsignedLong(Float.floatToIntBits((float) value));
                default -> Double.doubleToLongBits(value);
            });
        }
        if (!FRACTIONAL_TEXT.matcher(cleaned).matches()) {
            return OptionalLong.empty();
        }
        double exact = Double.parseDouble(cleaned);
        if (Double.isInfinite(exact)) {
            return OptionalLong.empty();
        }
        float single = Float.parseFloat(cleaned);
        return switch (width) {
            case 16 -> {
                short half = Float.floatToFloat16(single);
                yield Float.isInfinite(Float.float16ToFloat(half)) ? OptionalLong.empty()
                        : OptionalLong.of(Short.toUnsignedLong(half));
            }
            case 32 -> Float.isInfinite(single) ? OptionalLong.empty()
                    : OptionalLong.of(Integer.toUnsignedLong(Float.floatToIntBits(single)));
            default -> OptionalLong.of(Double.doubleToLongBits(exact));
        };
    }

    private static OptionalLong fixedBits(String cleaned, int width, int fractionBits) {
        if (!FRACTIONAL_TEXT.matcher(cleaned).matches()) {
            return OptionalLong.empty();
        }
        BigInteger scaled = new BigDecimal(cleaned).multiply(new BigDecimal(
                BigInteger.ONE.shiftLeft(clampFraction(fractionBits, width)))).setScale(0, RoundingMode.HALF_EVEN)
                .toBigIntegerExact();
        // two's complement: -2^(width-1) .. 2^(width-1) - 1 (the bit length of a negative number leaves the sign out)
        return scaled.bitLength() <= width - 1 ? OptionalLong.of(scaled.longValue() & Value.maskFor(width))
                : OptionalLong.empty();
    }

    private static int clampFraction(int fractionBits, int width) {
        return Math.max(0, Math.min(fractionBits, width));
    }

    private static int controlByName(String text) {
        return text.equals(DELETE_NAME) ? DELETE : CONTROL_NAMES.indexOf(text);
    }

    /**
     * The canonical text: lowercase prefix, uppercase hex digits, no separators, no extra leading zeros; a negative
     * decimal keeps its sign ({@code -0} is {@code 0}).
     */
    public static String normalize(String text) {
        String cleaned = text == null ? "" : clean(text);
        if (SPECIAL_FLOAT_TEXT.matcher(cleaned).matches()) {
            return cleaned.toLowerCase(Locale.ROOT);
        }
        if (FRACTIONAL_TEXT.matcher(cleaned).matches() && !DECIMAL_TEXT.matcher(cleaned).matches()
                || controlByName(cleaned) >= 0) {
            // a float or fixed-point number, or a control name: kept as typed, read in the part's format
            return cleaned;
        }
        BigInteger value = text == null ? null : parseUnbounded(text);
        if (value == null) {
            throw new IllegalArgumentException("not a number (use 0b..., 0o..., 0x..., a decimal or 'A'): " + text);
        }
        if (CHARACTER_TEXT.matcher(cleaned).matches()) {
            return cleaned;
        }
        if (OCTAL_TEXT.matcher(cleaned).matches()) {
            return "0o" + value.toString(8);
        }
        if (BINARY_TEXT.matcher(cleaned).matches()) {
            return "0b" + value.toString(2);
        }
        if (HEX_TEXT.matcher(cleaned).matches()) {
            return "0x" + value.toString(16).toUpperCase(Locale.ROOT);
        }
        return value.toString();
    }

    /**
     * {@code auto} becomes {@code binary} up to 4 bits and {@code hex} from 5 bits; {@code float} becomes {@code hex}
     * at a width that has no IEEE 754 format (not 16, 32 or 64); the others stay.
     */
    public static String resolveFormat(String format, int width) {
        if (AUTO.equals(format)) {
            return width <= 4 ? BINARY : HEX;
        }
        if (FLOAT.equals(format) && width != 16 && width != 32 && width != 64) {
            return HEX;
        }
        return format;
    }

    /**
     * The value as the body of a component shows it: binary padded to the width, hex with {@code 0x}, decimal, or
     * signed decimal (two's complement). Only the lowest {@code width} bits of {@code bits} count.
     */
    public static String display(long bits, int width, String format) {
        return display(bits, width, format, 0);
    }

    /** As {@link #display(long, int, String)}, with {@code fractionBits} for {@link #FIXED}. */
    public static String display(long bits, int width, String format, int fractionBits) {
        long masked = bits & Value.maskFor(width);
        return switch (resolveFormat(format, width)) {
            case BINARY -> {
                String digits = Long.toBinaryString(masked);
                yield "0".repeat(Math.max(0, width - digits.length())) + digits;
            }
            case HEX -> "0x" + Long.toHexString(masked).toUpperCase(Locale.ROOT);
            case DECIMAL -> Long.toUnsignedString(masked);
            case SIGNED -> Long.toString(signed(masked, width));
            case OCTAL -> "0o" + Long.toOctalString(masked);
            case ASCII -> character(masked);
            case FLOAT -> floatText(masked, width);
            case FIXED -> fixedText(masked, width, fractionBits);
            default -> throw new IllegalArgumentException("unknown format: " + format);
        };
    }

    private static String floatText(long masked, int width) {
        if (width == 16) {
            float half = Float.float16ToFloat((short) masked);
            if (Float.isNaN(half) || Float.isInfinite(half)) {
                return special(half);
            }
            // the shortest digits that read back as the same half
            for (int digits = 1; digits <= 9; digits++) {
                float rounded = new BigDecimal(half).round(new MathContext(digits)).floatValue();
                if (Float.floatToFloat16(rounded) == (short) masked) {
                    return Float.toString(rounded);
                }
            }
            return Float.toString(half);
        }
        if (width == 32) {
            float single = Float.intBitsToFloat((int) masked);
            return Float.isNaN(single) || Float.isInfinite(single) ? special(single) : Float.toString(single);
        }
        double value = Double.longBitsToDouble(masked);
        return Double.isNaN(value) || Double.isInfinite(value) ? special(value) : Double.toString(value);
    }

    private static String special(double value) {
        return Double.isNaN(value) ? "nan" : value > 0 ? "inf" : "-inf";
    }

    private static String fixedText(long masked, int width, int fractionBits) {
        int fraction = clampFraction(fractionBits, width);
        BigDecimal value = new BigDecimal(BigInteger.valueOf(signed(masked, width)))
                .divide(new BigDecimal(BigInteger.ONE.shiftLeft(fraction))).stripTrailingZeros();
        return value.setScale(Math.max(value.scale(), fraction > 0 ? 1 : 0)).toPlainString();
    }

    /**
     * A text in {@code format} that {@link #bitsOf} reads back as {@code bits}: what a value editor starts with
     * ({@link #display} writes binary without its {@code 0b}, which would read as a decimal).
     */
    public static String editableText(long bits, int width, String format) {
        return editableText(bits, width, format, 0);
    }

    /** As {@link #editableText(long, int, String)}, with {@code fractionBits} for {@link #FIXED}. */
    public static String editableText(long bits, int width, String format, int fractionBits) {
        String shown = display(bits, width, format, fractionBits);
        return BINARY.equals(resolveFormat(format, width)) ? "0b" + shown : shown;
    }

    /** The characters of the longest text {@link #display} gives at this width and format. */
    public static int longestDisplay(int width, String format) {
        return longestDisplay(width, format, 0);
    }

    /** As {@link #longestDisplay(int, String)}, with {@code fractionBits} for {@link #FIXED}. */
    public static int longestDisplay(int width, String format, int fractionBits) {
        return switch (resolveFormat(format, width)) {
            case BINARY -> width;
            case HEX -> 2 + (width + 3) / 4;
            case DECIMAL -> Long.toUnsignedString(Value.maskFor(width)).length();
            case SIGNED -> Long.toString(signed(1L << (width - 1), width)).length();
            case OCTAL -> 2 + (width + 2) / 3;
            case ASCII -> Math.max(MAXIMUM_CONTROL_NAME, 2 + (Math.max(width, Byte.SIZE) + 3) / 4);
            // a sign, the digits of the largest integer part, the point and every fraction digit
            case FIXED -> {
                int fraction = clampFraction(fractionBits, width);
                yield 1 + BigInteger.ONE.shiftLeft(width - 1 - fraction).toString().length() + 1 + fraction;
            }
            // the longest of a half is -6.1035156E-5, of a single -1.17549435E-38, of a double -2.2250738585072014E-308
            case FLOAT -> width == 16 ? 13 : width == 32 ? 15 : 24;
            default -> throw new IllegalArgumentException("unknown format: " + format);
        };
    }

    /** The control bytes 0..31 by their ASCII names; 127 is {@code DEL}. */
    private static final List<String> CONTROL_NAMES = List.of("NUL", "SOH", "STX", "ETX", "EOT", "ENQ", "ACK", "BEL",
            "BS", "HT", "LF", "VT", "FF", "CR", "SO", "SI", "DLE", "DC1", "DC2", "DC3", "DC4", "NAK", "SYN", "ETB",
            "CAN", "EM", "SUB", "ESC", "FS", "GS", "RS", "US");
    private static final String DELETE_NAME = "DEL";
    private static final int FIRST_PRINTABLE = 0x20;
    private static final int DELETE = 0x7F;
    private static final int MAXIMUM_CONTROL_NAME = 3;

    /** One byte as a character: {@code 'A'}, a control name such as {@code LF}, or {@code 0xE9}. */
    private static String character(long value) {
        if (value >= FIRST_PRINTABLE && value < DELETE) {
            return "'" + (char) value + "'";
        }
        if (value >= 0 && value < FIRST_PRINTABLE) {
            return CONTROL_NAMES.get((int) value);
        }
        if (value == DELETE) {
            return DELETE_NAME;
        }
        return "0x" + Long.toHexString(value).toUpperCase(Locale.ROOT);
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
        String trimmed = text.trim();
        return CHARACTER_TEXT.matcher(trimmed).matches() ? trimmed : trimmed.replace("_", "");
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
        var octal = OCTAL_TEXT.matcher(cleaned);
        if (octal.matches()) {
            return new BigInteger(octal.group(1), 8);
        }
        var character = CHARACTER_TEXT.matcher(cleaned);
        if (character.matches() && character.group(1).charAt(0) >= FIRST_PRINTABLE
                && character.group(1).charAt(0) < DELETE) {
            return BigInteger.valueOf(character.group(1).charAt(0));
        }
        if (DECIMAL_TEXT.matcher(cleaned).matches()) {
            return new BigInteger(cleaned);
        }
        return null;
    }
}
