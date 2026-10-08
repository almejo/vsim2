package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.BitState;
import cl.almejo.vsim.core.value.Value;

/** Combines N one-bit inputs into an N-bit value (pin 0 = least significant bit). */
final class SevenSegmentsDisplayBehavior implements Behavior {

    private final int pinCount;
    private Value value;

    SevenSegmentsDisplayBehavior(int pinCount) {
        this.pinCount = pinCount;
        this.value = Value.highImpedance(pinCount);
    }

    @Override
    public void initialize(BehaviorContext context) {
        recompute(context);
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
        recompute(context);
    }

    private void recompute(BehaviorContext context) {
        long bits = 0;
        long highImpedance = 0;
        for (int index = 0; index < pinCount; index++) {
            BitState bit = context.input(index).bit(0);
            if (bit == BitState.ONE) {
                bits |= 1L << index;
            } else if (bit == BitState.HIGH_IMPEDANCE) {
                highImpedance |= 1L << index;
            }
        }
        value = new Value(pinCount, bits, highImpedance, false);
    }

    @Override
    public VisibleState visibleState() {
        return new VisibleState.SevenSegmentsState(value);
    }
}
