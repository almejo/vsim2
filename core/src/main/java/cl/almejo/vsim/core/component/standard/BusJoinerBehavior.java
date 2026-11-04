package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

/**
 * Packs the pins (pins 1..n, one per group) into the bus on pin 0 with no delay: bit {@code k} of a pin goes to the
 * {@code k}-th lowest bus bit of its group. A Z bit stays Z, and so do the bus bits in no group. Each group is placed
 * by its {@linkplain BitRun runs}, worked out once.
 */
final class BusJoinerBehavior implements Behavior {

    private final int width;
    private final BitRun[][] runsByPin;
    private final long unusedMask;

    BusJoinerBehavior(BitGroups groups) {
        this.width = groups.width();
        this.runsByPin = new BitRun[groups.pinCount()][];
        for (int pin = 0; pin < groups.pinCount(); pin++) {
            runsByPin[pin] = BitRun.of(groups.bitsOf(pin));
        }
        this.unusedMask = groups.unusedBits().stream()
                .mapToLong(bit -> 1L << bit)
                .reduce(0L, (mask, bitMask) -> mask | bitMask);
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
        long highImpedance = unusedMask;
        for (int pin = 0; pin < runsByPin.length; pin++) {
            Value input = context.input(pin + 1);
            // a bit beyond a narrower input reads Z
            long inputHighImpedance = input.highImpedanceMask() | ~Value.maskFor(input.width());
            for (BitRun run : runsByPin[pin]) {
                bits |= run.toBus(input.bits());
                highImpedance |= run.toBus(inputHighImpedance);
            }
        }
        context.drive(0, new Value(width, bits, highImpedance, false), 0);
    }
}
