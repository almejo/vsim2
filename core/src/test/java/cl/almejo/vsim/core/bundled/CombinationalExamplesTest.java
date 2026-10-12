package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.examples.CircuitBuilder;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** The combinational examples are wired and explained cleanly and compute their function for every input. */
class CombinationalExamplesTest {

    @Test
    void everyCombinationalExampleIsWiredAndExplainedCleanly() {
        ExampleChecks.assertExample(CombinationalExamples.fullAdder());
        ExampleChecks.assertCleanBody(CombinationalExamples.fullAdderBodyBuilder());
        ExampleChecks.assertExample(CombinationalExamples.adderFourBits());
        ExampleChecks.assertExample(CombinationalExamples.multiplexer());
        ExampleChecks.assertExample(CombinationalExamples.decoder());
        ExampleChecks.assertExample(CombinationalExamples.parity());
        ExampleChecks.assertExample(CombinationalExamples.multiplier());
    }

    @Test
    void theFullAdderComponentTakesTheCarryOnTopAndGivesTheSumAboveTheCarry() {
        DefinitionBundle bundle = CombinationalExamples.fullAdderDefinition();
        ComponentDefinition definition = bundle.root();
        assertThat(bundle.dependencies()).isEmpty();
        assertThat(definition.id()).isEqualTo(CombinationalExamples.FULL_ADDER_ID);
        assertThat(definition.name()).isEqualTo("Sumador completo");
        assertThat(definition.pinout()).extracting(pin -> pin.spec().name())
                .containsExactly("Cin", "A", "B", "S", "Cout");
        assertThat(definition.pinout()).extracting(pin -> pin.spec().position().y())
                .containsExactly(2, 4, 6, 2, 4);
    }

    @Test
    void theFullAdderAddsThreeBits() {
        checkEveryCombination(CombinationalExamples::fullAdder, List.of("A", "B", "Cin"),
                CombinationalExamplesTest::fullAdderOutputs);
    }

    @Test
    void theFourBitAdderAddsEveryPairOfNumbersWithTheCarryAndShowsThemOnTheDisplays() {
        CircuitBuilder builder = CombinationalExamples.adderFourBits();
        assertThat(builder.document().definitions()).extracting(ComponentDefinition::id)
                .containsExactly(CombinationalExamples.FULL_ADDER_ID);
        assertThat(builder.document().components()).filteredOn(component -> component.typeId()
                .equals(SubcircuitTypes.typeIdOf(CombinationalExamples.FULL_ADDER_ID))).hasSize(4);
        forEveryCombination(() -> builder, List.of("A3", "A2", "A1", "A0", "B3", "B2", "B1", "B0", "Cin"),
                (inputs, simulation) -> {
                    int numberA = number(inputs, "A");
                    int numberB = number(inputs, "B");
                    int sum = numberA + numberB + inputs.get("Cin");
                    assertThat(simulation.led("Cout")).as("%s Cout", inputs).isEqualTo(Value.of(sum > 15));
                    assertThat(simulation.display(0)).as("%s A", inputs).isEqualTo(Value.of(4, numberA));
                    assertThat(simulation.display(1)).as("%s B", inputs).isEqualTo(Value.of(4, numberB));
                    assertThat(simulation.display(2)).as("%s A + B", inputs).isEqualTo(Value.of(4, sum & 15));
                });
    }

    @Test
    void theMultiplexerCopiesTheSelectedInput() {
        checkEveryCombination(CombinationalExamples::multiplexer, List.of("D0", "D1", "D2", "D3", "S1", "S0"),
                CombinationalExamplesTest::multiplexerOutputs);
    }

    @Test
    void theDecoderLightsOnlyTheOutputOfTheNumber() {
        checkEveryCombination(CombinationalExamples::decoder, List.of("A1", "A0"),
                CombinationalExamplesTest::decoderOutputs);
    }

    @Test
    void theParityTellsAnOddFromAnEvenNumberOfOnes() {
        checkEveryCombination(CombinationalExamples::parity, List.of("D2", "D1", "D0"),
                CombinationalExamplesTest::parityOutputs);
    }

    @Test
    void theMultiplierMultipliesTwoTwoBitNumbersOnTheLedsAndTheDisplay() {
        checkEveryCombination(CombinationalExamples::multiplier, List.of("A1", "A0", "B1", "B0"),
                CombinationalExamplesTest::multiplierOutputs);
        forEveryCombination(CombinationalExamples::multiplier, List.of("A1", "A0", "B1", "B0"),
                (inputs, simulation) -> assertThat(simulation.display(0)).as("%s", inputs)
                        .isEqualTo(Value.of(4, (inputs.get("A1") * 2 + inputs.get("A0"))
                                * (inputs.get("B1") * 2 + inputs.get("B0")))));
    }

    // The expected LEDs of each combinational example, by label, from the switches by label (also used by the
    // truth-table checks of BundledCircuitsTest).

    static Map<String, Integer> fullAdderOutputs(Map<String, Integer> inputs) {
        int sum = inputs.get("A") + inputs.get("B") + inputs.get("Cin");
        return outputs("S", sum & 1, "Cout", sum >> 1);
    }

    static Map<String, Integer> fourBitAdderOutputs(Map<String, Integer> inputs) {
        return outputs("Cout", (number(inputs, "A") + number(inputs, "B") + inputs.get("Cin")) >> 4);
    }

    static Map<String, Integer> multiplexerOutputs(Map<String, Integer> inputs) {
        return outputs("Y", inputs.get("D" + (inputs.get("S1") * 2 + inputs.get("S0"))));
    }

    static Map<String, Integer> decoderOutputs(Map<String, Integer> inputs) {
        int number = inputs.get("A1") * 2 + inputs.get("A0");
        Map<String, Integer> expected = new LinkedHashMap<>();
        for (int output = 0; output < 4; output++) {
            expected.put("Y" + output, output == number ? 1 : 0);
        }
        return expected;
    }

    static Map<String, Integer> parityOutputs(Map<String, Integer> inputs) {
        int odd = inputs.get("D2") ^ inputs.get("D1") ^ inputs.get("D0");
        return outputs("Impar", odd, "Par", 1 - odd);
    }

    static Map<String, Integer> multiplierOutputs(Map<String, Integer> inputs) {
        int product = (inputs.get("A1") * 2 + inputs.get("A0")) * (inputs.get("B1") * 2 + inputs.get("B0"));
        Map<String, Integer> expected = new LinkedHashMap<>();
        for (int bit = 0; bit < 4; bit++) {
            expected.put("P" + bit, product >> bit & 1);
        }
        return expected;
    }

    /** Every combination of the switches {@code inputs}: the LEDs are what {@code function} says. */
    static void checkEveryCombination(Supplier<CircuitBuilder> example, List<String> inputs,
                                      Function<Map<String, Integer>, Map<String, Integer>> function) {
        forEveryCombination(example, inputs, (values, simulation) -> {
            for (Map.Entry<String, Integer> output : function.apply(values).entrySet()) {
                assertThat(simulation.led(output.getKey())).as("%s → %s", values, output.getKey())
                        .isEqualTo(Value.of(output.getValue() == 1));
            }
        });
    }

    /**
     * Every combination of the switches {@code inputs} (first = most significant), each on a fresh engine run until
     * nothing is pending; {@code check} gets the switch values by label and the settled simulation.
     */
    static void forEveryCombination(Supplier<CircuitBuilder> example, List<String> inputs,
                                    BiConsumer<Map<String, Integer>, ExampleSimulation> check) {
        for (int row = 0; row < 1 << inputs.size(); row++) {
            Map<String, Integer> values = new LinkedHashMap<>();
            Map<String, String> switches = new HashMap<>();
            for (int index = 0; index < inputs.size(); index++) {
                int bit = row >> (inputs.size() - 1 - index) & 1;
                values.put(inputs.get(index), bit);
                switches.put(inputs.get(index), String.valueOf(bit));
            }
            check.accept(values, new ExampleSimulation(example.get(), switches).settle());
        }
    }

    static Map<String, Integer> outputs(Object... namesAndValues) {
        Map<String, Integer> outputs = new LinkedHashMap<>();
        for (int index = 0; index < namesAndValues.length; index += 2) {
            outputs.put((String) namesAndValues[index], (Integer) namesAndValues[index + 1]);
        }
        return outputs;
    }

    /** The 4-bit number of the inputs {@code prefix}0..{@code prefix}3. */
    static int number(Map<String, Integer> inputs, String prefix) {
        int number = 0;
        for (int bit = 0; bit < 4; bit++) {
            number |= inputs.get(prefix + bit) << bit;
        }
        return number;
    }
}
