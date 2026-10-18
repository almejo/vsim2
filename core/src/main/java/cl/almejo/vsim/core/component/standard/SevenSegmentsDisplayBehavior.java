package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.BitState;
import cl.almejo.vsim.core.value.Value;

/**
 * Shows an N-bit value: the bus input (pin N) when any of its bits is not Z, else the N one-bit inputs combined (pin 0 =
 * least significant bit). A bus with some Z bits is shown as it is, Z bits included.
 */
final class SevenSegmentsDisplayBehavior implements Behavior {

    private final int bitCount;
    private Value value;

    SevenSegmentsDisplayBehavior(int bitCount) {
        this.bitCount = bitCount;
        this.value = Value.highImpedance(bitCount);
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
        Value bus = context.input(bitCount);
        if (bus.width() == bitCount && !bus.isFullyHighImpedance()) {
            value = bus;
            return;
        }
        long bits = 0;
        long highImpedance = 0;
        for (int index = 0; index < bitCount; index++) {
            BitState bit = context.input(index).bit(0);
            if (bit == BitState.ONE) {
                bits |= 1L << index;
            } else if (bit == BitState.HIGH_IMPEDANCE) {
                highImpedance |= 1L << index;
            }
        }
        value = new Value(bitCount, bits, highImpedance, false);
    }

    @Override
    public VisibleState visibleState() {
        return new VisibleState.SevenSegmentsState(value);
    }
}
