package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * What hovering a multi-bit Switch or LED says: the value in binary, hex, decimal and signed decimal («0b1010_0110 =
 * 0xA6 = 166 (con signo: -90)»), and «bit 3» when the pointer is over one bit. A value with a Z or a conflict has no
 * number to give, so it is written bit by bit like a bus tooltip.
 */
public final class MultiBitReadout {

    /** Between the value and the bit. */
    private static final String SEPARATOR = " · ";

    private MultiBitReadout() {
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
        Optional<String> numbers = value.map(MultiBitReadout::numbers);
        Optional<String> bitName = bit.isPresent()
                ? Optional.of(Messages.format("multibit.bit", bit.getAsInt()))
                : Optional.empty();
        if (numbers.isPresent() && bitName.isPresent()) {
            return Optional.of(numbers.get() + SEPARATOR + bitName.get());
        }
        return numbers.isPresent() ? numbers : bitName;
    }

    private static String numbers(Value value) {
        if (!value.isFullyDefined()) {
            return BusValueText.binary(value);
        }
        return Messages.format("multibit.readout", "0b" + BusValueText.binary(value).replace(' ', '_'),
                ConstantValue.display(value.bits(), value.width(), ConstantValue.HEX),
                ConstantValue.display(value.bits(), value.width(), ConstantValue.DECIMAL),
                ConstantValue.display(value.bits(), value.width(), ConstantValue.SIGNED));
    }
}
