package cl.almejo.vsim.core.logic;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class TermTextTest {

    private static final List<String> NAMES = List.of("A", "B", "C", "D");

    @Test
    void aProductWritesTheLiteralsInInputOrderWithAQuoteForNegation() {
        assertThat(TermText.product(new Term(0b0110, 0b0010), NAMES)).isEqualTo("B'C");
        assertThat(TermText.product(new Term(0b0101, 0b0101), NAMES)).isEqualTo("BD");
        assertThat(TermText.product(new Term(0b1000, 0b0000), NAMES)).isEqualTo("A'");
        assertThat(TermText.product(new Term(0b1111, 0b1001), NAMES)).isEqualTo("AB'C'D");
    }

    @Test
    void theProductOfNothingIsOne() {
        assertThat(TermText.product(new Term(0, 0), NAMES)).isEqualTo("1");
    }

    @Test
    void longerNamesAreSeparatedByADotSoTheyStayReadable() {
        List<String> names = List.of("Cin", "A", "B");

        assertThat(TermText.product(new Term(0b111, 0b101), names)).isEqualTo("Cin·A'·B");
        assertThat(TermText.product(new Term(0b011, 0b001), names)).isEqualTo("A'B");
    }

    @Test
    void aSumComplementsTheGroupsValues() {
        // A group of 0s where B = 0 and C = 1 is the sum B + C'.
        assertThat(TermText.sum(new Term(0b0110, 0b0010), NAMES)).isEqualTo("(B + C')");
        assertThat(TermText.sum(new Term(0b1000, 0b1000), NAMES)).isEqualTo("A'");
        assertThat(TermText.sum(new Term(0, 0), NAMES)).isEqualTo("0");
    }

    @Test
    void aSumOfProductsJoinsTheTermsWithPlus() {
        assertThat(TermText.expression("F", Minimization.Form.SUM_OF_PRODUCTS,
                List.of(new Term(0b0110, 0b0010), new Term(0b0101, 0b0101)), NAMES))
                .isEqualTo("F = B'C + BD");
    }

    @Test
    void aProductOfSumsWritesTheSumsSideBySide() {
        assertThat(TermText.expression("F", Minimization.Form.PRODUCT_OF_SUMS,
                List.of(new Term(0b1100, 0b0000), new Term(0b0011, 0b0001)), NAMES))
                .isEqualTo("F = (A + B)(C + D')");
        assertThat(TermText.expression("F", Minimization.Form.PRODUCT_OF_SUMS,
                List.of(new Term(0b1100, 0b0100)), NAMES))
                .isEqualTo("F = A + B'");
    }

    @Test
    void constantOutputsFollowTheMinimizationConventions() {
        assertThat(TermText.expression("F", Minimization.Form.SUM_OF_PRODUCTS, List.of(), NAMES))
                .isEqualTo("F = 0");
        assertThat(TermText.expression("F", Minimization.Form.SUM_OF_PRODUCTS, List.of(new Term(0, 0)), NAMES))
                .isEqualTo("F = 1");
        assertThat(TermText.expression("F", Minimization.Form.PRODUCT_OF_SUMS, List.of(), NAMES))
                .isEqualTo("F = 1");
        assertThat(TermText.expression("F", Minimization.Form.PRODUCT_OF_SUMS, List.of(new Term(0, 0)), NAMES))
                .isEqualTo("F = 0");
    }

    @Test
    void anOutputOfAMinimizationUsesItsNamesAndForm() {
        TruthTableSpec spec = TruthTableSpec.zeros("BCD", List.of("A", "B", "C", "D"), List.of("P", "M"));
        Minimization minimization = new Minimization(spec, Minimization.Form.SUM_OF_PRODUCTS, List.of(
                new Minimization.OutputExpression(0, List.of(new Term(0b0110, 0b0010), new Term(0b0101, 0b0101))),
                new Minimization.OutputExpression(1, List.of(new Term(0b1000, 0b1000), new Term(0b0101, 0b0101),
                        new Term(0b0110, 0b0110)))));

        assertThat(TermText.expression(minimization, 0)).isEqualTo("P = B'C + BD");
        assertThat(TermText.expression(minimization, 1)).isEqualTo("M = A + BD + BC");
    }

    @Test
    void aProductOfSumsSeparatesABareLiteralFromALongerNameWithADot() {
        List<String> names = List.of("Cin", "A", "B");
        // (Cin)·(A'): without the dot it would read «CinA'».
        assertThat(TermText.expression("F", Minimization.Form.PRODUCT_OF_SUMS,
                List.of(new Term(0b100, 0b000), new Term(0b010, 0b010)), names)).isEqualTo("F = Cin·A'");
        assertThat(TermText.expression("F", Minimization.Form.PRODUCT_OF_SUMS,
                List.of(new Term(0b110, 0b000), new Term(0b001, 0b001)), names)).isEqualTo("F = (Cin + A)·B'");
        assertThat(TermText.expression("F", Minimization.Form.PRODUCT_OF_SUMS,
                List.of(new Term(0b110, 0b000), new Term(0b011, 0b001)), names))
                .as("two parenthesized sums need no dot").isEqualTo("F = (Cin + A)(A + B')");
        assertThat(TermText.expression("F", Minimization.Form.PRODUCT_OF_SUMS,
                List.of(new Term(0b1000, 0b0000), new Term(0b0011, 0b0001)), NAMES))
                .as("one-letter names keep the juxtaposition").isEqualTo("F = A(C + D')");
    }

    @Test
    void typedForTheExpressionsDialogAdjacentLiteralsAreSeparatedUnlessAQuoteEndsTheLeftOne() {
        assertThat(TermText.expression("F", Minimization.Form.SUM_OF_PRODUCTS,
                List.of(new Term(0b1100, 0b1100), new Term(0b0010, 0b0010)), NAMES, true))
                .isEqualTo("F = A·B + C");
        assertThat(TermText.expression("F", Minimization.Form.SUM_OF_PRODUCTS,
                List.of(new Term(0b1100, 0b0100), new Term(0b1111, 0b1001)), NAMES, true))
                .isEqualTo("F = A'B + A·B'C'D");
        assertThat(TermText.expression("F", Minimization.Form.PRODUCT_OF_SUMS,
                List.of(new Term(0b0100, 0b0000), new Term(0b1000, 0b0000)), NAMES, true))
                .as("two bare sums would run into one name").isEqualTo("F = B·A");
        assertThat(TermText.expression("F", Minimization.Form.PRODUCT_OF_SUMS,
                List.of(new Term(0b0100, 0b0100), new Term(0b1000, 0b0000), new Term(0b0011, 0b0001)), NAMES,
                true)).isEqualTo("F = B'A(C + D')");
        assertThat(TermText.expression("F", Minimization.Form.SUM_OF_PRODUCTS,
                List.of(new Term(0b1100, 0b1100)), NAMES, false))
                .as("the wizard keeps its juxtaposition").isEqualTo("F = AB");
    }

    @Test
    void aTypedExpressionReadsBackAsTheSameFunctionForRandomTables() {
        List<String> pool = List.of("A", "B", "C", "D", "Cin", "x1", "S_0", "y", "Q", "en");
        Random random = new Random(20261019L);
        for (int round = 0; round < 400; round++) {
            int inputs = 1 + random.nextInt(6);
            List<String> names = new ArrayList<>(pool);
            Collections.shuffle(names, random);
            names = names.subList(0, inputs);
            List<LogicValue> values = new ArrayList<>();
            for (int row = 0; row < 1 << inputs; row++) {
                double draw = random.nextDouble();
                values.add(draw < 0.15 ? LogicValue.DONT_CARE
                        : random.nextBoolean() ? LogicValue.ONE : LogicValue.ZERO);
            }
            TruthTableSpec spec = new TruthTableSpec("T", names, List.of("OUT"), List.of(values));
            for (Minimization.Form form : Minimization.Form.values()) {
                String text = TermText.typedExpression(Minimizer.minimize(spec, form), 0);
                ExpressionParser.ParseResult result = ExpressionParser.parseLine(text);
                assertThat(result.problem()).as(text).isEmpty();
                assertThat(result.mergedNames()).as(text).isEmpty();
                Expression expression = result.line().orElseThrow().expression();
                for (int row = 0; row < spec.rowCount(); row++) {
                    LogicValue expected = spec.value(0, row);
                    if (expected == LogicValue.DONT_CARE) {
                        continue;
                    }
                    Map<String, Boolean> assignment = new HashMap<>();
                    for (int input = 0; input < inputs; input++) {
                        assignment.put(names.get(input), spec.inputValue(row, input));
                    }
                    assertThat(expression.evaluate(assignment)).as(text + " row " + row)
                            .isEqualTo(expected == LogicValue.ONE);
                }
            }
        }
    }
}
