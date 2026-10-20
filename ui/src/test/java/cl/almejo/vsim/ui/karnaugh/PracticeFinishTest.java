package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.DrawnGroup;
import cl.almejo.vsim.core.logic.KarnaughGroup;
import cl.almejo.vsim.core.logic.KarnaughLayout;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.logic.Term;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JTabbedPane;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;

import static cl.almejo.vsim.core.logic.Minimization.Form.PRODUCT_OF_SUMS;
import static cl.almejo.vsim.core.logic.Minimization.Form.SUM_OF_PRODUCTS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Finishing every output before going on (plan task 8): an output is done when the student's groups cover it or they
 * gave up on it (for good); the tabs say which; leaving step 3 with pending outputs asks; a changed table restarts
 * the practice.
 */
class PracticeFinishTest {

    private static final long A = 0b100;
    private static final long B = 0b010;
    private static final long C = 0b001;

    private KarnaughModel model;
    private KarnaughWizard wizard;
    private GroupsPanel panel;
    private PracticePanel practicePanel;
    private final List<String> leaveQuestions = new ArrayList<>();
    private boolean leaveAnswer;
    private boolean giveUpAnswer = true;

    /** F = A'B + AC (rows 2, 3, 5, 7), G = C (rows 1, 3, 5, 7), H = A (rows 4..7); three inputs. */
    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        model = new KarnaughModel();
        model.setOutputCount(3);
        for (int row : new int[] {2, 3, 5, 7}) {
            model.setValue(0, row, LogicValue.ONE);
        }
        for (int row : new int[] {1, 3, 5, 7}) {
            model.setValue(1, row, LogicValue.ONE);
        }
        for (int row : new int[] {4, 5, 6, 7}) {
            model.setValue(2, row, LogicValue.ONE);
        }
        wizard = new KarnaughWizard(model, KarnaughCost.GENERATOR, new FakeCreationTarget(), Background.inline(),
                KarnaughWizard.Mode.PRACTICE);
        wizard.setOnClose(() -> { });
        wizard.setLeaveConfirmation((parent, message) -> {
            leaveQuestions.add(message);
            return leaveAnswer;
        });
        panel = wizard.groupsPanel();
        practicePanel = panel.practicePanel();
        practicePanel.setGiveUpConfirmation((parent, outputName) -> giveUpAnswer);
        wizard.goTo(KarnaughWizard.Step.GROUPS);
    }

    private DrawnGroup group(long mask, long value) {
        KarnaughGroup group = KarnaughLayout.of(model.inputCount()).group(new Term(mask, value));
        return new DrawnGroup(group.cells(), group.rectangles());
    }

    /** F covered by A'B + AC. */
    private void finishF() {
        model.practice().add(0, SUM_OF_PRODUCTS, group(A | B, B));
        model.practice().add(0, SUM_OF_PRODUCTS, group(A | C, A | C));
    }

    private JTabbedPane tabs() {
        return (JTabbedPane) panel.mapView(0).getParent();
    }

    private List<String> tabTitles() {
        List<String> titles = new ArrayList<>();
        for (int index = 0; index < tabs().getTabCount(); index++) {
            titles.add(tabs().getTitleAt(index));
        }
        return titles;
    }

    @Test
    void theTabsMarkEachOutputDonePendingOrGivenUpWithATooltip() {
        assertThat(tabTitles()).containsExactly("F •", "G •", "H •");
        assertThat(tabs().getToolTipTextAt(0)).isEqualTo("F: pendiente, todavía hay que agrupar");

        finishF();
        model.practice().add(1, SUM_OF_PRODUCTS, group(C, C));
        model.setSelectedOutput(2);
        practicePanel.giveUpButton().doClick();

        assertThat(tabTitles()).containsExactly("F ✓", "G ✓", "H ✗");
        assertThat(tabs().getToolTipTextAt(0)).isEqualTo("F: lista, tus grupos cubren la tabla");
        assertThat(tabs().getToolTipTextAt(2)).isEqualTo("H: te rendiste, se ve la solución de VSim");

        panel.practiceFormButton(PRODUCT_OF_SUMS).doClick();
        assertThat(tabTitles()).as("the other form's maps").containsExactly("F •", "G •", "H •");
    }

    @Test
    void stepTwoAndTheSolutionWizardKeepPlainTabs() {
        JTabbedPane stepTwo = (JTabbedPane) wizard.mapPanel().mapView(0).getParent();
        assertThat(stepTwo.getTitleAt(0)).isEqualTo("F");

        KarnaughModel solutionModel = new KarnaughModel();
        KarnaughWizard solution = new KarnaughWizard(solutionModel, KarnaughCost.GENERATOR,
                new FakeCreationTarget(), Background.inline());
        solution.goTo(KarnaughWizard.Step.GROUPS);
        JTabbedPane solutionTabs = (JTabbedPane) solution.groupsPanel().mapView(0).getParent();
        assertThat(solutionTabs.getTitleAt(0)).isEqualTo("F");
        assertThat(solutionTabs.getToolTipTextAt(0)).isNull();
    }

    @Test
    void givingUpIsForGoodAndTheMapBecomesReadOnly() {
        model.practice().add(0, SUM_OF_PRODUCTS, group(A | B, B));
        giveUpAnswer = false;
        practicePanel.giveUpButton().doClick();
        assertThat(model.practice().givenUp(0, SUM_OF_PRODUCTS)).isFalse();

        giveUpAnswer = true;
        practicePanel.giveUpButton().doClick();

        assertThat(model.practice().givenUp(0, SUM_OF_PRODUCTS)).isTrue();
        assertThat(practicePanel.solutionMapVisible()).isTrue();
        assertThat(practicePanel.givenUpText()).isEqualTo("Te rendiste en F: este mapa queda solo para mirar.");
        for (var button : List.of(practicePanel.hintButton(), practicePanel.reviewButton(),
                practicePanel.clearAllButton(), practicePanel.giveUpButton())) {
            assertThat(button.isEnabled()).as(button.getText()).isFalse();
            assertThat(button.getToolTipText()).as(button.getText())
                    .isEqualTo("Te rendiste en F: este mapa queda solo para mirar.");
        }

        KarnaughMapView map = panel.mapView(0);
        map.setSize(400, 400);
        assertThat(map.editable()).isFalse();
        assertThat(map.drawnGroups()).as("the student's groups stay drawn").hasSize(1);
        click(map, cellCentre(map, 1, 3));
        assertThat(model.practice().groups(0, SUM_OF_PRODUCTS)).hasSize(1);

        model.setSelectedOutput(1);
        model.setSelectedOutput(0);
        panel.practiceFormButton(PRODUCT_OF_SUMS).doClick();
        panel.practiceFormButton(SUM_OF_PRODUCTS).doClick();
        assertThat(practicePanel.solutionMapVisible()).as("it stays visible").isTrue();
        assertThat(model.practice().givenUp(0, SUM_OF_PRODUCTS)).isTrue();
        assertThat(practicePanel.hintButton().getToolTipText()).isNotNull();

        model.setSelectedOutput(1);
        assertThat(practicePanel.solutionMapVisible()).isFalse();
        assertThat(practicePanel.givenUpText()).isEmpty();
        assertThat(practicePanel.hintButton().getToolTipText()).isNull();
        assertThat(panel.mapView(1).editable()).isTrue();
    }

    @Test
    void leavingStepThreeWithPendingOutputsAsksAndNoStaysOnTheFirstPendingOne() {
        model.practice().add(1, SUM_OF_PRODUCTS, group(A | C, C)); // G: rows 1 and 3, rows 5 and 7 left
        model.setSelectedOutput(2);
        finishF();
        leaveAnswer = false;

        wizard.next();

        assertThat(leaveQuestions).containsExactly("Te falta: G (faltan 2 unos), H (sin grupos). Si sigues, te "
                + "rindes en esas salidas y verás la solución de VSim.");
        assertThat(wizard.step()).isEqualTo(KarnaughWizard.Step.GROUPS);
        assertThat(model.selectedOutput()).isEqualTo(1);
        assertThat(model.practice().givenUp(1, SUM_OF_PRODUCTS)).isFalse();

        wizard.stepButton(KarnaughWizard.Step.CREATE).doClick();
        assertThat(leaveQuestions).hasSize(2);
        assertThat(wizard.step()).isEqualTo(KarnaughWizard.Step.GROUPS);
        assertThat(wizard.stepButton(KarnaughWizard.Step.GROUPS).isSelected()).isTrue();
    }

    @Test
    void seguirIgualGivesUpThePendingOutputsAndStepFourShowsVsimsForThemOnly() {
        finishF();
        model.practice().add(1, SUM_OF_PRODUCTS, group(A | C, C));
        model.setSelectedOutput(1);
        leaveAnswer = true;

        wizard.next();

        assertThat(leaveQuestions).hasSize(1);
        assertThat(wizard.step()).isEqualTo(KarnaughWizard.Step.EXPRESSION);
        assertThat(model.practice().givenUp(0, SUM_OF_PRODUCTS)).isFalse();
        assertThat(model.practice().givenUp(1, SUM_OF_PRODUCTS)).isTrue();
        assertThat(model.practice().givenUp(2, SUM_OF_PRODUCTS)).isTrue();
        assertThat(model.practice().givenUp(1, PRODUCT_OF_SUMS)).as("only the chosen form").isFalse();
        assertThat(wizard.expressionPanel().expressionTexts()).containsExactly("F = A'B + AC", "G = C", "H = A");
        assertThat(model.usingStudentGroups()).isTrue();
        assertThat(model.effectiveMinimization().outputs().get(1).terms()).as("VSim's for G")
                .containsExactly(new Term(C, C));

        wizard.back();
        assertThat(tabTitles()).containsExactly("F ✓", "G ✗", "H ✗");
        wizard.next();
        assertThat(leaveQuestions).as("nothing pending any more").hasSize(1);
    }

    @Test
    void noQuestionWhenEveryOutputIsDoneOrWhenGoingBack() {
        wizard.back();
        assertThat(wizard.step()).isEqualTo(KarnaughWizard.Step.MAP);
        wizard.goTo(KarnaughWizard.Step.GROUPS);
        wizard.goTo(KarnaughWizard.Step.TABLE);
        assertThat(leaveQuestions).isEmpty();

        wizard.goTo(KarnaughWizard.Step.GROUPS);
        finishF();
        model.practice().add(1, SUM_OF_PRODUCTS, group(C, C));
        model.practice().add(2, SUM_OF_PRODUCTS, group(A, A));
        wizard.next();
        assertThat(leaveQuestions).isEmpty();
        assertThat(wizard.step()).isEqualTo(KarnaughWizard.Step.EXPRESSION);
        assertThat(wizard.expressionPanel().expressionTexts()).containsExactly("F = A'B + AC", "G = C", "H = A");
    }

    @Test
    void theSolutionWizardNeverAsks() {
        KarnaughModel solutionModel = new KarnaughModel();
        solutionModel.setValue(0, 1, LogicValue.ONE);
        KarnaughWizard solution = new KarnaughWizard(solutionModel, KarnaughCost.GENERATOR,
                new FakeCreationTarget(), Background.inline());
        solution.setLeaveConfirmation((parent, message) -> {
            leaveQuestions.add(message);
            return false;
        });
        solution.goTo(KarnaughWizard.Step.GROUPS);
        solution.next();
        assertThat(leaveQuestions).isEmpty();
        assertThat(solution.step()).isEqualTo(KarnaughWizard.Step.EXPRESSION);
    }

    @Test
    void aPendingCountSaysOneInTheSingularAndZerosInAProductOfSums() {
        panel.practiceFormButton(PRODUCT_OF_SUMS).doClick();
        // F's 0s are rows 0, 1, 4, 6: draw all but row 6.
        model.practice().add(0, PRODUCT_OF_SUMS, group(A | B, 0));
        model.practice().add(0, PRODUCT_OF_SUMS, group(B | C, 0));
        model.practice().add(1, PRODUCT_OF_SUMS, group(C, 0));
        model.practice().add(2, PRODUCT_OF_SUMS, group(A, 0));
        leaveAnswer = false;
        wizard.next();
        assertThat(leaveQuestions).containsExactly("Te falta: F (falta 1 cero). Si sigues, te rindes en esas "
                + "salidas y verás la solución de VSim.");
    }

    @Test
    void anyTableChangeRestartsThePracticeWithTheNoticeAndRenamesDoNot() {
        finishF();
        model.setSelectedOutput(2);
        practicePanel.giveUpButton().doClick();
        wizard.goTo(KarnaughWizard.Step.TABLE);
        model.setOutputName(1, "Z");
        model.setTitle("Otra");
        wizard.goTo(KarnaughWizard.Step.GROUPS);
        assertThat(practicePanel.noticeText()).isEmpty();
        assertThat(tabTitles()).containsExactly("F ✓", "Z •", "H ✗");

        wizard.goTo(KarnaughWizard.Step.TABLE);
        model.setValue(1, 0, LogicValue.DONT_CARE);
        wizard.goTo(KarnaughWizard.Step.GROUPS);

        assertThat(practicePanel.noticeText()).isEqualTo("La tabla cambió: la práctica empezó de nuevo");
        assertThat(tabTitles()).containsExactly("F •", "Z •", "H •");
        assertThat(practicePanel.solutionMapVisible()).isFalse();
        assertThat(model.practice().groups(0, SUM_OF_PRODUCTS)).isEmpty();

        model.practice().add(2, SUM_OF_PRODUCTS, group(A, A));
        assertThat(practicePanel.noticeText()).isEmpty();
    }

    @Test
    void aGradedSolutionGoesAwayWhenTheTableRestarts() {
        model.setSelectedOutput(2);
        model.practice().add(2, SUM_OF_PRODUCTS, group(A, A));
        practicePanel.reviewButton().doClick();
        assertThat(practicePanel.solutionMapVisible()).isTrue();
        assertThat(practicePanel.reviewText()).isEqualTo("¡Mínima!");

        wizard.goTo(KarnaughWizard.Step.TABLE);
        model.setValue(2, 0, LogicValue.ONE);
        model.setValue(2, 0, LogicValue.ZERO); // the same table again, but the practice restarted
        wizard.goTo(KarnaughWizard.Step.GROUPS);
        assertThat(practicePanel.solutionMapVisible()).isFalse();
        assertThat(practicePanel.reviewText()).isEmpty();
        assertThat(model.practice().revealed(2, SUM_OF_PRODUCTS)).isFalse();
    }

    private static Point cellCentre(KarnaughMapView map, int row, int column) {
        MapGeometry geometry = map.geometry();
        Rectangle grid = geometry.grid();
        int cell = geometry.cellSize();
        return new Point(grid.x + column * cell + cell / 2, grid.y + row * cell + cell / 2);
    }

    private static void click(KarnaughMapView map, Point point) {
        for (int id : new int[] {MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED}) {
            MouseEvent event = new MouseEvent(map, id, System.currentTimeMillis(), 0, point.x, point.y, 1, false,
                    MouseEvent.BUTTON1);
            for (MouseListener listener : map.getMouseListeners()) {
                if (listener.getClass().getName().startsWith("cl.almejo")) {
                    if (id == MouseEvent.MOUSE_PRESSED) {
                        listener.mousePressed(event);
                    } else {
                        listener.mouseReleased(event);
                    }
                }
            }
        }
    }
}
