package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The register and the counter against a reference model written from M7 spec §2.1, driven by long seeded random
 * sequences: one input changes per step (D defined, Z, partly Z or in conflict; EN, CLR, LD and CLK at 0, 1, Z or in
 * conflict), now and then «Reiniciar». After every step Q (and C) must keep their old value until exactly the delay
 * has passed and then show the model's.
 */
class SequentialBlocksReferenceTest {

    private static final int STEPS = 2_500;

    /** The pins of one block, in the block's pin order; LOAD and CARRY are -1 on the register. */
    private record Pins(int data, int load, int enable, int clear, int clock, int output, int carry) {
    }

    private static final Pins REGISTER = new Pins(0, -1, 1, 2, 3, 4, -1);
    private static final Pins COUNTER = new Pins(0, 1, 2, 3, 4, 5, 6);

    /** The model: the stored value, the clock it last saw, and the rules of the spec. */
    private static final class Model {

        private final boolean counter;
        private final int width;
        private final Value[] inputs;
        private Value stored;
        private Value previousClock;

        Model(boolean counter, int width, Value[] inputs) {
            this.counter = counter;
            this.width = width;
            this.inputs = inputs;
            restart();
        }

        /** Power-on and «Reiniciar»: Q = 0, and the clock as it is now (the level it starts at is not an edge). */
        void restart() {
            stored = Value.of(width, 0L);
            previousClock = inputs[pins().clock()];
            Boolean clear = CombinationalReference.control(inputs[pins().clear()], false);
            if (Boolean.TRUE.equals(clear)) {
                stored = Value.of(width, 0L);
            }
        }

        Pins pins() {
            return counter ? COUNTER : REGISTER;
        }

        void change(int pin, Value value) {
            inputs[pin] = value;
            boolean fallingEdge = false;
            if (pin == pins().clock()) {
                fallingEdge = previousClock.equals(Value.ONE) && value.equals(Value.ZERO);
                previousClock = value;
            }
            Boolean clear = CombinationalReference.control(inputs[pins().clear()], false);
            if (clear == null) {
                return;
            }
            if (clear) {
                stored = Value.of(width, 0L);
                return;
            }
            if (!fallingEdge) {
                return;
            }
            Boolean enable = CombinationalReference.control(inputs[pins().enable()], true);
            if (counter) {
                Boolean load = CombinationalReference.control(inputs[pins().load()], false);
                if (load == null) {
                    return;
                }
                if (load) {
                    stored = dataOrZ();
                } else if (Boolean.TRUE.equals(enable) && CombinationalReference.defined(stored)) {
                    stored = Value.of(width, (stored.bits() + 1) & Value.maskFor(width));
                }
            } else if (Boolean.TRUE.equals(enable)) {
                stored = dataOrZ();
            }
        }

        private Value dataOrZ() {
            Value data = inputs[pins().data()];
            return CombinationalReference.defined(data) ? data : Value.highImpedance(width);
        }

        /** C: 1 while Q is all ones and EN = 1; Z when EN or Q is undefined. */
        Value carry() {
            Boolean enable = CombinationalReference.control(inputs[pins().enable()], true);
            if (enable == null || !CombinationalReference.defined(stored)) {
                return Value.HIGH_IMPEDANCE;
            }
            return Value.of(enable && stored.bits() == Value.maskFor(width));
        }
    }

    static Stream<Arguments> blocks() {
        List<Arguments> arguments = new ArrayList<>();
        for (boolean counter : new boolean[]{false, true}) {
            for (int width : new int[]{1, 2, 4, 8, 32}) {
                for (int delay : new int[]{1, 3}) {
                    arguments.add(Arguments.of(counter ? "counter" : "register", width, delay));
                }
            }
        }
        return arguments.stream();
    }

    @ParameterizedTest(name = "{0} width {1} delay {2}")
    @MethodSource("blocks")
    void randomSequencesFollowTheReferenceModel(String block, int width, int delay) {
        boolean counter = block.equals("counter");
        Pins pins = counter ? COUNTER : REGISTER;
        Random random = new Random(31L * width + delay + (counter ? 1_000 : 0));
        BlockHarness harness = new BlockHarness(counter ? StandardComponentTypes.COUNTER
                : StandardComponentTypes.REGISTER, Map.of(StandardComponentTypes.WIDTH, width, "delay", delay));
        int inputCount = pins.output();
        Value[] inputs = new Value[inputCount];
        for (int pin = 0; pin < inputCount; pin++) {
            inputs[pin] = Value.of(harness.type().pin(pin).width(), 0L); // the harness starts every source at 0
        }
        Model model = new Model(counter, width, inputs);
        harness.advance(delay);
        assertMatches(harness, model, "at power-on");

        int nonZeroCounts = 0;
        int carries = 0;
        int undefinedCounts = 0;
        for (int step = 0; step < STEPS; step++) {
            String description;
            Value previousOutput = model.stored;
            Value previousCarry = counter ? model.carry() : null;
            if (random.nextInt(400) == 0) {
                harness.restart();
                model.restart();
                description = "Reiniciar";
                harness.advance(delay);
                assertMatches(harness, model, "step " + step + " after " + description);
                continue;
            }
            int pin = choosePin(random, pins);
            Stimulus stimulus = pin == pins.data() ? dataStimulus(random, width)
                    : controlStimulus(random, pin == pins.clock(), pin == pins.clear() ? 5 : 2);
            stimulus.applyTo(harness, pin);
            model.change(pin, stimulus.netValue());
            description = harness.type().pin(pin).name() + " = " + stimulus;
            if (delay > 1) {
                harness.advance(delay - 1);
                assertThat(harness.output(pins.output())).as("step %d, %s: Q before the delay", step, description)
                        .isEqualTo(previousOutput);
                if (counter) {
                    assertThat(harness.output(pins.carry())).as("step %d, %s: C before the delay", step, description)
                            .isEqualTo(previousCarry);
                }
                harness.advance(1);
            } else {
                harness.advance(1);
            }
            assertMatches(harness, model, "step " + step + " after " + description);
            if (CombinationalReference.defined(model.stored) && model.stored.bits() != 0L) {
                nonZeroCounts++;
            }
            if (!CombinationalReference.defined(model.stored)) {
                undefinedCounts++;
            }
            if (counter && model.carry().equals(Value.ONE)) {
                carries++;
            }
        }
        // the sequences reach the interesting states, not only 0
        assertThat(nonZeroCounts).isGreaterThan(STEPS / 10);
        assertThat(undefinedCounts).isGreaterThan(0);
        if (counter) {
            assertThat(carries).isGreaterThan(0);
        }
    }

    /** Like any data input, plus all ones or one less often, so a loaded counter soon reaches its end (C = 1). */
    private static Stimulus dataStimulus(Random random, int width) {
        if (random.nextInt(5) == 0) {
            return Stimulus.defined(width, Value.maskFor(width) - random.nextInt(2));
        }
        return Stimulus.sample(width, random, 12);
    }

    /** The clock changes most often, so edges happen; D and the controls the rest of the time. */
    private static int choosePin(Random random, Pins pins) {
        int choice = random.nextInt(10);
        if (choice < 4) {
            return pins.clock();
        }
        if (choice < 6) {
            return pins.data();
        }
        if (choice < 7) {
            return pins.clear();
        }
        if (pins.load() >= 0 && choice < 8) {
            return pins.load();
        }
        return pins.enable();
    }

    /**
     * Mostly 0 or 1 (1 one time in {@code oneInEvery}); a Z or a conflict one time in ten (one in twenty for the
     * clock).
     */
    private static Stimulus controlStimulus(Random random, boolean clock, int oneInEvery) {
        int choice = random.nextInt(clock ? 40 : 20);
        return switch (choice) {
            case 0 -> Stimulus.highImpedance(1);
            case 1 -> Stimulus.conflict(1);
            default -> Stimulus.defined(1, random.nextInt(oneInEvery) == 0 ? 1 : 0);
        };
    }

    private static void assertMatches(BlockHarness harness, Model model, String description) {
        Pins pins = model.pins();
        assertThat(harness.output(pins.output())).as("%s: Q", description).isEqualTo(model.stored);
        assertThat(harness.state()).as("%s: the shown value", description)
                .isEqualTo(new VisibleState.StoredValueState(model.stored));
        if (model.counter) {
            assertThat(harness.output(pins.carry())).as("%s: C", description).isEqualTo(model.carry());
        }
    }
}
