package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.canvas.BusValueText;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.Optional;
import java.util.Set;

/**
 * The tooltip of one component pin (M6.1-E, spec §6b.4): «in0 · bit 0 (el de la derecha) = Z (sin conectar)». The
 * pin name; its bit when the component reads its one-bit pins as a number (the 7-segment displays: pin {@code k} is
 * bit {@code k}; their bus input has no bit); its width when it is wider than one bit; and the value, when one is given, written like the bus
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
     * The tooltip of pin {@code pinIndex} of {@code component} (of {@code type});
     * {@code unconnected} = no wire and no other pin on it; {@code value} empty = no current snapshot.
     */
    static String describe(Component component, ComponentType type, int pinIndex, boolean unconnected,
                           Optional<Value> value) {
        PinSpec pin = type.pin(pinIndex);
        StringBuilder text = new StringBuilder(pin.name());
        if (NUMBER_READERS.contains(component.typeId()) && pin.width() == 1) {
            text.append(SEPARATOR).append(Messages.format(bitKey(component, type, pinIndex), pinIndex));
        }
        if (pin.width() > 1) {
            text.append(SEPARATOR).append(Messages.format("pin.width", pin.width()));
        }
        value.ifPresent(current -> text.append(" = ").append(valueText(current, unconnected)));
        return text.toString();
    }

    /**
     * Bit 0 and the last bit are named by where they are drawn, so a rotated display still points the right way. The
     * last bit is the last one-bit pin: the display's bus input comes after the bits.
     */
    private static String bitKey(Component component, ComponentType type, int pinIndex) {
        int last = type.pinCount() - 1;
        while (last > 0 && type.pin(last).width() > 1) {
            last--;
        }
        if (pinIndex != 0 && pinIndex != last) {
            return "pin.bit";
        }
        GridPoint first = type.pinLocation(component, 0);
        GridPoint end = type.pinLocation(component, last);
        boolean isFirst = pinIndex == 0;
        if (first.x() != end.x()) {
            boolean firstIsRight = first.x() > end.x();
            return isFirst == firstIsRight ? "pin.bitRight" : "pin.bitLeft";
        }
        boolean firstIsBottom = first.y() > end.y();
        return isFirst == firstIsBottom ? "pin.bitBottom" : "pin.bitTop";
    }

    private static String valueText(Value value, boolean unconnected) {
        String shown = value.width() > 1 && !value.conflict()
                ? BusValueText.of(value) + " (" + BusValueText.binary(value) + ")"
                : BusValueText.binary(value);
        return unconnected && value.isFullyHighImpedance() ? Messages.format("pin.unconnected", shown) : shown;
    }
}
