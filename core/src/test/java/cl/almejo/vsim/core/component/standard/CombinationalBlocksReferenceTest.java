package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.FlatComponent;
import cl.almejo.vsim.core.simulation.FlatNet;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every combinational block against {@link CombinationalReference}: exhaustive over every defined input at small
 * widths, sampled with fixed seeds at 1, 8 and 32 bits with Z, partly Z and conflicts on every input pin, unconnected
 * control inputs at their rest values, and every output exactly one delay after an input. The inputs of one check
 * follow the previous ones in the same harness, so a block that does not react to a change fails too.
 */
class CombinationalBlocksReferenceTest {

    private static final long SEED = 20261018L;

    static Stream<CombinationalReference> smallBlocks() {
        return Stream.of(
                CombinationalReference.adder(4, 1),
                CombinationalReference.adder(1, 1),
                CombinationalReference.alu(4, 1),
                CombinationalReference.alu(1, 1),
                CombinationalReference.comparator(4, false, 1),
                CombinationalReference.comparator(4, true, 1),
                CombinationalReference.comparator(1, true, 1),
                CombinationalReference.mux(2, 4, 1),
                CombinationalReference.mux(4, 2, 1),
                CombinationalReference.decoder(1, 1),
                CombinationalReference.decoder(2, 1),
                CombinationalReference.decoder(3, 1),
                CombinationalReference.decoder(4, 1));
    }

    static Stream<CombinationalReference> blocksOfEveryWidth() {
        List<CombinationalReference> blocks = new ArrayList<>();
        for (int width : new int[]{1, 4, 8, 32}) {
            blocks.add(CombinationalReference.adder(width, 1));
            blocks.add(CombinationalReference.alu(width, 1));
            blocks.add(CombinationalReference.comparator(width, false, 1));
            blocks.add(CombinationalReference.comparator(width, true, 1));
            blocks.add(CombinationalReference.mux(2, width, 1));
            blocks.add(CombinationalReference.mux(4, width, 1));
        }
        for (int inputBits = 1; inputBits <= 4; inputBits++) {
            blocks.add(CombinationalReference.decoder(inputBits, 1));
        }
        return blocks.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("smallBlocks")
    void everyDefinedInputMatchesTheReference(CombinationalReference reference) {
        BlockHarness harness = new BlockHarness(reference.typeId(), reference.parameters());
        int[] widths = inputWidths(harness, reference);
        int totalBits = Arrays.stream(widths).sum();
        assertThat(totalBits).as("small enough to try every input").isLessThanOrEqualTo(12);
        for (long combination = 0; combination < 1L << totalBits; combination++) {
            Stimulus[] stimuli = new Stimulus[widths.length];
            long remaining = combination;
            for (int pin = 0; pin < widths.length; pin++) {
                stimuli[pin] = Stimulus.defined(widths[pin], remaining);
                remaining >>>= widths[pin];
            }
            applyAndCheck(harness, reference, stimuli, 1);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("blocksOfEveryWidth")
    void sampledInputsWithUndefinedValuesOnEveryPinMatchTheReference(CombinationalReference reference) {
        Random random = new Random(SEED + reference.description().hashCode());
        BlockHarness harness = new BlockHarness(reference.typeId(), reference.parameters());
        int[] widths = inputWidths(harness, reference);
        for (int check = 0; check < 1_500; check++) {
            Stimulus[] stimuli = new Stimulus[widths.length];
            for (int pin = 0; pin < widths.length; pin++) {
                stimuli[pin] = Stimulus.sample(widths[pin], random, 12);
            }
            applyAndCheck(harness, reference, stimuli, 1);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("blocksOfEveryWidth")
    void eachKindOfUndefinedValueOnEachPinGivesWhatTheReferenceSays(CombinationalReference reference) {
        Random random = new Random(SEED - reference.description().hashCode());
        BlockHarness harness = new BlockHarness(reference.typeId(), reference.parameters());
        int[] widths = inputWidths(harness, reference);
        for (int undefinedPin = 0; undefinedPin < widths.length; undefinedPin++) {
            int width = widths[undefinedPin];
            List<Stimulus> undefinedKinds = List.of(Stimulus.highImpedance(width),
                    Stimulus.partlyHighImpedance(width, random), Stimulus.conflict(width));
            for (Stimulus undefined : undefinedKinds) {
                for (int base = 0; base < 25; base++) {
                    Stimulus[] stimuli = new Stimulus[widths.length];
                    for (int pin = 0; pin < widths.length; pin++) {
                        stimuli[pin] = pin == undefinedPin ? undefined : Stimulus.definedSample(widths[pin], random);
                    }
                    applyAndCheck(harness, reference, stimuli, 1);
                }
            }
        }
    }

    static Stream<Arguments> controlInputs() {
        return Stream.of(
                Arguments.of(CombinationalReference.adder(4, 1), CombinationalReference.ADDER_CARRY_IN),
                Arguments.of(CombinationalReference.adder(32, 1), CombinationalReference.ADDER_CARRY_IN),
                Arguments.of(CombinationalReference.decoder(2, 1), CombinationalReference.DECODER_ENABLE),
                Arguments.of(CombinationalReference.decoder(4, 1), CombinationalReference.DECODER_ENABLE));
    }

    @ParameterizedTest(name = "{0} pin {1}")
    @MethodSource("controlInputs")
    void anUnconnectedControlInputTakesItsRestValue(CombinationalReference reference, int controlPin) {
        Random random = new Random(SEED + controlPin);
        BlockHarness harness = new BlockHarness(reference.typeId(), reference.parameters(), controlPin);
        int[] widths = inputWidths(harness, reference);
        for (int check = 0; check < 300; check++) {
            Stimulus[] stimuli = new Stimulus[widths.length];
            for (int pin = 0; pin < widths.length; pin++) {
                stimuli[pin] = pin == controlPin ? null : Stimulus.sample(widths[pin], random, 10);
            }
            applyAndCheck(harness, reference, stimuli, 1);
        }
    }

    static Stream<CombinationalReference> slowBlocks() {
        return Stream.of(
                CombinationalReference.adder(8, 5),
                CombinationalReference.alu(8, 5),
                CombinationalReference.comparator(8, true, 5),
                CombinationalReference.mux(4, 8, 5),
                CombinationalReference.decoder(3, 5));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("slowBlocks")
    void everyOutputChangesExactlyTheDelayAfterAnInputAndOnInitialization(CombinationalReference reference) {
        int delay = (int) reference.parameters().get("delay");
        Random random = new Random(SEED * delay);
        BlockHarness harness = new BlockHarness(reference.typeId(), reference.parameters());
        int[] widths = inputWidths(harness, reference);
        Stimulus[] stimuli = new Stimulus[widths.length];
        for (int pin = 0; pin < widths.length; pin++) {
            stimuli[pin] = Stimulus.defined(widths[pin], 0L);
        }
        Value[] expected = reference.outputs().apply(netValues(stimuli, widths));
        harness.advance(delay - 1);
        for (int output = 0; output < expected.length; output++) {
            assertThat(harness.output(reference.inputCount() + output)).as("output %d before the first delay", output)
                    .isEqualTo(Value.highImpedance(expected[output].width()));
        }
        harness.advance(1);
        assertOutputs(harness, reference, expected, "on initialization");

        for (int check = 0; check < 300; check++) {
            Value[] previous = expected;
            Stimulus[] next = new Stimulus[widths.length];
            for (int pin = 0; pin < widths.length; pin++) {
                next[pin] = Stimulus.sample(widths[pin], random, 10);
                next[pin].applyTo(harness, pin);
            }
            expected = reference.outputs().apply(netValues(next, widths));
            harness.advance(delay - 1);
            assertOutputs(harness, reference, previous, "still the old outputs after " + Arrays.toString(next));
            harness.advance(1);
            assertOutputs(harness, reference, expected, "after " + Arrays.toString(next));
        }
    }

    /**
     * Added to a running circuit whose input nets are already settled, a block gets no input change: it must compute
     * from the levels it finds when it starts.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("slowBlocks")
    void aBlockAddedToARunningCircuitComputesFromTheLevelsAlreadyThere(CombinationalReference reference) {
        Random random = new Random(SEED + 7);
        ComponentType type = StandardComponentTypes.registry().require(reference.typeId())
                .specializedFor(reference.parameters());
        for (int trial = 0; trial < 20; trial++) {
            SimulationEngine engine = new SimulationEngine();
            List<FlatComponent> sources = new ArrayList<>();
            List<FlatNet> inputNets = new ArrayList<>();
            Value[] inputs = new Value[reference.inputCount()];
            for (int pin = 0; pin < inputs.length; pin++) {
                int width = type.pin(pin).width();
                Map<String, Object> sourceParameters = Map.of("value", "0", InterfacePins.WIDTH, width);
                sources.add(new FlatComponent("source" + pin, StandardComponentTypes.registry()
                        .require(InterfacePins.INPUT_PIN).specializedFor(sourceParameters), sourceParameters));
                inputNets.add(new FlatNet("net" + pin, List.of(new FlatPinRef("source" + pin, 0)), width, false));
            }
            engine.update(new FlatCircuit(sources, inputNets));
            for (int pin = 0; pin < inputs.length; pin++) {
                inputs[pin] = Stimulus.definedSample(type.pin(pin).width(), random).netValue();
                engine.setInput("source" + pin, inputs[pin]);
            }
            engine.advance(10);

            List<FlatComponent> components = new ArrayList<>(sources);
            components.add(new FlatComponent("dut", type, reference.parameters()));
            List<FlatNet> nets = new ArrayList<>();
            for (int pin = 0; pin < type.pinCount(); pin++) {
                List<FlatPinRef> pins = new ArrayList<>(List.of(new FlatPinRef("dut", pin)));
                if (pin < inputs.length) {
                    pins.add(new FlatPinRef("source" + pin, 0));
                }
                nets.add(new FlatNet("net" + pin, pins, type.pin(pin).width(), false));
            }
            engine.update(new FlatCircuit(components, nets));
            engine.advance(10);
            Value[] expected = reference.outputs().apply(inputs);
            for (int output = 0; output < expected.length; output++) {
                int pin = inputs.length + output;
                assertThat(engine.snapshot().netValue("net" + pin)).as("%s %s inputs %s", reference,
                        type.pin(pin).name(), Arrays.toString(inputs)).isEqualTo(expected[output]);
            }
        }
    }

    @Test
    void theSampledChecksCoverEveryAluOperationWithEachFlagBothWays() {
        Random random = new Random(SEED);
        boolean[][] seen = new boolean[8][8];
        CombinationalReference reference = CombinationalReference.alu(8, 1);
        for (int check = 0; check < 4_000; check++) {
            Value first = Stimulus.definedSample(8, random).netValue();
            Value second = Stimulus.definedSample(8, random).netValue();
            int operation = random.nextInt(8);
            Value[] outputs = reference.outputs().apply(new Value[]{first, second, Value.of(3, operation)});
            for (int flag = 1; flag <= 4; flag++) {
                seen[operation][(flag - 1) * 2 + (int) outputs[flag].bits()] = true;
            }
        }
        // add and subtract set every flag both ways; Z and N go both ways for every operation but A>>1 (logical: its
        // top bit is always 0)
        assertThat(seen[7][3]).as("A>>1 is never negative").isFalse();
        for (int operation = 0; operation < 8; operation++) {
            for (int flag = 0; flag < (operation == 7 ? 1 : 2); flag++) {
                assertThat(seen[operation][flag * 2]).as("op %d flag %d at 0", operation, flag).isTrue();
                assertThat(seen[operation][flag * 2 + 1]).as("op %d flag %d at 1", operation, flag).isTrue();
            }
        }
        for (int operation = 0; operation < 2; operation++) {
            assertThat(seen[operation]).as("op %d", operation).containsOnly(true);
        }
        assertThat(seen[6][5]).as("A<<1 shifts a one out").isTrue();
        assertThat(seen[7][5]).as("A>>1 shifts a one out").isTrue();
    }

    private static void applyAndCheck(BlockHarness harness, CombinationalReference reference, Stimulus[] stimuli,
                                      int delay) {
        int[] widths = inputWidths(harness, reference);
        for (int pin = 0; pin < stimuli.length; pin++) {
            if (stimuli[pin] != null) {
                stimuli[pin].applyTo(harness, pin);
            }
        }
        harness.advance(delay + 1);
        assertOutputs(harness, reference, reference.outputs().apply(netValues(stimuli, widths)),
                "inputs " + Arrays.toString(stimuli));
    }

    /** What the block reads: an unconnected pin (a null stimulus) reads all Z. */
    private static Value[] netValues(Stimulus[] stimuli, int[] widths) {
        Value[] values = new Value[stimuli.length];
        for (int pin = 0; pin < stimuli.length; pin++) {
            values[pin] = stimuli[pin] == null ? Value.highImpedance(widths[pin]) : stimuli[pin].netValue();
        }
        return values;
    }

    private static void assertOutputs(BlockHarness harness, CombinationalReference reference, Value[] expected,
                                      String description) {
        for (int output = 0; output < expected.length; output++) {
            int pin = reference.inputCount() + output;
            assertThat(harness.output(pin)).as("%s, %s, %s", reference, harness.type().pin(pin).name(), description)
                    .isEqualTo(expected[output]);
        }
        assertThat(reference.inputCount() + expected.length).isEqualTo(harness.type().pinCount());
    }

    private static int[] inputWidths(BlockHarness harness, CombinationalReference reference) {
        int[] widths = new int[reference.inputCount()];
        for (int pin = 0; pin < widths.length; pin++) {
            assertThat(harness.type().pin(pin).isInput()).isTrue();
            widths[pin] = harness.type().pin(pin).width();
        }
        return widths;
    }
}
