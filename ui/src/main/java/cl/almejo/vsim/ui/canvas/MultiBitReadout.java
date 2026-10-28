package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * What hovering a multi-bit Switch or LED says: the value in binary, hex and decimal («0b1010_0110 = 0xA6 = 166»; the
 * LED also gives the signed one, «(con signo: -90)», and a Switch only the decimal its «Números» chose), and «bit 3» when the pointer is over one bit. A value with a Z or a conflict has no
 * number to give, so it is written bit by bit like a bus tooltip.
 */
public final class MultiBitReadout {

    /** Between the value and the bit. */
    private static final String SEPARATOR = " · ";

    /** Which decimal the readout gives: both (the LED), or only the one the Switch's «Números» chose. */
    public enum Numbers { BOTH, UNSIGNED, SIGNED }

    private MultiBitReadout() {
    }

    /** Which decimal a Switch's hover gives, from its «Números» parameter (any other part gives both). */
    public static Numbers numbersOf(ComponentType type, Map<String, Object> parameters) {
        if (!type.id().equals(StandardComponentTypes.SWITCH)) {
            return Numbers.BOTH;
        }
        return StandardComponentTypes.SIGNEDNESS_SIGNED.equals(
                type.resolveParameters(parameters).get(StandardComponentTypes.SIGNEDNESS))
                ? Numbers.SIGNED : Numbers.UNSIGNED;
    }

    /** The value a Switch or LED state holds, if {@code state} is one of theirs. */
    public static Optional<Value> valueOf(VisibleState state) {
        return switch (state) {
            case VisibleState.SwitchState switchState -> Optional.of(switchState.value());
            case VisibleState.LedState led -> Optional.of(led.value());
            default -> Optional.empty();
        };
    }

    /** The tooltip: the value (when known) and the bit under the pointer (when over one); empty if neither. */
    public static Optional<String> text(Optional<Value> value, OptionalInt bit) {
        return text(value, bit, Numbers.BOTH);
    }

    /** As {@link #text(Optional, OptionalInt)}, with the decimal(s) {@code decimals} says. */
    public static Optional<String> text(Optional<Value> value, OptionalInt bit, Numbers decimals) {
        Optional<String> numbers = value.map(found -> numbers(found, decimals));
        Optional<String> bitName = bit.isPresent()
                ? Optional.of(Messages.format("multibit.bit", bit.getAsInt()))
                : Optional.empty();
        if (numbers.isPresent() && bitName.isPresent()) {
            return Optional.of(numbers.get() + SEPARATOR + bitName.get());
        }
        return numbers.isPresent() ? numbers : bitName;
    }

    private static String numbers(Value value, Numbers decimals) {
        if (!value.isFullyDefined()) {
            return BusValueText.binary(value);
        }
        String binary = "0b" + BusValueText.binary(value).replace(' ', '_');
        String hex = ConstantValue.display(value.bits(), value.width(), ConstantValue.HEX);
        String unsigned = ConstantValue.display(value.bits(), value.width(), ConstantValue.DECIMAL);
        String signed = ConstantValue.display(value.bits(), value.width(), ConstantValue.SIGNED);
        return switch (decimals) {
            case BOTH -> Messages.format("multibit.readout", binary, hex, unsigned, signed);
            case UNSIGNED -> Messages.format("multibit.readoutOne", binary, hex, unsigned);
            case SIGNED -> Messages.format("multibit.readoutOne", binary, hex, signed);
        };
    }
}
