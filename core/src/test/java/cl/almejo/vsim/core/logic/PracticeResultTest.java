package cl.almejo.vsim.core.logic;

import cl.almejo.vsim.core.logic.Minimization.Form;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;

import static cl.almejo.vsim.core.logic.GroupCheckTest.randomValues;
import static cl.almejo.vsim.core.logic.GroupCheckTest.spec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeResultTest {

    private static final LogicValue O = LogicValue.ZERO;
    private static final LogicValue I = LogicValue.ONE;
    private static final LogicValue X = LogicValue.DONT_CARE;

    /** 3 inputs, F = A: rows 4..7. */
    private static final TruthTableSpec A = spec(3, List.of(O, O, O, O, I, I, I, I));
    private static final Term TERM_A = new Term(0b100, 0b100);
    private static final Term TERM_A_NEGATED = new Term(0b100, 0b000);
    private static final Term TERM_AB = new Term(0b110, 0b110);
    private static final Term TERM_B = new Term(0b010, 0b010);

    @Test
    void theRowsStillToGroupAreTheUncoveredOnesNeverTheDontCares() {
        // 3 inputs: 1s at 4, 5, 6; X at 7.
        TruthTableSpec spec = spec(3, List.of(O, O, O, O, I, I, I, X));

        PracticeResult result = PracticeResult.of(spec, 0, Form.SUM_OF_PRODUCTS, List.of(TERM_AB));

        assertThat(result.uncoveredRows()).containsExactly(4, 5);
        assertThat(PracticeResult.of(spec, 0, Form.SUM_OF_PRODUCTS, List.of(TERM_A)).uncoveredRows()).isEmpty();
    }

    @Test
    void anIncompleteCoverIsNotEquivalentAtItsFirstUncoveredRow() {
        PracticeResult result = PracticeResult.of(A, 0, Form.SUM_OF_PRODUCTS, List.of(TERM_AB));

        assertThat(result.equivalent()).isFalse();
        assertThat(result.firstDifferingRow()).hasValue(4);
        assertThat(result.minimal()).isFalse();
    }

    @Test
    void aTermOverAZeroMakesTheFirstDifferingRowThatZero() {
        PracticeResult result = PracticeResult.of(A, 0, Form.SUM_OF_PRODUCTS, List.of(TERM_A, TERM_B));

        assertThat(result.equivalent()).isFalse();
        assertThat(result.firstDifferingRow()).hasValue(2);
        assertThat(result.uncoveredRows()).isEmpty();
    }

    @Test
    void dontCaresNeverMakeACoverDiffer() {
        TruthTableSpec spec = spec(3, List.of(O, O, X, X, I, I, I, I));

        PracticeResult result = PracticeResult.of(spec, 0, Form.SUM_OF_PRODUCTS, List.of(TERM_A, TERM_B));

        assertThat(result.equivalent()).isTrue();
        assertThat(result.firstDifferingRow()).isEmpty();
    }

    @Test
    void theMinimalCoverIsMinimalAndCountsItsGroupsAndLiterals() {
        PracticeResult result = PracticeResult.of(A, 0, Form.SUM_OF_PRODUCTS, List.of(TERM_A));

        assertThat(result.equivalent()).isTrue();
        assertThat(result.minimal()).isTrue();
        assertThat(result.groupCount()).isEqualTo(1);
        assertThat(result.literalCount()).isEqualTo(1);
        assertThat(result.solutionGroupCount()).isEqualTo(1);
        assertThat(result.solutionLiteralCount()).isEqualTo(1);
    }

    @Test
    void aProductOfSumsGroupsTheZeros() {
        PracticeResult minimal = PracticeResult.of(A, 0, Form.PRODUCT_OF_SUMS, List.of(TERM_A_NEGATED));
        PracticeResult wrongZeros = PracticeResult.of(A, 0, Form.PRODUCT_OF_SUMS, List.of(TERM_A));

        assertThat(minimal.equivalent()).isTrue();
        assertThat(minimal.minimal()).isTrue();
        assertThat(minimal.uncoveredRows()).isEmpty();
        assertThat(wrongZeros.equivalent()).isFalse();
        assertThat(wrongZeros.firstDifferingRow()).hasValue(0);
        assertThat(wrongZeros.uncoveredRows()).containsExactly(0, 1, 2, 3);
    }

    @Test
    void anExtraGroupOrExtraLiteralsAreCorrectButNotMinimal() {
        // 2 inputs, F = A + B.
        TruthTableSpec spec = spec(2, List.of(O, I, I, I));
        Term termA = new Term(0b10, 0b10);
        Term termB = new Term(0b01, 0b01);

        PracticeResult extraGroup = PracticeResult.of(spec, 0, Form.SUM_OF_PRODUCTS,
                List.of(termA, termB, new Term(0b11, 0b11)));
        PracticeResult extraLiteral = PracticeResult.of(spec, 0, Form.SUM_OF_PRODUCTS,
                List.of(termA, new Term(0b11, 0b01)));

        assertThat(extraGroup.equivalent()).isTrue();
        assertThat(extraGroup.minimal()).isFalse();
        assertThat(extraGroup.groupCount()).isEqualTo(3);
        assertThat(extraGroup.literalCount()).isEqualTo(4);
        assertThat(extraGroup.solutionGroupCount()).isEqualTo(2);
        assertThat(extraGroup.solutionLiteralCount()).isEqualTo(2);
        assertThat(extraLiteral.equivalent()).isTrue();
        assertThat(extraLiteral.minimal()).isFalse();
        assertThat(extraLiteral.literalCount()).isEqualTo(3);
    }

    @Test
    void theMinimizersCoverIsAlwaysMinimalForRandomFunctionsBothForms() {
        Random random = new Random(19102026L);
        for (int inputCount = 2; inputCount <= 4; inputCount++) {
            for (int sample = 0; sample < 100; sample++) {
                TruthTableSpec spec = spec(inputCount, randomValues(random, 1 << inputCount, 0.25));
                for (Form form : Form.values()) {
                    List<Term> terms = Minimizer.minimize(spec, form).outputs().get(0).terms();

                    PracticeResult result = PracticeResult.of(spec, 0, form, terms);

                    assertThat(result.equivalent()).as("%s %s", form, spec.outputs()).isTrue();
                    assertThat(result.minimal()).as("%s %s", form, spec.outputs()).isTrue();
                    assertThat(result.uncoveredRows()).isEmpty();
                    assertThat(result.groupCount()).isEqualTo(result.solutionGroupCount());
                    assertThat(result.literalCount()).isEqualTo(result.solutionLiteralCount());
                    assertThat(PracticeResult.hintRow(spec, 0, form, terms)).isEmpty();
                }
            }
        }
    }

    @Test
    void anAllZeroSumOfProductsNeedsNoGroups() {
        TruthTableSpec spec = spec(3, Collections.nCopies(8, O));

        PracticeResult result = PracticeResult.of(spec, 0, Form.SUM_OF_PRODUCTS, List.of());

        assertThat(result.uncoveredRows()).isEmpty();
        assertThat(result.equivalent()).isTrue();
        assertThat(result.minimal()).isTrue();
        assertThat(result.solutionGroupCount()).isZero();
        assertThat(PracticeResult.hintRow(spec, 0, Form.SUM_OF_PRODUCTS, List.of())).isEmpty();
    }

    @Test
    void anAllOneSumOfProductsIsTheWholeMap() {
        TruthTableSpec spec = spec(4, Collections.nCopies(16, I));

        PracticeResult wholeMap = PracticeResult.of(spec, 0, Form.SUM_OF_PRODUCTS, List.of(new Term(0, 0)));
        PracticeResult nothing = PracticeResult.of(spec, 0, Form.SUM_OF_PRODUCTS, List.of());

        assertThat(wholeMap.minimal()).isTrue();
        assertThat(wholeMap.solutionGroupCount()).isEqualTo(1);
        assertThat(wholeMap.solutionLiteralCount()).isZero();
        assertThat(nothing.equivalent()).isFalse();
        assertThat(nothing.firstDifferingRow()).hasValue(0);
        assertThat(nothing.uncoveredRows()).hasSize(16);
        assertThat(PracticeResult.hintGroup(spec, 0, Form.SUM_OF_PRODUCTS, 5)).isEqualTo(new Term(0, 0));
    }

    @Test
    void anAllDontCareOutputIsEquivalentWithAnyCoverButMinimalOnlyWithoutGroups() {
        TruthTableSpec spec = spec(2, Collections.nCopies(4, X));

        PracticeResult empty = PracticeResult.of(spec, 0, Form.PRODUCT_OF_SUMS, List.of());
        PracticeResult oneGroup = PracticeResult.of(spec, 0, Form.PRODUCT_OF_SUMS, List.of(new Term(0, 0)));

        assertThat(empty.minimal()).isTrue();
        assertThat(oneGroup.equivalent()).isTrue();
        assertThat(oneGroup.minimal()).isFalse();
        assertThat(oneGroup.uncoveredRows()).isEmpty();
    }

    @Test
    void theHintPrefersAnEssentialCell() {
        // 3 inputs, F = m(0, 1, 3): primes A'B' (0, 1) and A'C (1, 3). With row 0 grouped alone, row 1 has two primes
        // and row 3 only one: the hint marks row 3 although row 1 is lower.
        TruthTableSpec spec = spec(3, List.of(I, I, O, I, O, O, O, O));
        List<Term> terms = List.of(new Term(0b111, 0b000));

        assertThat(PracticeResult.hintRow(spec, 0, Form.SUM_OF_PRODUCTS, terms)).isEqualTo(OptionalInt.of(3));
        assertThat(PracticeResult.hintGroup(spec, 0, Form.SUM_OF_PRODUCTS, 3)).isEqualTo(new Term(0b101, 0b001));
    }

    @Test
    void withoutEssentialCellsTheHintIsTheLowestUncoveredRow() {
        // 3 inputs, the cyclic F = m(0, 1, 2, 5, 6, 7): every 1 has two primes.
        TruthTableSpec spec = spec(3, List.of(I, I, I, O, O, I, I, I));

        assertThat(PracticeResult.hintRow(spec, 0, Form.SUM_OF_PRODUCTS, List.of())).isEqualTo(OptionalInt.of(0));
        assertThat(PracticeResult.hintRow(spec, 0, Form.SUM_OF_PRODUCTS, List.of(new Term(0b110, 0b000))))
                .isEqualTo(OptionalInt.of(2));
    }

    @Test
    void theHintInAProductOfSumsLooksForZeros() {
        assertThat(PracticeResult.hintRow(A, 0, Form.PRODUCT_OF_SUMS, List.of())).isEqualTo(OptionalInt.of(0));
        assertThat(PracticeResult.hintGroup(A, 0, Form.PRODUCT_OF_SUMS, 2)).isEqualTo(TERM_A_NEGATED);
    }

    @Test
    void theHintGroupIsTheLargestPrimeWithTheTieRuleOfCanGrow() {
        List<LogicValue> values = new ArrayList<>(Collections.nCopies(16, I));
        values.set(0, O);
        TruthTableSpec spec = spec(4, values);

        assertThat(PracticeResult.hintGroup(spec, 0, Form.SUM_OF_PRODUCTS, 15)).isEqualTo(new Term(0b0001, 0b0001));
    }

    @Test
    void aHintGroupForACellThatNeedsNoGroupIsRefused() {
        assertThatThrownBy(() -> PracticeResult.hintGroup(A, 0, Form.SUM_OF_PRODUCTS, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theResultReadsTheGivenOutput() {
        TruthTableSpec spec = new TruthTableSpec("T", List.of("A", "B"), List.of("F", "G"),
                List.of(List.of(O, O, O, O), List.of(I, I, I, I)));

        PracticeResult result = PracticeResult.of(spec, 1, Form.SUM_OF_PRODUCTS, List.of(new Term(0, 0)));

        assertThat(result.minimal()).isTrue();
    }
}
