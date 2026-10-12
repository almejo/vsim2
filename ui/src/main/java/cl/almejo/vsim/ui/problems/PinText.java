package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.canvas.BusValueText;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.Optional;
import java.util.Set;

/**
 * The tooltip of one component pin (M6.1-E, spec §6b.4): «in0 · bit 0 (el de la derecha) = Z (sin conectar)». The
 * pin name; its bit when the component reads its pins as a number (the 7-segment displays: pin {@code k} is bit
 * {@code k}); its width when it is wider than one bit; and the value, when one is given, written like the bus
 * tooltip («0x9 (1001)», {@code X} for a conflict). A fully Z value on a pin that touches nothing says so.
 */
final class PinText {

    /** Between the parts of a pin tooltip, and before a problem of its net. */
    static final String SEPARATOR = " · ";

    private static final Set<String> NUMBER_READERS = Set.of(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY,
            StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY_DOUBLE);

    private PinText() {
    }

    /**
     * The tooltip of pin {@code pinIndex} of a component of {@code type} (whose id is {@code typeId});
     * {@code unconnected} = no wire and no other pin on it; {@code value} empty = no current snapshot.
     */
    static String describe(String typeId, ComponentType type, int pinIndex, boolean unconnected,
                           Optional<Value> value) {
        PinSpec pin = type.pin(pinIndex);
        StringBuilder text = new StringBuilder(pin.name());
        if (NUMBER_READERS.contains(typeId)) {
            String key = pinIndex == 0 ? "pin.bitRight"
                    : pinIndex == type.pinCount() - 1 ? "pin.bitLeft" : "pin.bit";
            text.append(SEPARATOR).append(Messages.format(key, pinIndex));
        }
        if (pin.width() > 1) {
            text.append(SEPARATOR).append(Messages.format("pin.width", pin.width()));
        }
        value.ifPresent(current -> text.append(" = ").append(valueText(current, unconnected)));
        return text.toString();
    }

    private static String valueText(Value value, boolean unconnected) {
        String shown = value.width() > 1 && !value.conflict()
                ? BusValueText.of(value) + " (" + BusValueText.binary(value) + ")"
                : BusValueText.binary(value);
        return unconnected && value.isFullyHighImpedance() ? Messages.format("pin.unconnected", shown) : shown;
    }
}
