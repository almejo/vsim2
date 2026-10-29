package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.value.Value;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * What a click does to a Switch's value, and how that value is written back into its «Valor»: a click is an edit of
 * the parameter (decided 2026-10-28), so the editor and the simulation agree on what a click gives.
 */
public final class SwitchValues {

    private SwitchValues() {
    }

    /** The value a Switch with these resolved parameters drives: all Z for {@code Z}, else its number. */
    public static Value valueOf(Map<String, Object> resolvedParameters) {
        return SwitchBehavior.parseNumber((String) resolvedParameters.get(StandardComponentTypes.VALUE),
                (Integer) resolvedParameters.get(StandardComponentTypes.WIDTH));
    }

    /** A click on the whole switch: all ones becomes all zeros, anything else (zeros, Z, a mixed value) all ones. */
    public static Value toggled(Value value) {
        Value allOnes = Value.of(value.width(), Value.maskFor(value.width()));
        return value.equals(allOnes) ? Value.of(value.width(), 0L) : allOnes;
    }

    /**
     * A click on bit {@code bit} (0 = least significant) of a multi-bit switch: that bit flipped; empty for a bit
     * outside the width or a value with Z, which a click leaves alone.
     */
    public static Optional<Value> withBitFlipped(Value value, int bit) {
        if (bit < 0 || bit >= value.width() || !value.isFullyDefined()) {
            return Optional.empty();
        }
        return Optional.of(Value.of(value.width(), value.bits() ^ (1L << bit)));
    }

    /**
     * The «Valor» that {@code text} becomes when «Números» goes back to «Sin signo» on a switch of {@code width} bits: a
     * negative decimal that fits is written as its bit pattern read unsigned ({@code -1} on 8 bits is {@code 255}), so
     * the switch keeps driving the same bits (review M-2). Empty for any other text, which stays as it is.
     */
    public static Optional<String> unsignedText(String text, int width) {
        String stripped = text == null ? "" : text.strip();
        if (!stripped.startsWith("-") || !ConstantValue.isValidText(stripped)) {
            return Optional.empty();
        }
        OptionalLong bits = ConstantValue.bitsOf(stripped, width);
        return bits.isPresent() ? Optional.of(Long.toUnsignedString(bits.getAsLong())) : Optional.empty();
    }

    /**
     * The «Valor» text that stores {@code value} for a Switch whose resolved parameters are {@code resolvedParameters}:
     * {@code 0} or {@code 1} on one bit ({@code Z} for Z); on several bits the number in the base the text was
     * written in ({@code 0b…} stays binary, {@code 0x…} hexadecimal, a decimal stays decimal, with its sign under
     * «Con signo»), canonical as {@link SwitchBehavior#normalizeValue} stores it. Empty for a multi-bit value with Z or
     * a conflict, which «Valor» cannot hold.
     */
    public static Optional<String> textFor(Value value, Map<String, Object> resolvedParameters) {
        int width = value.width();
        if (!value.isFullyDefined()) {
            return width == 1 && value.isFullyHighImpedance() ? Optional.of("Z") : Optional.empty();
        }
        String previous = String.valueOf(resolvedParameters.get(StandardComponentTypes.VALUE)).strip();
        long bits = value.bits();
        String text;
        if (width == 1 || previous.startsWith("0b")) {
            text = "0b" + Long.toBinaryString(bits);
        } else if (previous.startsWith("0x")) {
            text = "0x" + Long.toHexString(bits).toUpperCase(Locale.ROOT);
        } else if (StandardComponentTypes.SIGNEDNESS_SIGNED.equals(
                resolvedParameters.get(StandardComponentTypes.SIGNEDNESS))) {
            text = Long.toString(ConstantValue.signed(bits, width));
        } else {
            text = Long.toUnsignedString(bits);
        }
        return Optional.of(SwitchBehavior.normalizeValue(text));
    }
}
