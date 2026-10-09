package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

/**
 * Copies each bit of the bus (pin 0) to its own pin (b0 = bit 0 on pin 1) with no delay. A Z bit stays Z; a bus on a
 * width mismatch reads Z (the engine gives such pins Z), so every bit is Z.
 */
final class BusSplitterBehavior implements Behavior {

    private final int width;

    BusSplitterBehavior(int width) {
        this.width = width;
    }

    @Override
    public void initialize(BehaviorContext context) {
        split(context);
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
        split(context);
    }

    private void split(BehaviorContext context) {
        Value bus = context.input(0);
        for (int bit = 0; bit < width; bit++) {
            context.drive(bit + 1, bitOf(bus, bit), 0);
        }
    }

    private static Value bitOf(Value bus, int bit) {
        if (bit >= bus.width()) {
            return Value.HIGH_IMPEDANCE;
        }
        return switch (bus.bit(bit)) {
            case ZERO -> Value.ZERO;
            case ONE -> Value.ONE;
            case HIGH_IMPEDANCE -> Value.HIGH_IMPEDANCE;
        };
    }
}
