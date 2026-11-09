package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.DrawnGroup;
import cl.almejo.vsim.core.logic.KarnaughGroup;
import cl.almejo.vsim.core.logic.KarnaughLayout;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.logic.Term;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static cl.almejo.vsim.core.logic.Minimization.Form.SUM_OF_PRODUCTS;
import static org.assertj.core.api.Assertions.assertThat;

/** A mis-click on the table must not lose the practice, and closing a practice with progress asks first. */
class PracticeSafetyTest {

    private KarnaughModel model;
    private KarnaughWizard wizard;
    private int closeQuestions;
    private boolean closeAnswer;
    private int closes;

    @BeforeEach
    void create() {
        model = new KarnaughModel();
        model.setValue(0, 1, LogicValue.ONE);
        wizard = new KarnaughWizard(model, KarnaughCost.GENERATOR, new FakeCreationTarget(), Background.inline(),
                KarnaughWizard.Mode.PRACTICE, () -> true);
        wizard.setOnClose(() -> closes++);
        wizard.setPracticeCloseConfirmation(parent -> {
            closeQuestions++;
            return closeAnswer;
        });
        wizard.setDiscardConfirmation(parent -> true);
    }

    private void drawAGroup() {
        KarnaughGroup group = KarnaughLayout.of(model.inputCount()).group(new Term(0b11, 0b01));
        model.practice().add(0, SUM_OF_PRODUCTS, new DrawnGroup(group.cells(), group.rectangles()));
    }

    @Test
    void aMisClickOnTheTableCanBeUndoneInOneAction() {
        drawAGroup();
        model.cycle(0, 2);
        assertThat(model.practice().groups(0, SUM_OF_PRODUCTS)).isEmpty();
        assertThat(wizard.undoRestartButton().isVisible()).isTrue();

        wizard.undoRestartButton().doClick();

        assertThat(model.practice().groups(0, SUM_OF_PRODUCTS)).hasSize(1);
        assertThat(model.value(0, 2)).isEqualTo(LogicValue.ZERO);
        assertThat(model.value(0, 1)).isEqualTo(LogicValue.ONE);
        assertThat(model.practice().restarted()).isFalse();
        assertThat(wizard.undoRestartButton().isVisible()).isFalse();
    }

    @Test
    void anInputCountChangeCanBeUndoneToo() {
        drawAGroup();
        model.setInputCount(4);
        model.restorePractice();
        assertThat(model.inputCount()).isEqualTo(3);
        assertThat(model.practice().groups(0, SUM_OF_PRODUCTS)).hasSize(1);
    }

    @Test
    void twoMisClicksInARowStillRestoreTheOriginal() {
        drawAGroup();
        model.cycle(0, 2);
        model.cycle(0, 3);
        model.restorePractice();
        assertThat(model.value(0, 2)).isEqualTo(LogicValue.ZERO);
        assertThat(model.value(0, 3)).isEqualTo(LogicValue.ZERO);
        assertThat(model.practice().groups(0, SUM_OF_PRODUCTS)).hasSize(1);
    }

    @Test
    void thereIsNothingToUndoWithoutProgressOrAfterDrawingAgain() {
        model.cycle(0, 2);
        assertThat(model.canRestorePractice()).isFalse();
        drawAGroup();
        model.cycle(0, 3);
        assertThat(model.canRestorePractice()).isTrue();
        drawAGroup();
        assertThat(model.canRestorePractice()).as("new work must not be thrown away by an undo").isFalse();
    }

    @Test
    void closingWithGroupsDrawnAsksAndKeepingItOpenKeepsTheGroups() {
        drawAGroup();
        closeAnswer = false;
        wizard.requestClose();
        assertThat(closeQuestions).isEqualTo(1);
        assertThat(closes).isZero();
        assertThat(model.practice().groups(0, SUM_OF_PRODUCTS)).hasSize(1);

        closeAnswer = true;
        wizard.requestClose();
        assertThat(closes).isEqualTo(1);
    }

    @Test
    void closingWithoutProgressDoesNotAskAboutThePractice() {
        wizard.requestClose();
        assertThat(closeQuestions).isZero();
        assertThat(closes).isEqualTo(1);
    }
}
