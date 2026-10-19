package cl.almejo.vsim.core.logic;

import org.junit.jupiter.api.Test;

import java.util.List;

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
}
