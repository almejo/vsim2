package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.value.Value;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * The rules every «Bloques» block follows (M7 spec §2.1), so they all agree:
 * <ul>
 *     <li>a 1-bit <b>control</b> input (Cin, EN, CLR, LD) that is Z, because nothing drives it, takes its rest value;
 *     a conflict on it is undefined ({@link #control});</li>
 *     <li>a <b>data</b> input counts only when every bit is 0 or 1 and there is no conflict ({@link #unsigned},
 *     {@link #signed}, {@link #allDefined}); otherwise the outputs that depend on it are driven
 *     {@link #undefined} (all Z, what the student already reads as «not driven»);</li>
 *     <li>arithmetic runs in {@code long}, exact for widths up to 32, and {@link #result} keeps only the bits of the
 *     output's width, so no sign extension or carry leaks out of it.</li>
 * </ul>
 * Blocks take their width from {@link #WIDTH} and their delay from {@link StandardComponentTypes#DELAY}: one delay from
 * any input to every output.
 */
public final class BlockSignals {

    /** «Ancho (bits)» of a block: 1..32 bits, 8 by default (like the bus splitter and joiner and the Constante). */
    public static final ParameterSpec WIDTH = ParameterSpec.integer(StandardComponentTypes.WIDTH, 8, 1, 32);

    private BlockSignals() {
    }

    /**
     * A 1-bit control input as a boolean: Z (unconnected or released) gives {@code restValue}, 0 and 1 give
     * themselves, a conflict (or a value only partly Z) gives empty, which the block treats as undefined.
     */
    public static Optional<Boolean> control(Value input, boolean restValue) {
        if (input.conflict()) {
            return Optional.empty();
        }
        if (input.isFullyHighImpedance()) {
            return Optional.of(restValue);
        }
        if (input.highImpedanceMask() != 0L) {
            return Optional.empty();
        }
        return Optional.of(input.bits() != 0L);
    }

    /** The value as an unsigned number, empty unless it is fully defined (no Z bit, no conflict). */
    public static OptionalLong unsigned(Value value) {
        return value.isFullyDefined() ? OptionalLong.of(value.bits()) : OptionalLong.empty();
    }

    /**
     * The value as a two's complement number of its width (a 4-bit {@code 1011} is -5, a 1-bit {@code 1} is -1),
     * empty unless it is fully defined.
     */
    public static OptionalLong signed(Value value) {
        if (!value.isFullyDefined()) {
            return OptionalLong.empty();
        }
        int unusedBits = Long.SIZE - value.width();
        return OptionalLong.of(value.bits() << unusedBits >> unusedBits);
    }

    /** True when every value is fully defined (no Z bit, no conflict). */
    public static boolean allDefined(Value... values) {
        for (Value value : values) {
            if (!value.isFullyDefined()) {
                return false;
            }
        }
        return true;
    }

    /** A defined output of {@code width} bits: the low {@code width} bits of {@code bits}, the rest dropped. */
    public static Value result(int width, long bits) {
        return Value.of(width, bits & Value.maskFor(width));
    }

    /** What a block drives on an output of {@code width} bits whose result is undefined: all Z. */
    public static Value undefined(int width) {
        return Value.highImpedance(width);
    }
}
