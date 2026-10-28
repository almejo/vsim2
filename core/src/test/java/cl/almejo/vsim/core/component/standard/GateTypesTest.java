package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.GateEvaluation;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentPreset;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterKind;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The parameterized gates: AND, OR, XOR with «Entradas», «Ancho (bits)» and «Negar salida», and the Buffer. */
class GateTypesTest {

    private static final List<String> OPERATIONS = List.of("and", "or", "xor");

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    @ParameterizedTest
    @ValueSource(strings = {"and", "or", "xor"})
    void aGateHasInputsWidthNegateAndDelayInThatOrder(String typeId) {
        ComponentType gate = registry.require(typeId);

        assertThat(gate.category()).isEqualTo(ComponentCategory.GATES);
        assertThat(gate.parameters()).extracting(ParameterSpec::name)
                .containsExactly("inputs", "width", "negate", "delay");
        assertThat(gate.parameters()).extracting(ParameterSpec::kind).containsExactly(ParameterKind.INTEGER,
                ParameterKind.INTEGER, ParameterKind.BOOLEAN, ParameterKind.INTEGER);
        assertThat(gate.parameters()).extracting(ParameterSpec::defaultValue).containsExactly(2, 1, false, 1);
        ParameterSpec inputs = gate.parameters().getFirst();
        assertThat(List.of(inputs.minimum(), inputs.maximum())).containsExactly(2, 8);
        ParameterSpec width = gate.parameters().get(1);
        assertThat(List.of(width.minimum(), width.maximum())).containsExactly(1, 32);
    }

    @Test
    void theBufferHasWidthNegateAndDelayAndTheOldNotGeometry() {
        ComponentType buffer = registry.require("buffer");

        assertThat(buffer.category()).isEqualTo(ComponentCategory.GATES);
        assertThat(buffer.parameters()).extracting(ParameterSpec::name).containsExactly("width", "negate", "delay");
        assertThat(buffer.size()).isEqualTo(new GridSize(4, 4));
        assertThat(buffer.pins()).extracting(PinSpec::name).containsExactly("in", "out");
        assertThat(buffer.pins()).extracting(PinSpec::position)
                .containsExactly(new GridPoint(0, 2), new GridPoint(4, 2));
        assertThat(buffer.specializedFor(Map.of("width", 8)).pins()).extracting(PinSpec::width)
                .containsExactly(8, 8);
    }

    @ParameterizedTest(name = "{0} inputs: rows {1}, output row {2}, height {3}")
    @CsvSource(delimiter = ';', value = {
            "2; 0 4; 2; 4",
            "3; 0 2 4; 2; 4",
            "4; 0 1 2 3; 2; 4",
            "5; 0 1 2 3 4; 2; 4",
            "6; 0 1 2 4 5 6; 3; 6",
            "7; 0 1 2 3 4 5 6; 3; 6",
            "8; 0 1 2 3 5 6 7 8; 4; 8"})
    void theInputsSitOnTheRowsOfTheTableAndTheOutputIsTheLastPin(int inputs, String rows, int outputRow,
                                                                  int height) {
        List<Integer> expectedRows = Arrays.stream(rows.split(" ")).map(Integer::valueOf).toList();
        assertThat(StandardComponentTypes.gateInputRows(inputs)).isEqualTo(expectedRows);
        assertThat(StandardComponentTypes.gateOutputRow(inputs)).isEqualTo(outputRow);
        for (String typeId : OPERATIONS) {
            ComponentType gate = registry.require(typeId).specializedFor(Map.of("inputs", inputs));
            assertThat(gate.size()).isEqualTo(new GridSize(4, height));
            assertThat(gate.pinCount()).isEqualTo(inputs + 1);
            List<GridPoint> expected = new ArrayList<>();
            expectedRows.forEach(row -> expected.add(new GridPoint(0, row)));
            expected.add(new GridPoint(4, outputRow));
            assertThat(gate.pins()).extracting(PinSpec::position).isEqualTo(expected);
            assertThat(gate.pin(inputs).isInput()).isFalse();
            assertThat(gate.pin(inputs).name()).isEqualTo("out");
        }
    }

    @Test
    void everyPinHasTheGatesWidth() {
        ComponentType gate = registry.require("xor").specializedFor(Map.of("inputs", 5, "width", 8));

        assertThat(gate.pins()).extracting(PinSpec::width).containsOnly(8);
    }

    @Test
    void everyGateFollowsItsTruthTableForEveryInputCount() {
        for (String typeId : OPERATIONS) {
            for (int inputs = 2; inputs <= 8; inputs++) {
                for (boolean negated : new boolean[]{false, true}) {
                    checkTruthTable(typeId, inputs, negated);
                }
            }
        }
    }

    /** Every row of the 1-bit table of {@code typeId} with {@code inputs} inputs against a fold of booleans. */
    private void checkTruthTable(String typeId, int inputs, boolean negated) {
        BinaryOperator<Boolean> fold = switch (typeId) {
            case "and" -> (left, right) -> left && right;
            case "or" -> (left, right) -> left || right;
            default -> (left, right) -> left ^ right;
        };
        for (int row = 0; row < 1 << inputs; row++) {
            List<Value> values = new ArrayList<>();
            boolean expected = (row & 1) == 1;
            for (int input = 0; input < inputs; input++) {
                boolean bit = (row >> input & 1) == 1;
                values.add(Value.of(bit));
                if (input > 0) {
                    expected = fold.apply(expected, bit);
                }
            }
            Value output = evaluate(typeId, Map.of("inputs", inputs, "negate", negated), values);
            assertThat(output).as("%s/%d negated %s, row %d", typeId, inputs, negated, row)
                    .isEqualTo(Value.of(expected != negated));
        }
    }

    @Test
    void xorOfManyInputsIsOddParity() {
        List<Value> threeOnes = List.of(Value.ONE, Value.ONE, Value.ONE, Value.ZERO);

        assertThat(evaluate("xor", Map.of("inputs", 4), threeOnes)).isEqualTo(Value.ONE);
        assertThat(evaluate("xor", Map.of("inputs", 4, "negate", true), threeOnes)).isEqualTo(Value.ZERO);
    }

    @Test
    void wideGatesWorkBitByBit() {
        Map<String, Object> eightBits = Map.of("inputs", 3, "width", 8);
        List<Value> values = List.of(Value.of(8, 0b1111_0000), Value.of(8, 0b1100_1100), Value.of(8, 0b1010_1010));

        assertThat(evaluate("and", eightBits, values)).isEqualTo(Value.of(8, 0b1000_0000));
        assertThat(evaluate("or", eightBits, values)).isEqualTo(Value.of(8, 0b1111_1110));
        assertThat(evaluate("xor", eightBits, values)).isEqualTo(Value.of(8, 0b1001_0110));
        assertThat(evaluate("and", Map.of("inputs", 3, "width", 8, "negate", true), values))
                .isEqualTo(Value.of(8, 0b0111_1111));
    }

    @ParameterizedTest
    @CsvSource({"1,0,false,0", "1,1,false,1", "1,0,true,1", "1,1,true,0", "8,165,false,165", "8,165,true,90"})
    void theBufferCopiesOrInvertsItsInput(int width, long input, boolean negated, long expected) {
        Value output = evaluate("buffer", Map.of("width", width, "negate", negated), List.of(Value.of(width, input)));

        assertThat(output).isEqualTo(Value.of(width, expected));
    }

    @Test
    void aFloatingInputBitIsIgnoredAsBefore() {
        assertThat(evaluate("and", Map.of(), List.of(Value.ONE, Value.HIGH_IMPEDANCE))).isEqualTo(Value.ONE);
        assertThat(evaluate("buffer", Map.of("negate", true), List.of(Value.HIGH_IMPEDANCE)))
                .isEqualTo(Value.HIGH_IMPEDANCE);
    }

    @Test
    void aGateCountsItsInputsBetweenTwoAndEight() {
        assertThatThrownBy(() -> registry.require("and").resolveParameters(Map.of("inputs", 9)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.require("or").resolveParameters(Map.of("inputs", 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theNameFollowsNegateOutput() {
        assertThat(StandardComponentTypes.nameId("and", Map.of("negate", true))).isEqualTo("nand");
        assertThat(StandardComponentTypes.nameId("or", Map.of("negate", true))).isEqualTo("nor");
        assertThat(StandardComponentTypes.nameId("xor", Map.of("negate", true))).isEqualTo("xnor");
        assertThat(StandardComponentTypes.nameId("buffer", Map.of("negate", true))).isEqualTo("not");
        assertThat(StandardComponentTypes.nameId("and", Map.of("negate", false))).isEqualTo("and");
        assertThat(StandardComponentTypes.nameId("buffer", Map.of())).isEqualTo("buffer");
        assertThat(StandardComponentTypes.nameId("led", Map.of("negate", true))).isEqualTo("led");
    }

    @Test
    void thePresetsAreTheNegatedGates() {
        assertThat(StandardComponentTypes.presets()).containsExactly(
                new ComponentPreset("nand", "and", Map.of("negate", true)),
                new ComponentPreset("nor", "or", Map.of("negate", true)),
                new ComponentPreset("xnor", "xor", Map.of("negate", true)),
                new ComponentPreset("not", "buffer", Map.of("negate", true)));
        StandardComponentTypes.presets().forEach(preset -> assertThat(
                StandardComponentTypes.nameId(preset.typeId(), preset.parameters())).isEqualTo(preset.id()));
    }

    @Test
    void aPresetIsFoundByItsIdAndATypeIdIsNotAPreset() {
        assertThat(StandardComponentTypes.preset("xnor"))
                .contains(new ComponentPreset("xnor", "xor", Map.of("negate", true)));
        assertThat(StandardComponentTypes.preset("xor")).isEmpty();
        assertThat(StandardComponentTypes.preset("nand2")).isEmpty();
    }

    /** What the gate drives on its output once with {@code inputs} on its input pins. */
    private Value evaluate(String typeId, Map<String, Object> parameters, List<Value> inputs) {
        return GateEvaluation.output(registry.require(typeId), parameters, inputs);
    }
}
