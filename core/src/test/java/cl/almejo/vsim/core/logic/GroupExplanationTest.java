package cl.almejo.vsim.core.logic;

import cl.almejo.vsim.core.logic.GroupExplanation.AlgebraStep;
import cl.almejo.vsim.core.logic.GroupExplanation.Combination;
import cl.almejo.vsim.core.logic.GroupExplanation.VariableBehavior;
import cl.almejo.vsim.core.logic.GroupExplanation.VariableRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GroupExplanationTest {

    private static final TruthTableSpec FOUR_INPUTS =
            TruthTableSpec.zeros("Tabla", List.of("A", "B", "C", "D"), List.of("F"));
    private static final TruthTableSpec THREE_INPUTS =
            TruthTableSpec.zeros("Tabla", List.of("A", "B", "C"), List.of("F"));

    /** B'C over A, B, C, D: «A cambia → se va · B = 0 → B' · C = 1 → C · D cambia → se va». */
    private static final Term B_NOT_C = new Term(0b0110, 0b0010);

    @Test
    void eachVariableStaysOrChangesLikeInTheTalk() {
        GroupExplanation explanation = GroupExplanation.of(B_NOT_C, FOUR_INPUTS);

        assertThat(explanation.variables()).containsExactly(
                new VariableRole(0, "A", VariableBehavior.CHANGES, ""),
                new VariableRole(1, "B", VariableBehavior.STAYS_ZERO, "B'"),
                new VariableRole(2, "C", VariableBehavior.STAYS_ONE, "C"),
                new VariableRole(3, "D", VariableBehavior.CHANGES, ""));
        assertThat(explanation.termText()).isEqualTo("B'C");
        assertThat(explanation.cellCount()).isEqualTo(4);
        assertThat(explanation.remainingVariables()).isEqualTo(2);
        assertThat(explanation.form()).isEqualTo(Minimization.Form.SUM_OF_PRODUCTS);
    }

    @Test
    void theCellsAreTheGroupsRowsInOrderAsFullProducts() {
        GroupExplanation explanation = GroupExplanation.of(B_NOT_C, FOUR_INPUTS);

        assertThat(explanation.cellTerms()).containsExactly(
                new Term(0b1111, 0b0010),
                new Term(0b1111, 0b0011),
                new Term(0b1111, 0b1010),
                new Term(0b1111, 0b1011));
    }

    @Test
    void theAlgebraEliminatesOneChangingVariablePerStep() {
        GroupExplanation explanation = GroupExplanation.of(B_NOT_C, FOUR_INPUTS);

        // A'B'CD' + AB'CD' = B'CD' and A'B'CD + AB'CD = B'CD; then B'CD' + B'CD = B'C.
        assertThat(explanation.steps()).containsExactly(
                new AlgebraStep(0, List.of(
                        new Combination(new Term(0b1111, 0b0010), new Term(0b1111, 0b1010), new Term(0b0111, 0b0010)),
                        new Combination(new Term(0b1111, 0b0011), new Term(0b1111, 0b1011),
                                new Term(0b0111, 0b0011)))),
                new AlgebraStep(3, List.of(
                        new Combination(new Term(0b0111, 0b0010), new Term(0b0111, 0b0011), B_NOT_C))));
    }

    @Test
    void aSingleCellNeedsNoAlgebra() {
        GroupExplanation explanation = GroupExplanation.of(new Term(0b111, 0b101), THREE_INPUTS);

        assertThat(explanation.termText()).isEqualTo("AB'C");
        assertThat(explanation.cellCount()).isEqualTo(1);
        assertThat(explanation.remainingVariables()).isEqualTo(3);
        assertThat(explanation.cellTerms()).containsExactly(new Term(0b111, 0b101));
        assertThat(explanation.steps()).isEmpty();
    }

    @Test
    void theWholeMapLeavesNoVariableAndIsOne() {
        GroupExplanation explanation = GroupExplanation.of(new Term(0, 0), THREE_INPUTS);

        assertThat(explanation.termText()).isEqualTo("1");
        assertThat(explanation.cellCount()).isEqualTo(8);
        assertThat(explanation.remainingVariables()).isZero();
        assertThat(explanation.variables()).extracting(VariableRole::behavior)
                .containsOnly(VariableBehavior.CHANGES);
        assertThat(explanation.steps()).hasSize(3);
        assertThat(explanation.steps().get(2).combinations())
                .containsExactly(new Combination(new Term(0b001, 0b000), new Term(0b001, 0b001), new Term(0, 0)));
    }

    @Test
    void aGroupOfZerosGivesTheSumTermWithTheLiteralsComplemented() {
        // Group of 0s where B = 0 and C = 1 → the sum (B + C').
        GroupExplanation explanation =
                GroupExplanation.of(B_NOT_C, FOUR_INPUTS, Minimization.Form.PRODUCT_OF_SUMS);

        assertThat(explanation.form()).isEqualTo(Minimization.Form.PRODUCT_OF_SUMS);
        assertThat(explanation.termText()).isEqualTo("(B + C')");
        assertThat(explanation.variables()).containsExactly(
                new VariableRole(0, "A", VariableBehavior.CHANGES, ""),
                new VariableRole(1, "B", VariableBehavior.STAYS_ZERO, "B"),
                new VariableRole(2, "C", VariableBehavior.STAYS_ONE, "C'"),
                new VariableRole(3, "D", VariableBehavior.CHANGES, ""));
        assertThat(explanation.steps()).hasSize(2);
    }

    @Test
    void theWholeMapOfZerosIsZero() {
        GroupExplanation explanation =
                GroupExplanation.of(new Term(0, 0), THREE_INPUTS, Minimization.Form.PRODUCT_OF_SUMS);

        assertThat(explanation.termText()).isEqualTo("0");
    }
}
