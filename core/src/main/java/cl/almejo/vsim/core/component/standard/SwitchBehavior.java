package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;

import java.util.Map;

final class SwitchBehavior implements Behavior {

    private Value value;

    SwitchBehavior(Value initialValue) {
        this.value = initialValue;
    }

    /** {@code "1"} is all ones, {@code "0"} all zeros, anything else all Z, {@code width} bits wide. */
    static Value parse(String text, int width) {
        return switch (text) {
            case "1" -> Value.of(width, Value.maskFor(width));
            case "0" -> Value.of(width, 0L);
            default -> Value.highImpedance(width);
        };
    }

    static Value parse(String text) {
        return parse(text, 1);
    }

    /** Whether {@code text} is the Z of a one-bit Switch (any case, as old files and typed values write it). */
    static boolean isHighImpedanceText(String text) {
        return text.strip().equalsIgnoreCase("Z");
    }

    /**
     * The stored text of a Switch's value: {@code Z}, or the canonical number ({@link ConstantValue#normalize}); a
     * number equal to 0 or 1 in any base is the plain digit, which is how a one-bit Switch and its painter read it.
     */
    static String normalizeValue(String text) {
        if (isHighImpedanceText(text)) {
            return "Z";
        }
        String canonical = ConstantValue.normalize(text);
        return switch (canonical) {
            case "0b0", "0x0" -> "0";
            case "0b1", "0x1" -> "1";
            default -> canonical;
        };
    }

    /**
     * The value a Switch starts with: the number {@code text} writes, {@code width} bits wide, or all Z for
     * {@code Z}. A one-bit Switch reads «0» and «1» exactly as it always did.
     */
    static Value parseNumber(String text, int width) {
        if (isHighImpedanceText(text)) {
            return Value.highImpedance(width);
        }
        return Value.of(width, ConstantValue.bitsOf(text, width).orElseThrow());
    }

    @Override
    public void initialize(BehaviorContext context) {
        context.drive(0, value, 0);
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
    }

    @Override
    public boolean isInteractive() {
        return true;
    }

    /** Toggles between all ones and all zeros (Z or a mixed value becomes all ones). */
    @Override
    public void interact(BehaviorContext context) {
        value = SwitchValues.toggled(value);
        context.drive(0, value, 0);
    }

    /** Flips bit {@code bit} (0 = least significant) of a multi-bit switch; a bit outside the width is ignored. */
    @Override
    public void interactBit(BehaviorContext context, int bit) {
        SwitchValues.withBitFlipped(value, bit).ifPresent(flipped -> {
            value = flipped;
            context.drive(0, value, 0);
        });
    }

    /** Drives {@code newValue} with delay 0; it must be as wide as the switch or pin (IllegalArgumentException). */
    @Override
    public boolean setValue(BehaviorContext context, Value newValue) {
        if (newValue.width() != value.width()) {
            throw new IllegalArgumentException("the input is " + value.width() + " bits wide, not "
                    + newValue.width());
        }
        value = newValue;
        context.drive(0, value, 0);
        return true;
    }

    /** A new «Valor» (an undo of a click, a value typed in Propiedades): driven now unless it is the one driven. */
    @Override
    public void takeLiveValue(BehaviorContext context, Map<String, Object> parameters) {
        Value wanted = SwitchValues.valueOf(parameters);
        if (!wanted.equals(value)) {
            setValue(context, wanted);
        }
    }

    @Override
    public VisibleState visibleState() {
        return new VisibleState.SwitchState(value);
    }
}
