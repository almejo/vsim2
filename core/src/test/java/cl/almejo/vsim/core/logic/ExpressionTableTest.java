package cl.almejo.vsim.core.logic;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ExpressionTableTest {

    @Test
    void theTableHasOneColumnPerLineAndTheInputsInNaturalOrder() {
        ExpressionTable.Result result = ExpressionTable.of("Sumador", lines("S = A ^ B ^ Cin",
                "Cout = A B + Cin (A ^ B)"));
        assertThat(result.problems()).isEmpty();
        assertThat(result.inputNames()).containsExactly("A", "B", "Cin");
        TruthTableSpec spec = result.spec().orElseThrow();
        assertThat(spec.title()).isEqualTo("Sumador");
        assertThat(spec.inputNames()).containsExactly("A", "B", "Cin");
        assertThat(spec.outputNames()).containsExactly("S", "Cout");
        for (int row = 0; row < 8; row++) {
            int ones = Integer.bitCount(row);
            assertThat(spec.value(0, row)).isEqualTo(ones % 2 == 1 ? LogicValue.ONE : LogicValue.ZERO);
            assertThat(spec.value(1, row)).isEqualTo(ones >= 2 ? LogicValue.ONE : LogicValue.ZERO);
        }
    }

    @Test
    void naturalOrderComparesLettersThenNumbers() {
        ExpressionTable.Result result = ExpressionTable.of("T", lines("F = X10 + X2 + C + X1 + B + A + X"));
        assertThat(result.inputNames()).containsExactly("A", "B", "C", "X", "X1", "X2", "X10");
        assertThat(ExpressionTable.of("T", lines("F = b + a + Cin")).inputNames()).containsExactly("a", "b", "Cin");
    }

    @Test
    void rowsEqualTheDirectEvaluationOfRandomLines() {
        Random random = new Random(42);
        for (int trial = 0; trial < 200; trial++) {
            int inputCount = 1 + random.nextInt(TruthTableSpec.MAXIMUM_INPUTS);
            int outputCount = 1 + random.nextInt(3);
            List<String> names = new ArrayList<>();
            for (int input = 0; input < inputCount; input++) {
                names.add("I" + input);
            }
            List<ExpressionLine> lines = new ArrayList<>();
            for (int output = 0; output < outputCount; output++) {
                lines.add(new ExpressionLine("O" + output, RandomExpressions.of(random, names, 4)));
            }
            ExpressionTable.Result result = ExpressionTable.of("T", lines);
            if (result.spec().isEmpty()) {
                // No variable left in any line: only the NO_INPUTS problem is allowed.
                assertThat(result.problems()).extracting(ExpressionProblem::messageKey)
                        .containsExactly(ExpressionProblem.NO_INPUTS);
                continue;
            }
            TruthTableSpec spec = result.spec().get();
            for (int row = 0; row < spec.rowCount(); row++) {
                Map<String, Boolean> values = new HashMap<>();
                for (int input = 0; input < spec.inputCount(); input++) {
                    values.put(spec.inputNames().get(input), spec.inputValue(row, input));
                }
                for (int output = 0; output < outputCount; output++) {
                    boolean expected = lines.get(output).expression().evaluate(values);
                    assertThat(spec.value(output, row)).isEqualTo(expected ? LogicValue.ONE : LogicValue.ZERO);
                }
            }
        }
    }

    @Test
    void aRepeatedOutputIsAProblemIgnoringCase() {
        ExpressionTable.Result result = ExpressionTable.of("T", lines("F = A", "f = B"));
        assertThat(result.spec()).isEmpty();
        assertThat(result.problems()).containsExactly(
                new ExpressionProblem(ExpressionProblem.REPEATED_OUTPUT, List.of("f"), -1));
    }

    @Test
    void anOutputUsedAsAnInputIsAProblem() {
        ExpressionTable.Result result = ExpressionTable.of("T", lines("F = A", "G = f + B"));
        assertThat(result.problems()).containsExactly(
                new ExpressionProblem(ExpressionProblem.OUTPUT_USED_AS_INPUT, List.of("f"), -1));
        assertThat(result.spec()).isEmpty();
    }

    @Test
    void inputsThatDifferOnlyInCaseAreAProblem() {
        ExpressionTable.Result result = ExpressionTable.of("T", lines("F = a + A"));
        assertThat(result.problems()).containsExactly(
                new ExpressionProblem(ExpressionProblem.INPUTS_DIFFER_ONLY_IN_CASE, List.of("A", "a"), -1));
    }

    @Test
    void theCountsAreLimited() {
        assertThat(keys(ExpressionTable.of("T", lines("F = 1")))).containsExactly(ExpressionProblem.NO_INPUTS);
        assertThat(keys(ExpressionTable.of("T", List.of()))).contains(ExpressionProblem.NO_OUTPUTS);
        ExpressionTable.Result nine = ExpressionTable.of("T", lines("F = A B C D E G H I J"));
        assertThat(nine.problems()).containsExactly(
                new ExpressionProblem(ExpressionProblem.TOO_MANY_INPUTS, List.of(9, 8), -1));
        assertThat(nine.inputNames()).hasSize(9);
        List<String> texts = new ArrayList<>();
        for (int output = 0; output < 9; output++) {
            texts.add("O" + output + " = A");
        }
        assertThat(ExpressionTable.of("T", lines(texts.toArray(String[]::new))).problems()).containsExactly(
                new ExpressionProblem(ExpressionProblem.TOO_MANY_OUTPUTS, List.of(9, 8), -1));
    }

    @Test
    void constantLinesNextToOthersAreFine() {
        TruthTableSpec spec = ExpressionTable.of("T", lines("F = 1", "G = A")).spec().orElseThrow();
        assertThat(spec.inputNames()).containsExactly("A");
        assertThat(spec.outputs().get(0)).containsExactly(LogicValue.ONE, LogicValue.ONE);
        assertThat(spec.outputs().get(1)).containsExactly(LogicValue.ZERO, LogicValue.ONE);
    }

    @Test
    void everyKeyIsListed() {
        assertThat(ExpressionProblem.MESSAGE_KEYS).doesNotHaveDuplicates().hasSize(16)
                .allSatisfy(key -> assertThat(key).startsWith("expressions.problem."));
    }

    private static List<String> keys(ExpressionTable.Result result) {
        return result.problems().stream().map(ExpressionProblem::messageKey).toList();
    }

    private static List<ExpressionLine> lines(String... texts) {
        List<ExpressionLine> lines = new ArrayList<>();
        for (String text : texts) {
            lines.add(ExpressionParser.parseLine(text).line().orElseThrow());
        }
        return lines;
    }
}
