package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.BitState;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The grouped splitter and joiner against a bit-by-bit reference written here, on random groups (skipped bits, ranges
 * out of order, unused bits) and random values with Z bits, including buses and pins of the wrong width.
 */
class GroupedBusRandomTest {

    private static final int ROUNDS = 2_000;
    private static final long SEED = 20261103L;

    private final Random random = new Random(SEED);

    /** A fake context: fixed inputs, and what the part drives. */
    private static final class Context implements BehaviorContext {

        private final Map<Integer, Value> inputs;
        private final Map<Integer, Value> driven = new HashMap<>();

        Context(Map<Integer, Value> inputs) {
            this.inputs = inputs;
        }

        @Override
        public Value input(int pinIndex) {
            return inputs.get(pinIndex);
        }

        @Override
        public void drive(int pinIndex, Value value, long delay) {
            driven.put(pinIndex, value);
        }

        @Override
        public void schedule(long delay, Runnable action) {
            throw new UnsupportedOperationException("the bus parts have no delay");
        }

        @Override
        public long now() {
            return 0;
        }
    }

    @Test
    void theSplitterCopiesEveryBitOfEveryGroupLikeTheReference() {
        for (int round = 0; round < ROUNDS; round++) {
            int width = 2 + random.nextInt(31);
            BitGroups groups = randomGroups(width);
            Value bus = randomValue(random.nextInt(8) == 0 ? 1 + random.nextInt(32) : width);
            Context context = new Context(Map.of(0, bus));

            new BusSplitterBehavior(groups).initialize(context);

            for (int pin = 0; pin < groups.pinCount(); pin++) {
                assertThat(context.driven.get(pin + 1)).as("%s pin %d of %s", groups, pin, bus)
                        .isEqualTo(referenceSplit(bus, groups.bitsOf(pin)));
            }
        }
    }

    @Test
    void theJoinerPacksEveryGroupLikeTheReference() {
        for (int round = 0; round < ROUNDS; round++) {
            int width = 2 + random.nextInt(31);
            BitGroups groups = randomGroups(width);
            Map<Integer, Value> inputs = new HashMap<>();
            for (int pin = 0; pin < groups.pinCount(); pin++) {
                int pinWidth = random.nextInt(8) == 0 ? 1 + random.nextInt(32) : groups.widthOf(pin);
                inputs.put(pin + 1, randomValue(pinWidth));
            }
            Context context = new Context(inputs);

            new BusJoinerBehavior(groups).initialize(context);

            assertThat(context.driven.get(0)).as("%s from %s", groups, inputs)
                    .isEqualTo(referenceJoin(groups, inputs));
        }
    }

    /** Some of the bus bits, shuffled into one to four pins. */
    private BitGroups randomGroups(int width) {
        List<Integer> bits = new ArrayList<>();
        for (int bit = 0; bit < width; bit++) {
            if (random.nextInt(5) > 0) {
                bits.add(bit);
            }
        }
        if (bits.isEmpty()) {
            bits.add(random.nextInt(width));
        }
        Collections.shuffle(bits, random);
        int pinCount = 1 + random.nextInt(Math.min(4, bits.size()));
        List<List<Integer>> pins = new ArrayList<>();
        for (int pin = 0; pin < pinCount; pin++) {
            pins.add(new ArrayList<>());
        }
        for (int index = 0; index < bits.size(); index++) {
            pins.get(index < pinCount ? index : random.nextInt(pinCount)).add(bits.get(index));
        }
        List<String> texts = pins.stream().map(pin -> BitGroups.label(pin.stream().sorted().toList())).toList();
        return BitGroups.parse(String.join("|", texts), width);
    }

    private Value randomValue(int width) {
        long highImpedance = random.nextInt(3) == 0 ? random.nextLong() : 0L;
        return new Value(width, random.nextLong(), highImpedance, false);
    }

    private static Value referenceSplit(Value bus, List<Integer> busBits) {
        long bits = 0L;
        long highImpedance = 0L;
        for (int pinBit = 0; pinBit < busBits.size(); pinBit++) {
            int busBit = busBits.get(pinBit);
            BitState state = busBit < bus.width() ? bus.bit(busBit) : BitState.HIGH_IMPEDANCE;
            if (state == BitState.ONE) {
                bits |= 1L << pinBit;
            } else if (state == BitState.HIGH_IMPEDANCE) {
                highImpedance |= 1L << pinBit;
            }
        }
        return new Value(busBits.size(), bits, highImpedance, false);
    }

    private static Value referenceJoin(BitGroups groups, Map<Integer, Value> inputs) {
        long bits = 0L;
        long highImpedance = 0L;
        for (int bit : groups.unusedBits()) {
            highImpedance |= 1L << bit;
        }
        for (int pin = 0; pin < groups.pinCount(); pin++) {
            Value input = inputs.get(pin + 1);
            List<Integer> busBits = groups.bitsOf(pin);
            for (int pinBit = 0; pinBit < busBits.size(); pinBit++) {
                BitState state = pinBit < input.width() ? input.bit(pinBit) : BitState.HIGH_IMPEDANCE;
                if (state == BitState.ONE) {
                    bits |= 1L << busBits.get(pinBit);
                } else if (state == BitState.HIGH_IMPEDANCE) {
                    highImpedance |= 1L << busBits.get(pinBit);
                }
            }
        }
        return new Value(groups.width(), bits, highImpedance, false);
    }
}
