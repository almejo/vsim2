package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.DrawnGroup;
import cl.almejo.vsim.core.logic.GroupCheck;
import cl.almejo.vsim.core.logic.KarnaughGroup;
import cl.almejo.vsim.core.logic.KarnaughLayout;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.Minimization.Form;
import cl.almejo.vsim.core.logic.Term;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;

import static cl.almejo.vsim.core.logic.Minimization.Form.PRODUCT_OF_SUMS;
import static cl.almejo.vsim.core.logic.Minimization.Form.SUM_OF_PRODUCTS;
import static org.assertj.core.api.Assertions.assertThat;

class PracticeModelTest {

    /** A'C (rows 1 and 3) of three inputs A, B, C. */
    private static final Term A_NOT_C = new Term(0b101, 0b001);
    /** BC (rows 3 and 7). */
    private static final Term B_C = new Term(0b011, 0b011);

    private KarnaughModel model;
    private PracticeModel practice;
    private final KarnaughLayout layout = KarnaughLayout.of(3);

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        model = new KarnaughModel();
        practice = model.practice();
    }

    /** F is 1 on {@code fOnes}, G on {@code gOnes}; three inputs. */
    private void load(List<Integer> fOnes, List<Integer> gOnes) {
        model.load(new TruthTableSpec("Tabla", List.of("A", "B", "C"), List.of("F", "G"),
                List.of(column(fOnes), column(gOnes))));
    }

    private static List<LogicValue> column(List<Integer> ones) {
        List<LogicValue> values = new ArrayList<>();
        for (int row = 0; row < 8; row++) {
            values.add(ones.contains(row) ? LogicValue.ONE : LogicValue.ZERO);
        }
        return values;
    }

    /** The drawn rectangle covering exactly the group of {@code term} (no wrap needed for the terms used here). */
    private DrawnGroup drawn(Term term) {
        KarnaughGroup.CellRectangle piece = layout.group(term).rectangles().getFirst();
        return layout.drawn(piece.row(), piece.column(), piece.rowSpan(), piece.columnSpan());
    }

    private DrawnGroup cell(int tableRow) {
        KarnaughLayout.GridCell gridCell = layout.cell(tableRow);
        return layout.drawn(gridCell.row(), gridCell.column(), 1, 1);
    }

    private List<GroupCheck.Verdict> verdicts(int output, Form form) {
        return practice.verdicts(output, form).stream().map(GroupCheck.Result::verdict).toList();
    }

    @Test
    void startsInSolutionModeWithNoGroupsAndHearsChangesThroughTheModel() {
        AtomicInteger changes = new AtomicInteger();
        model.addListener(changes::incrementAndGet);
        assertThat(practice.practising()).isFalse();
        assertThat(practice.groups(0, SUM_OF_PRODUCTS)).isEmpty();
        assertThat(practice.practised(0, SUM_OF_PRODUCTS)).isFalse();

        practice.setPractising(true);
        practice.add(0, SUM_OF_PRODUCTS, cell(1));

        assertThat(practice.practising()).isTrue();
        assertThat(practice.practised(0, SUM_OF_PRODUCTS)).isTrue();
        assertThat(practice.practised(0, PRODUCT_OF_SUMS)).isFalse();
        assertThat(changes.get()).isEqualTo(2);
    }

    @Test
    void groupsSurviveModesOutputsFormsTitlesAndNames() {
        load(List.of(1, 3, 7), List.of(0, 4));
        practice.add(0, SUM_OF_PRODUCTS, drawn(A_NOT_C));
        practice.add(0, PRODUCT_OF_SUMS, cell(0));
        practice.add(1, SUM_OF_PRODUCTS, cell(0));

        practice.setPractising(true);
        practice.setPractising(false);
        model.setSelectedOutput(1);
        model.setOptions(model.options().withForm(PRODUCT_OF_SUMS));
        model.setTitle("Otra");
        model.setOutputName(0, "Z");
        model.setInputName(2, "Q");

        assertThat(practice.groups(0, SUM_OF_PRODUCTS)).containsExactly(drawn(A_NOT_C));
        assertThat(practice.groups(0, PRODUCT_OF_SUMS)).containsExactly(cell(0));
        assertThat(practice.groups(1, SUM_OF_PRODUCTS)).containsExactly(cell(0));
        assertThat(practice.restarted()).isFalse();
    }

    @Test
    void anyChangedValueRestartsThePracticeOfEveryOutputAndFormWithTheNotice() {
        load(List.of(1, 3, 7), List.of(0, 4));
        practice.setPractising(true);
        practice.add(0, SUM_OF_PRODUCTS, drawn(A_NOT_C));
        practice.add(0, PRODUCT_OF_SUMS, cell(0));
        practice.add(1, SUM_OF_PRODUCTS, cell(0));
        practice.pressHint();
        practice.reveal(1, SUM_OF_PRODUCTS);
        practice.giveUp(1, PRODUCT_OF_SUMS);

        model.setValue(1, 5, LogicValue.DONT_CARE); // only G's column changed: every output restarts

        for (int output = 0; output < 2; output++) {
            for (Form form : Form.values()) {
                assertThat(practice.groups(output, form)).isEmpty();
                assertThat(practice.revealed(output, form)).isFalse();
                assertThat(practice.givenUp(output, form)).isFalse();
                assertThat(practice.hintsUsed(output, form)).isZero();
            }
        }
        assertThat(practice.hintRow()).isEmpty();
        assertThat(practice.restarted()).isTrue();

        practice.add(1, SUM_OF_PRODUCTS, cell(4)); // the next drawing dismisses the notice
        assertThat(practice.restarted()).isFalse();
    }

    @Test
    void theNoticeCanBeDismissedAndARestartWithNothingToLoseSaysNothing() {
        load(List.of(1, 3, 7), List.of(0, 4));
        practice.setPractising(true);

        model.setValue(1, 2, LogicValue.ONE);
        assertThat(practice.restarted()).as("nothing was practised").isFalse();

        practice.add(0, SUM_OF_PRODUCTS, drawn(A_NOT_C));
        model.setValue(0, 2, LogicValue.ONE);
        assertThat(practice.restarted()).isTrue();
        practice.clearRestartedNotice();
        assertThat(practice.restarted()).isFalse();
    }

    @Test
    void aChangedInputOrOutputCountRestartsThePractice() {
        load(List.of(1, 3, 7), List.of(0, 4));
        practice.add(0, SUM_OF_PRODUCTS, drawn(A_NOT_C));
        practice.add(1, PRODUCT_OF_SUMS, cell(1));

        model.setInputCount(4);

        assertThat(practice.groups(0, SUM_OF_PRODUCTS)).isEmpty();
        assertThat(practice.groups(1, PRODUCT_OF_SUMS)).isEmpty();
        assertThat(practice.restarted()).isTrue();

        model.setInputCount(3);
        practice.add(0, SUM_OF_PRODUCTS, cell(0));
        model.setOutputCount(3);
        assertThat(practice.groups(0, SUM_OF_PRODUCTS)).as("an added output restarts too").isEmpty();
        practice.add(0, SUM_OF_PRODUCTS, cell(0));
        model.setOutputCount(1);
        assertThat(practice.groups(0, SUM_OF_PRODUCTS)).as("and a removed one").isEmpty();
        assertThat(practice.restarted()).isTrue();
    }

    @Test
    void anOutputIsPendingDoneOrGivenUpPerForm() {
        load(List.of(1, 3, 7), List.of());
        practice.setPractising(true);
        assertThat(practice.state(0, SUM_OF_PRODUCTS)).isEqualTo(PracticeModel.MapState.PENDING);
        assertThat(practice.state(1, SUM_OF_PRODUCTS)).as("no 1s to group").isEqualTo(PracticeModel.MapState.DONE);
        assertThat(practice.pendingOutputs()).containsExactly(0);

        practice.add(0, SUM_OF_PRODUCTS, drawn(A_NOT_C));
        practice.add(0, SUM_OF_PRODUCTS, cell(0)); // red: it does not count
        assertThat(practice.state(0, SUM_OF_PRODUCTS)).isEqualTo(PracticeModel.MapState.PENDING);
        practice.add(0, SUM_OF_PRODUCTS, cell(7)); // covers everything, not minimal: done anyway
        assertThat(practice.state(0, SUM_OF_PRODUCTS)).isEqualTo(PracticeModel.MapState.DONE);
        assertThat(practice.pendingOutputs()).isEmpty();
        assertThat(practice.state(0, PRODUCT_OF_SUMS)).isEqualTo(PracticeModel.MapState.PENDING);
        assertThat(practice.state(1, PRODUCT_OF_SUMS)).isEqualTo(PracticeModel.MapState.PENDING);

        model.setOptions(model.options().withForm(PRODUCT_OF_SUMS));
        assertThat(practice.pendingOutputs()).as("the chosen form's maps").containsExactly(0, 1);
    }

    @Test
    void givingUpIsPermanentPerMapAndMakesItReadOnly() {
        load(List.of(1, 3, 7), List.of(0, 4));
        practice.setPractising(true);
        practice.add(0, SUM_OF_PRODUCTS, drawn(A_NOT_C));
        practice.select(0);
        practice.pressHint();
        int[] events = {0};
        model.addListener(() -> events[0]++);

        practice.giveUp(0, SUM_OF_PRODUCTS);

        assertThat(events[0]).isEqualTo(1);
        assertThat(practice.givenUp(0, SUM_OF_PRODUCTS)).isTrue();
        assertThat(practice.revealed(0, SUM_OF_PRODUCTS)).isTrue();
        assertThat(practice.state(0, SUM_OF_PRODUCTS)).isEqualTo(PracticeModel.MapState.GIVEN_UP);
        assertThat(practice.givenUp(0, PRODUCT_OF_SUMS)).isFalse();
        assertThat(practice.givenUp(1, SUM_OF_PRODUCTS)).isFalse();
        assertThat(practice.selected()).isEmpty();
        assertThat(practice.hintRow()).isEmpty();
        assertThat(practice.groups(0, SUM_OF_PRODUCTS)).as("the student's groups stay")
                .containsExactly(drawn(A_NOT_C));

        assertThat(practice.add(0, SUM_OF_PRODUCTS, drawn(B_C))).isFalse();
        practice.remove(0, SUM_OF_PRODUCTS, 0);
        practice.clear(0, SUM_OF_PRODUCTS);
        practice.select(0);
        assertThat(practice.pressHint()).isFalse();
        assertThat(practice.groups(0, SUM_OF_PRODUCTS)).containsExactly(drawn(A_NOT_C));
        assertThat(practice.selected()).isEmpty();

        model.setOutputName(0, "Z");
        model.setTitle("Otra");
        assertThat(practice.givenUp(0, SUM_OF_PRODUCTS)).as("renames keep it").isTrue();
    }

    @Test
    void aGivenUpOutputUsesVsimsTermsInStepFour() {
        load(List.of(1, 3, 7), List.of(0, 4));
        practice.setPractising(true);
        Minimization vsim = model.minimization();
        practice.add(0, SUM_OF_PRODUCTS, drawn(A_NOT_C));
        practice.add(1, SUM_OF_PRODUCTS, cell(0));
        practice.add(1, SUM_OF_PRODUCTS, cell(4));
        assertThat(model.studentGroupsProblem()).contains("F: tus grupos no cubren la tabla");

        practice.giveUp(0, SUM_OF_PRODUCTS);

        assertThat(model.studentGroupsProblem()).isEmpty();
        assertThat(model.usingStudentGroups()).isTrue();
        Minimization effective = model.effectiveMinimization();
        assertThat(effective.outputs().get(0)).isEqualTo(vsim.outputs().get(0));
        assertThat(effective.outputs().get(1).terms()).hasSize(2);
        assertThat(model.shownMinimization().outputs().get(0)).isEqualTo(vsim.outputs().get(0));

        practice.giveUp(1, SUM_OF_PRODUCTS);
        assertThat(model.anyPractised()).as("nothing of the student's left to use").isFalse();
        assertThat(model.effectiveMinimization()).isEqualTo(vsim);
    }

    @Test
    void verdictsAreJudgedAgainstTheOthersAndFollowARemoval() {
        load(List.of(1, 3), List.of());
        practice.add(0, SUM_OF_PRODUCTS, drawn(A_NOT_C));
        practice.add(0, SUM_OF_PRODUCTS, drawn(A_NOT_C));
        practice.add(0, SUM_OF_PRODUCTS, cell(0));
        assertThat(verdicts(0, SUM_OF_PRODUCTS)).containsExactly(GroupCheck.Verdict.VALID,
                GroupCheck.Verdict.REDUNDANT, GroupCheck.Verdict.INCLUDES_WRONG_VALUE);
        assertThat(practice.validTerms(0, SUM_OF_PRODUCTS)).containsExactly(A_NOT_C, A_NOT_C);

        practice.remove(0, SUM_OF_PRODUCTS, 0);

        assertThat(verdicts(0, SUM_OF_PRODUCTS)).containsExactly(GroupCheck.Verdict.VALID,
                GroupCheck.Verdict.INCLUDES_WRONG_VALUE);
        assertThat(practice.result(0, SUM_OF_PRODUCTS).equivalent()).isTrue();
        assertThat(practice.result(0, SUM_OF_PRODUCTS).groupCount()).isEqualTo(1);
    }

    @Test
    void theResultGradesTheValidGroupsOnly() {
        load(List.of(1, 3, 7), List.of());
        practice.add(0, SUM_OF_PRODUCTS, drawn(A_NOT_C));

        assertThat(practice.result(0, SUM_OF_PRODUCTS).uncoveredRows()).containsExactly(7);
        assertThat(practice.result(0, SUM_OF_PRODUCTS).equivalent()).isFalse();

        practice.add(0, SUM_OF_PRODUCTS, drawn(B_C));
        assertThat(practice.result(0, SUM_OF_PRODUCTS).minimal()).isTrue();
    }

    @Test
    void moreThanSixteenGroupsAreRefused() {
        for (int count = 0; count < PracticeModel.MAXIMUM_GROUPS; count++) {
            assertThat(practice.add(0, SUM_OF_PRODUCTS, cell(count % 8))).isTrue();
        }
        assertThat(practice.add(0, SUM_OF_PRODUCTS, cell(0))).isFalse();
        assertThat(practice.groups(0, SUM_OF_PRODUCTS)).hasSize(PracticeModel.MAXIMUM_GROUPS);
    }

    @Test
    void theSelectionIsPerShownOutputAndFormAndFollowsRemovals() {
        load(List.of(1, 3, 7), List.of(0, 4));
        practice.add(0, SUM_OF_PRODUCTS, cell(1));
        practice.add(0, SUM_OF_PRODUCTS, cell(3));
        practice.add(0, SUM_OF_PRODUCTS, cell(7));

        practice.select(2);
        assertThat(practice.selected()).isEqualTo(OptionalInt.of(2));
        model.setSelectedOutput(1);
        assertThat(practice.selected()).isEmpty();
        model.setSelectedOutput(0);
        model.setOptions(model.options().withForm(PRODUCT_OF_SUMS));
        assertThat(practice.selected()).isEmpty();
        model.setOptions(model.options().withForm(SUM_OF_PRODUCTS));
        assertThat(practice.selected()).isEqualTo(OptionalInt.of(2));

        practice.remove(0, SUM_OF_PRODUCTS, 0);
        assertThat(practice.selected()).isEqualTo(OptionalInt.of(1));
        practice.remove(0, SUM_OF_PRODUCTS, 1);
        assertThat(practice.selected()).isEmpty();

        practice.select(0);
        practice.select(5);
        assertThat(practice.selected()).isEmpty();
        practice.select(0);
        practice.clearSelection();
        assertThat(practice.selected()).isEmpty();
    }

    @Test
    void hintsMarkAnEssentialCellThenShowItsGroupAndAreCounted() {
        load(List.of(1, 3, 7), List.of());

        assertThat(practice.pressHint()).isTrue();
        assertThat(practice.hintRow()).isEqualTo(OptionalInt.of(1));
        assertThat(practice.hintGroup()).isEmpty();
        assertThat(practice.hintsUsed(0, SUM_OF_PRODUCTS)).isEqualTo(1);

        assertThat(practice.pressHint()).isTrue();
        assertThat(practice.hintRow()).isEqualTo(OptionalInt.of(1));
        assertThat(practice.hintGroup()).contains(A_NOT_C);
        assertThat(practice.hintsUsed(0, SUM_OF_PRODUCTS)).isEqualTo(2);

        assertThat(practice.pressHint()).isFalse(); // nothing more to show for this cell
        assertThat(practice.hintsUsed(0, SUM_OF_PRODUCTS)).isEqualTo(2);

        practice.add(0, SUM_OF_PRODUCTS, drawn(A_NOT_C));
        assertThat(practice.hintRow()).isEmpty();
        assertThat(practice.hintGroup()).isEmpty();

        assertThat(practice.pressHint()).isTrue();
        assertThat(practice.hintRow()).isEqualTo(OptionalInt.of(7));
        assertThat(practice.pressHint()).isTrue();
        assertThat(practice.hintGroup()).contains(B_C);
        assertThat(practice.hintsUsed(0, SUM_OF_PRODUCTS)).isEqualTo(4);
        assertThat(practice.hintsUsed(0, PRODUCT_OF_SUMS)).isZero();

        practice.add(0, SUM_OF_PRODUCTS, drawn(B_C));
        assertThat(practice.pressHint()).isFalse(); // every 1 is grouped
        assertThat(practice.hintsUsed(0, SUM_OF_PRODUCTS)).isEqualTo(4);
    }

    @Test
    void outsideThePracticeTheStudentsGroupsAreNeverUsed() {
        load(List.of(1, 3, 5, 7), List.of(0, 4));
        practice.add(0, SUM_OF_PRODUCTS, drawn(new Term(0b001, 0b001)));
        assertThat(model.usingStudentGroups()).isFalse();
        assertThat(model.effectiveMinimization()).isEqualTo(model.minimization());
        practice.setPractising(true);
        assertThat(model.usingStudentGroups()).isTrue();
    }

    @Test
    void anOutputStaysRevealedPerFormUntilTheTableChanges() {
        load(List.of(1, 3, 7), List.of(0, 4));
        int[] events = {0};
        model.addListener(() -> events[0]++);
        assertThat(practice.revealed(0, SUM_OF_PRODUCTS)).isFalse();
        practice.reveal(0, SUM_OF_PRODUCTS);
        assertThat(events[0]).isEqualTo(1);
        practice.reveal(0, SUM_OF_PRODUCTS);
        assertThat(events[0]).as("once is enough").isEqualTo(1);
        assertThat(practice.revealed(0, SUM_OF_PRODUCTS)).isTrue();
        assertThat(practice.revealed(0, PRODUCT_OF_SUMS)).isFalse();
        assertThat(practice.revealed(1, SUM_OF_PRODUCTS)).isFalse();
        practice.add(0, SUM_OF_PRODUCTS, cell(1));
        practice.clear(0, SUM_OF_PRODUCTS);
        model.setOutputName(0, "Z");
        assertThat(practice.revealed(0, SUM_OF_PRODUCTS)).isTrue();
        model.setValue(1, 1, LogicValue.ONE);
        assertThat(practice.revealed(0, SUM_OF_PRODUCTS)).as("a new table, even another column").isFalse();
    }

    @Test
    void theEffectiveMinimizationUsesTheStudentsTermsOnlyForEquivalentPractisedOutputs() {
        load(List.of(1, 3, 7), List.of(0, 4));
        practice.setPractising(true);
        Minimization vsim = model.minimization();
        assertThat(model.anyPractised()).isFalse();
        assertThat(model.usingStudentGroups()).isFalse();
        assertThat(model.effectiveMinimization()).isEqualTo(vsim);

        practice.add(0, SUM_OF_PRODUCTS, drawn(B_C));
        practice.add(0, SUM_OF_PRODUCTS, cell(0)); // red: left out
        assertThat(model.studentGroupsProblem()).contains("F: tus grupos no cubren la tabla");
        assertThat(model.usingStudentGroups()).isFalse();
        assertThat(model.effectiveMinimization()).isEqualTo(vsim);

        practice.add(0, SUM_OF_PRODUCTS, drawn(A_NOT_C));
        assertThat(model.studentGroupsProblem()).isEmpty();
        assertThat(model.usingStudentGroups()).isTrue();
        Minimization effective = model.effectiveMinimization();
        assertThat(effective.outputs().get(0).terms()).containsExactly(B_C, A_NOT_C);
        assertThat(effective.outputs().get(1)).isEqualTo(vsim.outputs().get(1));
        assertThat(effective.form()).isEqualTo(SUM_OF_PRODUCTS);

        practice.add(1, SUM_OF_PRODUCTS, cell(0));
        assertThat(model.studentGroupsProblem()).contains("G: tus grupos no cubren la tabla");
        assertThat(model.effectiveMinimization()).isEqualTo(vsim);
        practice.add(1, SUM_OF_PRODUCTS, cell(4));
        assertThat(model.effectiveMinimization().outputs().get(1).terms()).hasSize(2);

        model.setUseStudentGroups(false);
        assertThat(model.usingStudentGroups()).isFalse();
        assertThat(model.effectiveMinimization()).isEqualTo(vsim);
        model.setUseStudentGroups(true);

        model.setOptions(model.options().withForm(PRODUCT_OF_SUMS)); // nothing practised in this form
        assertThat(model.anyPractised()).isFalse();
        assertThat(model.effectiveMinimization()).isEqualTo(model.minimization());
    }
}
