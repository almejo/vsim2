package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.DrawnGroup;
import cl.almejo.vsim.core.logic.GroupCheck;
import cl.almejo.vsim.core.logic.KarnaughGroup;
import cl.almejo.vsim.core.logic.KarnaughLayout;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.Term;
import cl.almejo.vsim.ui.i18n.Messages;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.core.logic.Minimization.Form.PRODUCT_OF_SUMS;
import static cl.almejo.vsim.core.logic.Minimization.Form.SUM_OF_PRODUCTS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 3 of the practice wizard (spec §2, «Practicar Karnaugh…»): no mode switch, the form, the verdicts, the counter,
 * Pista, Revisar, «Me rindo», Borrar todos.
 */
class GroupsPanelPracticeTest {

    private KarnaughModel model;
    private KarnaughWizard wizard;
    private GroupsPanel panel;
    private PracticePanel practicePanel;
    private final List<Integer> confirmations = new ArrayList<>();
    private boolean confirmAnswer;
    private int giveUpQuestions;
    private boolean giveUpAnswer;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        model = new KarnaughModel();
        wizard = new KarnaughWizard(model, KarnaughCost.GENERATOR, new FakeCreationTarget(), Background.inline(),
                KarnaughWizard.Mode.PRACTICE);
        panel = wizard.groupsPanel();
        practicePanel = panel.practicePanel();
        practicePanel.setClearConfirmation((parent, count, name) -> {
            confirmations.add(count);
            return confirmAnswer;
        });
        practicePanel.setGiveUpConfirmation((parent, outputName) -> {
            giveUpQuestions++;
            return giveUpAnswer;
        });
    }

    /** F(A, B, C) with 1 on {@code ones}, then step 3 in practice mode. */
    private void practise(int... ones) {
        for (int row : ones) {
            model.setValue(0, row, LogicValue.ONE);
        }
        wizard.goTo(KarnaughWizard.Step.GROUPS);
    }

    /** Every button text in {@code root}'s tree. */
    private static List<String> buttonTexts(Container root) {
        List<String> texts = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (child instanceof AbstractButton button) {
                texts.add(button.getText());
            }
            if (child instanceof Container container) {
                texts.addAll(buttonTexts(container));
            }
        }
        return texts;
    }

    private DrawnGroup group(long mask, long value) {
        KarnaughGroup group = KarnaughLayout.of(model.inputCount()).group(new Term(mask, value));
        return new DrawnGroup(group.cells(), group.rectangles());
    }

    private void add(Minimization.Form form, DrawnGroup group) {
        model.practice().add(0, form, group);
    }

    private static void press(JComponent component) {
        MouseEvent event = new MouseEvent(component, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 2, 2, 1,
                false, MouseEvent.BUTTON1);
        for (MouseListener listener : component.getMouseListeners()) {
            if (listener.getClass().getName().startsWith("cl.almejo")) {
                listener.mousePressed(event);
            }
        }
    }

    // A is 0b100, B 0b010, C 0b001.
    private static final long C = 0b001;
    private static final long A = 0b100;
    private static final long B = 0b010;

    @Test
    void theSolutionWizardShowsStepThreeAsBeforeThePracticeWithNoModeControl() {
        KarnaughModel solutionModel = new KarnaughModel();
        KarnaughWizard solution = new KarnaughWizard(solutionModel, KarnaughCost.GENERATOR,
                new FakeCreationTarget(), Background.inline());
        solutionModel.setValue(0, 1, LogicValue.ONE);
        solution.goTo(KarnaughWizard.Step.GROUPS);
        GroupsPanel groups = solution.groupsPanel();
        assertThat(solution.mode()).isEqualTo(KarnaughWizard.Mode.SOLUTION);
        assertThat(solutionModel.practice().practising()).isFalse();
        assertThat(groups.practicePanelVisible()).isFalse();
        assertThat(groups.practiceFormVisible()).isFalse();
        assertThat(groups.solutionSideVisible()).isTrue();
        assertThat(groups.headerText()).isEqualTo(Messages.text("karnaugh.groups.header.sop"));
        assertThat(groups.legendTexts()).containsExactly("① A'B'C");
        assertThat(buttonTexts(solution)).doesNotContain("Practicar", "Ver la solución", "Me rindo · Ver la solución");
    }

    @Test
    void thePracticeWizardShowsOnlyThePracticeWithNoModeControlAndKeepsTheGroupsAcrossSteps() {
        practise(1, 3, 5, 7);
        assertThat(wizard.mode()).isEqualTo(KarnaughWizard.Mode.PRACTICE);
        assertThat(model.practice().practising()).isTrue();
        assertThat(panel.practicePanelVisible()).isTrue();
        assertThat(panel.practiceFormVisible()).isTrue();
        assertThat(panel.solutionSideVisible()).isFalse();
        assertThat(buttonTexts(wizard)).doesNotContain("Practicar", "Ver la solución");
        add(SUM_OF_PRODUCTS, group(C, C));
        assertThat(practicePanel.legendTexts()).containsExactly("① C");
        wizard.goTo(KarnaughWizard.Step.TABLE);
        wizard.goTo(KarnaughWizard.Step.GROUPS);
        assertThat(practicePanel.legendTexts()).containsExactly("① C");
        assertThat(model.practice().practising()).isTrue();
    }

    @Test
    void theFormCanBeChosenInStepThreeAndEachFormKeepsItsGroups() {
        practise(1, 3, 5, 7);
        assertThat(practicePanel.headerText()).startsWith("Agrupa los 1:");
        add(SUM_OF_PRODUCTS, group(C, C));
        panel.practiceFormButton(PRODUCT_OF_SUMS).doClick();
        assertThat(model.options().form()).isEqualTo(PRODUCT_OF_SUMS);
        assertThat(practicePanel.headerText()).startsWith("Agrupa los 0:");
        assertThat(practicePanel.legendTexts()).isEmpty();
        add(PRODUCT_OF_SUMS, group(C, 0));
        assertThat(practicePanel.legendTexts()).containsExactly("① C");
        panel.practiceFormButton(SUM_OF_PRODUCTS).doClick();
        assertThat(panel.practiceFormButton(SUM_OF_PRODUCTS).isSelected()).isTrue();
        assertThat(practicePanel.legendTexts()).containsExactly("① C");
    }

    @Test
    void eachVerdictHasItsTextAndColour() {
        practise(1, 3, 5, 7);
        KarnaughLayout layout = KarnaughLayout.of(3);
        add(SUM_OF_PRODUCTS, layout.drawn(0, 0, 1, 3)); // 3 cells
        add(SUM_OF_PRODUCTS, layout.drawn(0, 0, 1, 2)); // A'B': rows 0 and 1, row 0 is a 0
        add(SUM_OF_PRODUCTS, group(A | C, C)); // A'C fits in C
        add(SUM_OF_PRODUCTS, group(C, C)); // C: A'C's ones are in it
        assertThat(practicePanel.legendTexts()).containsExactly(
                "① 3 celdas: un grupo tiene 1, 2, 4, 8 o 16",
                "② incluye un 0",
                "③ sobra: sus unos ya están en otros grupos",
                "④ C");
        assertThat(practicePanel.legendColor(0)).isEqualTo(WizardColors.failure());
        assertThat(practicePanel.legendColor(2)).isEqualTo(WizardColors.warning());
        assertThat(practicePanel.legendColor(3)).isEqualTo(WizardColors.success());
        model.practice().remove(0, SUM_OF_PRODUCTS, 3);
        assertThat(practicePanel.legendTexts().get(2)).isEqualTo("③ puede crecer: también cabe con C");
    }

    @Test
    void theProductOfSumsSpeaksOfZerosAndOnes() {
        practise(1, 3, 5, 7);
        panel.practiceFormButton(PRODUCT_OF_SUMS).doClick();
        add(PRODUCT_OF_SUMS, KarnaughLayout.of(3).drawn(0, 0, 1, 2)); // rows 0 and 1, row 1 is a 1
        add(PRODUCT_OF_SUMS, group(C, 0));
        add(PRODUCT_OF_SUMS, group(A | C, 0));
        assertThat(practicePanel.legendTexts()).containsExactly("① incluye un 1", "② C",
                "③ sobra: sus ceros ya están en otros grupos");
    }

    @Test
    void notARectangleHasItsOwnText() {
        GroupCheck.Result result = new GroupCheck.Result(GroupCheck.Verdict.NOT_RECTANGLE, Optional.empty(),
                Optional.empty());
        assertThat(PracticePanel.verdictText(result, 2, List.of("A", "B", "C"), SUM_OF_PRODUCTS))
                .isEqualTo("no es un rectángulo");
    }

    @Test
    void theCounterSaysWhatIsLeftInBothForms() {
        practise(1, 3, 5, 7);
        assertThat(practicePanel.counterText()).isEqualTo("Faltan 4 unos por agrupar");
        add(SUM_OF_PRODUCTS, group(A | C, C));
        assertThat(practicePanel.counterText()).isEqualTo("Faltan 2 unos por agrupar");
        add(SUM_OF_PRODUCTS, group(A | B | C, A | C));
        assertThat(practicePanel.counterText()).isEqualTo("Falta 1 uno por agrupar");
        add(SUM_OF_PRODUCTS, group(C, C));
        assertThat(practicePanel.counterText()).isEqualTo("¡Todos los unos están agrupados!");
        panel.practiceFormButton(PRODUCT_OF_SUMS).doClick();
        assertThat(practicePanel.counterText()).isEqualTo("Faltan 4 ceros por agrupar");
        add(PRODUCT_OF_SUMS, group(A | B | C, 0));
        assertThat(practicePanel.counterText()).isEqualTo("Faltan 3 ceros por agrupar");
        add(PRODUCT_OF_SUMS, group(C, 0));
        assertThat(practicePanel.counterText()).isEqualTo("¡Todos los ceros están agrupados!");
    }

    @Test
    void aConstantOutputHasNothingToGroupButCanBePractised() {
        practise();
        assertThat(practicePanel.specialCaseText()).isEqualTo("F = 0: no hay unos que agrupar.");
        assertThat(practicePanel.counterText()).isEqualTo("No hay unos que agrupar: no hace falta ningún grupo.");
        assertThat(practicePanel.hintButton().isEnabled()).isFalse();
        add(SUM_OF_PRODUCTS, group(C, C));
        assertThat(practicePanel.legendTexts()).containsExactly("① incluye un 0");
    }

    @Test
    void pistaMarksACellThenShowsAGroupAndCountsTheHints() {
        practise(1, 3, 5, 7);
        assertThat(practicePanel.hintText()).isEmpty();
        assertThat(practicePanel.hintsUsedText()).isEmpty();
        practicePanel.hintButton().doClick();
        assertThat(practicePanel.hintText())
                .isEqualTo("Esta celda todavía no está en ningún grupo: ¿con qué vecinas puede ir?");
        assertThat(practicePanel.hintsUsedText()).isEqualTo("1 pista usada");
        practicePanel.hintButton().doClick();
        assertThat(practicePanel.hintText()).isEqualTo(Messages.text("karnaugh.practice.hint.group"));
        assertThat(model.practice().hintGroup()).contains(new Term(C, C));
        assertThat(practicePanel.hintsUsedText()).isEqualTo("2 pistas usadas");
        add(SUM_OF_PRODUCTS, group(C, C));
        assertThat(practicePanel.hintText()).isEmpty();
        assertThat(practicePanel.hintsUsedText()).isEqualTo("2 pistas usadas");
        assertThat(practicePanel.hintButton().isEnabled()).isFalse();
    }

    @Test
    void revisarSaysMinimal() {
        practise(1, 3, 5, 7);
        assertThat(practicePanel.reviewButton().isEnabled()).isFalse();
        add(SUM_OF_PRODUCTS, group(C, C));
        assertThat(practicePanel.reviewButton().isEnabled()).isTrue();
        assertThat(practicePanel.solutionMapVisible()).isFalse();
        practicePanel.reviewButton().doClick();
        assertThat(practicePanel.reviewText()).isEqualTo("¡Mínima!");
        assertThat(practicePanel.reviewColor()).isEqualTo(WizardColors.success());
        assertThat(practicePanel.solutionMapVisible()).isTrue();
        assertThat(practicePanel.solutionTitleText()).isEqualTo("Solución de VSim");
        assertThat(practicePanel.solutionMap().practising()).isFalse();
        assertThat(practicePanel.solutionMap().groups()).hasSize(1);
        add(SUM_OF_PRODUCTS, group(A | C, C));
        assertThat(practicePanel.reviewText()).isEmpty();
        assertThat(practicePanel.solutionMapVisible()).isFalse();
    }

    @Test
    void revisarSaysCorrectButNotMinimalWithTheCounts() {
        practise(2, 3, 5, 7); // A'B + AC
        add(SUM_OF_PRODUCTS, group(A | B, B));
        add(SUM_OF_PRODUCTS, group(A | C, A | C));
        add(SUM_OF_PRODUCTS, group(B | C, B | C));
        practicePanel.reviewButton().doClick();
        assertThat(practicePanel.reviewText())
                .isEqualTo("Correcta pero no mínima: VSim usa 2 grupos y 4 literales, tú 3 y 6");
        assertThat(practicePanel.reviewColor()).isEqualTo(WizardColors.warning());
    }

    @Test
    void revisarSaysNotEquivalentWithTheFirstDifferingRow() {
        practise(2, 3, 5, 7);
        add(SUM_OF_PRODUCTS, group(A | B, B));
        practicePanel.reviewButton().doClick();
        assertThat(practicePanel.reviewText())
                .isEqualTo("No es equivalente: la fila 5 (A=1, B=0, C=1) da 0 y debería dar 1");
        assertThat(practicePanel.reviewColor()).isEqualTo(WizardColors.failure());
    }

    @Test
    void revisarIsOffWhileAGroupIsRed() {
        practise(1, 3, 5, 7);
        add(SUM_OF_PRODUCTS, group(C, C));
        add(SUM_OF_PRODUCTS, KarnaughLayout.of(3).drawn(0, 0, 1, 3));
        assertThat(practicePanel.reviewButton().isEnabled()).isFalse();
    }

    @Test
    void nothingShowsVsimsSolutionBeforeRevisarOrMeRindo() {
        practise(1, 3, 5, 7);
        add(SUM_OF_PRODUCTS, group(A | C, C));
        assertThat(practicePanel.solutionMapVisible()).isFalse();
        assertThat(practicePanel.reviewText()).isEmpty();
        assertThat(model.practice().revealed(0, SUM_OF_PRODUCTS)).isFalse();
        assertThat(practicePanel.giveUpButton().getText()).isEqualTo("Me rindo · Ver la solución");
        assertThat(practicePanel.giveUpButton().isEnabled()).isTrue();
    }

    @Test
    void meRindoAsksFirstAndNoKeepsPractising() {
        practise(1, 3, 5, 7);
        giveUpAnswer = false;
        practicePanel.giveUpButton().doClick();
        assertThat(giveUpQuestions).isEqualTo(1);
        assertThat(practicePanel.solutionMapVisible()).isFalse();
        assertThat(model.practice().revealed(0, SUM_OF_PRODUCTS)).isFalse();
    }

    @Test
    void meRindoShowsVsimsMapWithoutGradingUntilTheGroupsOutputOrFormChange() {
        practise(1, 3, 5, 7);
        giveUpAnswer = true;
        practicePanel.giveUpButton().doClick();
        assertThat(giveUpQuestions).isEqualTo(1);
        assertThat(model.practice().practising()).as("the mode stays").isTrue();
        assertThat(panel.practicePanelVisible()).isTrue();
        assertThat(practicePanel.solutionMapVisible()).isTrue();
        assertThat(practicePanel.solutionTitleText()).isEqualTo("Solución de VSim");
        assertThat(practicePanel.solutionMap().groups()).hasSize(1);
        assertThat(practicePanel.reviewText()).as("nothing was graded").isEmpty();
        assertThat(practicePanel.giveUpButton().isEnabled()).as("already shown").isFalse();
        assertThat(model.practice().revealed(0, SUM_OF_PRODUCTS)).isTrue();
        assertThat(model.practice().revealed(0, PRODUCT_OF_SUMS)).isFalse();

        add(SUM_OF_PRODUCTS, group(C, C));
        assertThat(practicePanel.solutionMapVisible()).isFalse();
        assertThat(practicePanel.giveUpButton().isEnabled()).isTrue();
        assertThat(model.practice().revealed(0, SUM_OF_PRODUCTS)).as("seen once, revealed for good").isTrue();

        practicePanel.giveUpButton().doClick();
        assertThat(practicePanel.solutionMapVisible()).isTrue();
        panel.practiceFormButton(PRODUCT_OF_SUMS).doClick();
        assertThat(practicePanel.solutionMapVisible()).isFalse();
    }

    @Test
    void revisarRevealsTheOutputToo() {
        practise(1, 3, 5, 7);
        add(SUM_OF_PRODUCTS, group(C, C));
        practicePanel.reviewButton().doClick();
        assertThat(model.practice().revealed(0, SUM_OF_PRODUCTS)).isTrue();
        assertThat(giveUpQuestions).isZero();
    }

    @Test
    void borrarTodosAsksOnlyWithThreeOrMoreGroups() {
        practise(1, 3, 5, 7);
        assertThat(practicePanel.clearAllButton().isEnabled()).isFalse();
        add(SUM_OF_PRODUCTS, group(C, C));
        add(SUM_OF_PRODUCTS, group(A | C, C));
        practicePanel.clearAllButton().doClick();
        assertThat(confirmations).isEmpty();
        assertThat(model.practice().groups(0, SUM_OF_PRODUCTS)).isEmpty();
        add(SUM_OF_PRODUCTS, group(C, C));
        add(SUM_OF_PRODUCTS, group(A | C, C));
        add(SUM_OF_PRODUCTS, group(A | C, A | C));
        confirmAnswer = false;
        practicePanel.clearAllButton().doClick();
        assertThat(confirmations).containsExactly(3);
        assertThat(model.practice().groups(0, SUM_OF_PRODUCTS)).hasSize(3);
        confirmAnswer = true;
        practicePanel.clearAllButton().doClick();
        assertThat(model.practice().groups(0, SUM_OF_PRODUCTS)).isEmpty();
    }

    @Test
    void clickingALegendLineSelectsItsGroup() {
        practise(1, 3, 5, 7);
        add(SUM_OF_PRODUCTS, group(C, C));
        add(SUM_OF_PRODUCTS, group(A | C, C));
        press(practicePanel.legendEntry(1));
        assertThat(model.practice().selected()).hasValue(1);
        assertThat(practicePanel.legendSelected(1)).isTrue();
        press(practicePanel.legendEntry(0));
        assertThat(model.practice().selected()).hasValue(0);
    }

    @Test
    void aTableChangeShowsTheNoticeUntilTheNextEdit() {
        practise(1, 3, 5, 7);
        add(SUM_OF_PRODUCTS, group(C, C));
        assertThat(practicePanel.noticeText()).isEmpty();
        wizard.goTo(KarnaughWizard.Step.TABLE);
        model.setValue(0, 0, LogicValue.ONE);
        wizard.goTo(KarnaughWizard.Step.GROUPS);
        assertThat(practicePanel.noticeText()).isEqualTo("La tabla cambió: se borraron tus grupos de F");
        assertThat(practicePanel.legendTexts()).isEmpty();
        add(SUM_OF_PRODUCTS, group(C, C));
        assertThat(practicePanel.noticeText()).isEmpty();
    }

    @Test
    void sixteenGroupsSayWhyNoMoreCanBeDrawn() {
        model.setInputCount(4);
        practise(1);
        KarnaughLayout layout = KarnaughLayout.of(4);
        for (int index = 0; index < PracticeModel.MAXIMUM_GROUPS - 1; index++) {
            add(SUM_OF_PRODUCTS, layout.drawn(index / 4, index % 4, 1, 1));
        }
        assertThat(practicePanel.fullText()).isEmpty();
        add(SUM_OF_PRODUCTS, layout.drawn(3, 3, 1, 1));
        assertThat(practicePanel.fullText()).isEqualTo("Ya hay 16 grupos: borra uno para dibujar otro");
    }

    @Test
    void theVerdictColoursAreReadableOnBothThemes() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            for (LookAndFeel theme : List.of(new FlatLightLaf(), new FlatDarkLaf())) {
                UIManager.setLookAndFeel(theme);
                for (Color color : List.of(WizardColors.failure(), WizardColors.warning(),
                        WizardColors.success())) {
                    assertThat(WizardColors.contrast(color, WizardColors.background())).as(theme.getName())
                            .isGreaterThanOrEqualTo(4.5);
                }
            }
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }
}
