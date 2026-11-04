package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

/**
 * Copies the bits of each group of the bus (pin 0) to its own pin (the first group on pin 1) with no delay; pin bit
 * {@code k} is the {@code k}-th lowest bus bit of its group and bits in no group are ignored. A Z bit stays Z; a bus on
 * a width mismatch reads Z (the engine gives such pins Z), so every bit is Z. Each group is copied by its
 * {@linkplain BitRun runs}, worked out once.
 */
final class BusSplitterBehavior implements Behavior {

    private final BitRun[][] runsByPin;
    private final int[] pinWidths;

    BusSplitterBehavior(BitGroups groups) {
        this.runsByPin = new BitRun[groups.pinCount()][];
        this.pinWidths = new int[groups.pinCount()];
        for (int pin = 0; pin < groups.pinCount(); pin++) {
            runsByPin[pin] = BitRun.of(groups.bitsOf(pin));
            pinWidths[pin] = groups.widthOf(pin);
        }
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
        // a bit beyond a narrower bus reads Z
        long highImpedance = bus.highImpedanceMask() | ~Value.maskFor(bus.width());
        for (int pin = 0; pin < runsByPin.length; pin++) {
            long pinBits = 0L;
            long pinHighImpedance = 0L;
            for (BitRun run : runsByPin[pin]) {
                pinBits |= run.toPin(bus.bits());
                pinHighImpedance |= run.toPin(highImpedance);
            }
            context.drive(pin + 1, new Value(pinWidths[pin], pinBits, pinHighImpedance, false), 0);
        }
    }
}
