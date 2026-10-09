package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

/** Packs pins b0..b(width-1) (pins 1..width) into the bus on pin 0 with no delay; b0 is bit 0 and a Z bit stays Z. */
final class BusJoinerBehavior implements Behavior {

    private final int width;

    BusJoinerBehavior(int width) {
        this.width = width;
    }

    @Override
    public void initialize(BehaviorContext context) {
        join(context);
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
        join(context);
    }

    private void join(BehaviorContext context) {
        long bits = 0L;
        long highImpedance = 0L;
        for (int bit = 0; bit < width; bit++) {
            switch (context.input(bit + 1).bit(0)) {
                case ONE -> bits |= 1L << bit;
                case HIGH_IMPEDANCE -> highImpedance |= 1L << bit;
                case ZERO -> {
                }
            }
        }
        context.drive(0, new Value(width, bits, highImpedance, false), 0);
    }
}
