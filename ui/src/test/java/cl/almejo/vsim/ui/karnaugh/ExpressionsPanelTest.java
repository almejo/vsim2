package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.logic.CircuitGenerator;
import cl.almejo.vsim.core.logic.ExpressionLine;
import cl.almejo.vsim.core.logic.GateCost;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.TableCircuits;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ExpressionsPanelTest {

    private static final String GATE = "(and|or|xor|nand|nor)\\d+|not";

    private final FakeCreationTarget target = new FakeCreationTarget();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<TruthTableSpec> wizardTables = new ArrayList<>();
    private final List<Runnable> queued = new ArrayList<>();
    private ExpressionsPanel panel;
    private int closed;
    private int discardQuestions;
    private boolean discardAnswer;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        open(Background.inline());
    }

    private void open(Background background) {
        panel = new ExpressionsPanel(target, background, preferences, KarnaughCost.GENERATOR, (owner, spec) -> wizardTables.add(spec));
        panel.setOnClose(() -> closed++);
        panel.setDiscardConfirmation(parent -> {
            discardQuestions++;
            return discardAnswer;
        });
    }

    private void type(int line, String text) {
        panel.lineField(line).setText(text);
    }

    @Test
    void typingParsesTheLineListsTheInputsAndPreviewsTheSimplifiedExpression() {
        assertThat(panel.lineField(0).getText()).isEqualTo("F = ");
        assertThat(panel.problemText(0)).as("a fresh dialog shows no error").isEmpty();
        type(0, "F = A");
        type(0, "F = ");
        assertThat(panel.problemText(0)).isEqualTo("Falta la expresión a la derecha del «=» (posición 4)");

        type(0, "F = A B + A B'");

        assertThat(panel.problemText(0)).isEmpty();
        assertThat(panel.inputsText()).isEqualTo("Entradas: A, B (2)");
        assertThat(panel.previewText(0)).isEqualTo("→ F = A");
        assertThat(panel.costText()).isEqualTo("Sin compuertas: cada salida es una entrada o una constante");
        assertThat(panel.createButton().isEnabled()).isTrue();

        type(0, "F = A'B + C");
        assertThat(panel.previewText(0)).isEqualTo("ya está simplificada");
        assertThat(panel.costText()).isEqualTo("1 AND, 1 OR, 1 NOT (3 compuertas)");
    }

    @Test
    void aProblemTurnsCreateOffAndHidesThePreview() {
        type(0, "F = A + (B");
        assertThat(panel.problemText(0)).isEqualTo("Falta cerrar un paréntesis (posición 9)");
        assertThat(panel.previewText(0)).isEmpty();
        assertThat(panel.createButton().isEnabled()).isFalse();
    }

    @Test
    void aLatePreviewOfAnOlderTextIsDropped() {
        open(new Background(queued::add, Runnable::run));
        type(0, "F = A B + A B'");
        assertThat(panel.previewText(0)).isEqualTo("calculando…");
        type(0, "F = A + B");
        assertThat(queued).hasSize(2);

        queued.get(1).run();
        assertThat(panel.previewText(0)).isEqualTo("ya está simplificada");
        queued.get(0).run();
        assertThat(panel.previewText(0)).isEqualTo("ya está simplificada");
    }

    @Test
    void aPreviewAlreadyStaleWhenTheWorkerReachesItIsNotComputed() {
        List<Minimization> costed = new ArrayList<>();
        panel = new ExpressionsPanel(target, new Background(queued::add, Runnable::run), preferences,
                (minimization, options) -> {
                    costed.add(minimization);
                    return KarnaughCost.GENERATOR.gatesByType(minimization, options);
                }, (owner, spec) -> wizardTables.add(spec));
        type(0, "F = A B + A B'");
        type(0, "F = A + B");

        queued.get(0).run();
        assertThat(costed).isEmpty();
        assertThat(panel.previewText(0)).isEqualTo("calculando…");
        queued.get(1).run();
        assertThat(costed).hasSize(1);
        assertThat(panel.previewText(0)).isEqualTo("ya está simplificada");
    }

    @Test
    void withoutSimplifyingTheGateOptionsAreOffAndTheCostIsAsWritten() {
        type(0, "F = A ^ B");
        assertThat(panel.costText()).isEqualTo("2 AND, 1 OR, 2 NOT (5 compuertas)");
        assertThat(panel.formReasonShown()).isFalse();

        panel.simplifyBox().doClick();

        assertThat(preferences.simplifyExpressions()).isFalse();
        assertThat(panel.gateButton(WizardOptions.GateChoice.NAND_ONLY).isEnabled()).isFalse();
        assertThat(panel.formReasonShown()).isTrue();
        assertThat(panel.shareBox().isEnabled()).isTrue();
        assertThat(panel.costText()).isEqualTo("1 XOR (1 compuerta)");
        assertThat(panel.previewText(0)).isEmpty();
        assertThat(panel.wizardLink().isVisible()).as("seeing the map does not depend on simplifying").isTrue();

        panel.simplifyBox().doClick();
        assertThat(preferences.simplifyExpressions()).isTrue();
        assertThat(panel.gateButton(WizardOptions.GateChoice.NAND_ONLY).isEnabled()).isTrue();
    }

    @Test
    void thePreferenceDecidesWhetherANewDialogSimplifies() {
        preferences.setSimplifyExpressions(false);
        open(Background.inline());
        assertThat(panel.simplifyBox().isSelected()).isFalse();
    }

    @Test
    void createWithoutSimplifyingBuildsTheCircuitAsWritten() {
        preferences.setSimplifyExpressions(false);
        open(Background.inline());
        type(0, "F = A B C D E + (A ^ B ^ C)'");
        panel.addLineButton().doClick();
        type(1, "G = !A' + 1");
        List<ExpressionLine> lines = panel.model().expressionLines().orElseThrow();
        TruthTableSpec spec = panel.model().spec().orElseThrow();
        GateCost cost = CircuitGenerator.costAsWritten(spec, lines, true);

        panel.createButton().doClick();

        assertThat(target.newCircuits).singleElement().satisfies(created -> {
            assertThat(created.name()).isEqualTo("Expresiones");
            long gates = created.fragment().components().stream().map(Component::typeId)
                    .filter(type -> type.matches(GATE)).count();
            assertThat(gates).isEqualTo(cost.gateCount());
            assertThat(created.fragment().components().stream().map(Component::typeId)).contains("xor3");
            assertThat(TableCircuits.check(spec, created.fragment(), StandardComponentTypes.registry()).passed())
                    .isTrue();
            assertThat(created.notice()).isEqualTo("✓ El circuito cumple la tabla.");
        });
        assertThat(panel.resultText()).isEqualTo("✓ El circuito cumple la tabla.");
        assertThat(closed).as("the work is done: the dialog closes").isEqualTo(1);
        assertThat(discardQuestions).isZero();
    }

    @Test
    void createWhileSimplifyingBuildsTheMinimizedCircuit() {
        type(0, "F = A B + A B'");
        panel.createButton().doClick();
        assertThat(target.newCircuits).singleElement().satisfies(created ->
                assertThat(created.fragment().components().stream().map(Component::typeId))
                        .noneMatch(type -> type.startsWith("and") || type.startsWith("or")));
    }

    @Test
    void aLegendRowInsertsItsOperatorAtTheCaretOfTheLastFocusedLine() {
        type(0, "F = A B");
        panel.addLineButton().doClick();
        type(1, "G = AB");
        focus(panel.lineField(0));
        panel.lineField(0).setCaretPosition(5);

        panel.legend().rowButton(ExpressionLegend.Row.OR).doClick();
        assertThat(panel.lineField(0).getText()).isEqualTo("F = A+ B");

        focus(panel.lineField(1));
        panel.lineField(1).setCaretPosition(5);
        panel.legend().rowButton(ExpressionLegend.Row.AND).doClick();
        assertThat(panel.lineField(1).getText()).isEqualTo("G = A·B");

        panel.lineField(1).setCaretPosition(panel.lineField(1).getText().length());
        panel.legend().rowButton(ExpressionLegend.Row.PARENTHESES).doClick();
        assertThat(panel.lineField(1).getText()).isEqualTo("G = A·B()");
        assertThat(panel.lineField(1).getCaretPosition()).isEqualTo(8);
    }

    @Test
    void aLegendConstantDoesNotGlueOntoTheNameBeforeIt() {
        type(0, "F = A");
        focus(panel.lineField(0));
        panel.lineField(0).setCaretPosition(5);
        panel.legend().rowButton(ExpressionLegend.Row.ONE).doClick();
        assertThat(panel.lineField(0).getText()).isEqualTo("F = A 1");
        assertThat(panel.lineField(0).getCaretPosition()).isEqualTo(7);

        type(0, "F = A + ");
        panel.lineField(0).setCaretPosition(8);
        panel.legend().rowButton(ExpressionLegend.Row.ZERO).doClick();
        assertThat(panel.lineField(0).getText()).isEqualTo("F = A + 0");
    }

    @Test
    void theLegendKeepsTypingIntoTheSameLineWhenAnEarlierOneIsRemoved() {
        type(0, "F = A");
        panel.addLineButton().doClick();
        type(1, "G = B");
        panel.addLineButton().doClick();
        type(2, "H = C");
        focus(panel.lineField(2));
        panel.lineField(2).setCaretPosition(5);

        panel.removeLineButton(0).doClick();
        panel.legend().rowButton(ExpressionLegend.Row.NOT).doClick();

        assertThat(panel.lineField(0).getText()).isEqualTo("G = B");
        assertThat(panel.lineField(1).getText()).isEqualTo("H = C'");
    }

    @Test
    void enterInALineGoesToTheNextLineAddingOneAtTheEndInsteadOfCreating() {
        type(0, "F = A B");
        panel.lineField(0).postActionEvent();
        assertThat(panel.lineCount()).isEqualTo(2);
        assertThat(target.newCircuits).isEmpty();

        type(1, "G = A");
        focus(panel.lineField(0));
        panel.lineField(0).postActionEvent();
        assertThat(panel.lineCount()).as("not the last line: no new one").isEqualTo(2);
        panel.legend().rowButton(ExpressionLegend.Row.NOT).doClick();
        assertThat(panel.lineField(1).getText()).as("the next line is the one typed into").endsWith("'");
    }

    @Test
    void theLineFontFollowsTheThemesTextFont() {
        Font original = UIManager.getFont("TextField.font");
        try {
            UIManager.put("TextField.font", new FontUIResource(Font.SANS_SERIF, Font.PLAIN, 20));
            panel.lineField(0).updateUI();
            assertThat(panel.lineField(0).getFont().getName()).isEqualTo(Font.MONOSPACED);
            assertThat(panel.lineField(0).getFont().getSize()).isEqualTo(24);
        } finally {
            UIManager.put("TextField.font", original);
        }
    }

    @Test
    void theLegendIsOpenAndCanBeCollapsed() {
        assertThat(panel.legend().isExpanded()).isTrue();
        panel.legend().toggleButton().doClick();
        assertThat(panel.legend().isExpanded()).isFalse();
        panel.legend().toggleButton().doClick();
        assertThat(panel.legend().isExpanded()).isTrue();
    }

    @Test
    void mergedLettersShowAWarningThatCanBeDismissed() {
        type(0, "F = AB + C");
        assertThat(panel.warningText(0)).isEqualTo("¿Quisiste decir A·B? Sepáralas con espacio o *");
        assertThat(panel.createButton().isEnabled()).isTrue();
        panel.dismissWarningButton(0).doClick();
        assertThat(panel.warningText(0)).isEmpty();
    }

    @Test
    void linesAreAddedAndRemoved() {
        panel.addLineButton().doClick();
        assertThat(panel.lineCount()).isEqualTo(2);
        assertThat(panel.lineField(1).getText()).isEqualTo("G = ");
        assertThat(panel.removeLineButton(0).isEnabled()).isTrue();
        panel.removeLineButton(0).doClick();
        assertThat(panel.lineCount()).isEqualTo(1);
        assertThat(panel.lineField(0).getText()).isEqualTo("G = ");
        assertThat(panel.removeLineButton(0).isEnabled()).isFalse();
    }

    @Test
    void tableProblemsAreShownOnce() {
        type(0, "F = A");
        panel.addLineButton().doClick();
        type(1, "F = B");
        assertThat(panel.tableProblemsText()).isEqualTo("Hay más de una salida llamada «F».");
        assertThat(panel.createButton().isEnabled()).isFalse();
    }

    @Test
    void seeInKarnaughNeedsTwoToFourInputsAndOpensTheWizardWithTheTable() {
        type(0, "F = A");
        assertThat(panel.wizardLink().isVisible()).isTrue();
        assertThat(panel.buttonBarTexts()).as("a button at the bottom, not an option of «Simplificar»")
                .containsExactly("Ver en Karnaugh…", "Crear", "Cerrar");
        assertThat(panel.wizardLink().isEnabled()).isFalse();
        assertThat(panel.wizardReasonText()).isEqualTo("El asistente funciona con 2 a 4 entradas y hasta 4 salidas");

        panel.titleField().setText("Mi tabla");
        type(0, "F = A + B C");
        assertThat(panel.wizardLink().isEnabled()).isTrue();
        assertThat(panel.wizardReasonText()).isEmpty();

        panel.wizardLink().doClick();

        assertThat(closed).as("the expressions stay open under the wizard").isZero();
        assertThat(discardQuestions).isZero();
        assertThat(wizardTables).singleElement().satisfies(spec -> {
            assertThat(spec.title()).isEqualTo("Mi tabla");
            assertThat(spec.inputNames()).containsExactly("A", "B", "C");
        });
    }

    @Test
    void closingAsksOnlyWhenTheLinesWereEdited() {
        panel.requestClose();
        assertThat(discardQuestions).isZero();
        assertThat(closed).isEqualTo(1);

        type(0, "F = A");
        discardAnswer = false;
        panel.requestClose();
        assertThat(discardQuestions).isEqualTo(1);
        assertThat(closed).isEqualTo(1);

        discardAnswer = true;
        panel.requestClose();
        assertThat(closed).isEqualTo(2);
    }

    @Test
    void afterCreatingClosingDoesNotAsk() {
        type(0, "F = A + B");
        panel.createButton().doClick();
        assertThat(closed).isEqualTo(1);
        panel.requestClose();
        assertThat(discardQuestions).isZero();
        assertThat(closed).isEqualTo(2);
    }

    @Test
    void theLinesFillTheLeftSideWithoutAHorizontalScrollbarAndShrinkWithTheDialog() {
        for (int index = 0; index < 5; index++) {
            panel.addLineButton().doClick();
        }
        for (int index = 0; index < 6; index++) {
            type(index, "F" + index + " = A B + C' D ^ (E + F)");
        }
        panel.setSize(panel.dialogSize().width, 660);
        layOut(panel);
        JScrollPane scroll = panel.contentScroll();
        assertThat(scroll.getVerticalScrollBar().isVisible()).as("enough lines to scroll").isTrue();
        assertThat(scroll.getHorizontalScrollBar().isVisible()).isFalse();
        int wide = panel.lineField(0).getWidth();
        assertThat(rightEdgeInViewport(scroll, panel.lineField(0))).isLessThanOrEqualTo(scroll.getViewport().getWidth());
        assertThat(wide).as("the line takes the room there is").isGreaterThan(scroll.getViewport().getWidth() / 2);

        panel.setSize(900, 660);
        layOut(panel);
        assertThat(scroll.getHorizontalScrollBar().isVisible()).isFalse();
        assertThat(panel.lineField(0).getWidth()).as("it shrinks with the dialog").isLessThan(wide);
        assertThat(rightEdgeInViewport(scroll, panel.lineField(0))).isLessThanOrEqualTo(scroll.getViewport().getWidth());

        panel.setSize(panel.dialogSize().width + 200, 660);
        layOut(panel);
        assertThat(panel.lineField(0).getWidth()).as("and grows again").isGreaterThan(wide);
        assertThat(scroll.getHorizontalScrollBar().isVisible()).isFalse();
    }

    @Test
    void theLegendExamplesAreLabelsOfTheirOwnSoAnExpressionIsNeverBrokenInTwo() {
        assertThat(panel.legend().example(ExpressionLegend.Row.NOT).getText()).isEqualTo("Ejemplo: (A + B)'");
        assertThat(panel.legend().example(ExpressionLegend.Row.AND).getText()).isEqualTo("Ejemplo: A'B, A(B + C)");
        assertThat(panel.legend().example(ExpressionLegend.Row.NAMES)).isNull();
    }

    @Test
    void theLegendIsWideEnoughForEachRowOnOneLineAndGivesThatWidthBackWhenCollapsed() {
        for (Locale language : List.of(Messages.SPANISH, Locale.ENGLISH)) {
            Messages.initialize(language);
            open(Background.inline());
            Dimension size = panel.dialogSize();
            assertThat(size.width).isGreaterThanOrEqualTo(1040);
            panel.setSize(size.width, 660);
            layOut(panel);
            for (ExpressionLegend.Row row : ExpressionLegend.Row.values()) {
                if (row != ExpressionLegend.Row.NAMES) {
                    Paragraph detail = panel.legend().detail(row);
                    int lineHeight = detail.getFontMetrics(detail.getFont()).getHeight();
                    assertThat(detail.getHeight()).as(language + " " + row + " on one line")
                            .isLessThan(2 * lineHeight);
                }
            }
            assertThat(panel.legendScroll().getHorizontalScrollBar().isVisible()).isFalse();
            assertThat(panel.contentScroll().getWidth()).as("the lines keep their room").isGreaterThanOrEqualTo(600);

            int left = panel.contentScroll().getWidth();
            panel.legend().toggleButton().doClick();
            layOut(panel);
            assertThat(panel.contentScroll().getWidth()).as("collapsed, the lines get the width back")
                    .isGreaterThan(left + 200);

            panel.legend().toggleButton().doClick();
            panel.setSize(900, 660);
            layOut(panel);
            assertThat(panel.contentScroll().getWidth()).as("a narrow dialog keeps the lines' room")
                    .isGreaterThanOrEqualTo(500);
            assertThat(panel.legendScroll().getHorizontalScrollBar().isVisible()).isFalse();
            assertThat(panel.contentScroll().getHorizontalScrollBar().isVisible()).isFalse();
        }
    }

    @Test
    void theLegendIsASoftSidePanelWithReadableMutedExamplesInBothThemes() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            for (LookAndFeel theme : List.of(new FlatLightLaf(), new FlatDarkLaf())) {
                UIManager.setLookAndFeel(theme);
                Color side = WizardColors.sidePanel();
                assertThat(side).as(theme.getName()).isNotEqualTo(WizardColors.background());
                assertThat(WizardColors.contrast(WizardColors.foreground(), side)).as(theme.getName())
                        .isGreaterThanOrEqualTo(4.5);
                Color muted = WizardColors.sidePanelMuted();
                assertThat(muted).as(theme.getName()).isNotEqualTo(WizardColors.foreground());
                assertThat(WizardColors.contrast(muted, side)).as(theme.getName()).isGreaterThanOrEqualTo(4.5);
                open(Background.inline());
                assertThat(panel.legend().example(ExpressionLegend.Row.OR).getForeground()).isEqualTo(muted);
                assertThat(panel.legend().isOpaque()).as("the side panel paints the tint").isFalse();
            }
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }

    private static int rightEdgeInViewport(JScrollPane scroll, java.awt.Component component) {
        return SwingUtilities.convertPoint(component, component.getWidth(), 0, scroll.getViewport()).x;
    }

    /** Lays the tree out a few times, as a shown window settles (wrapped paragraphs change their height). */
    private static void layOut(java.awt.Component root) {
        for (int round = 0; round < 4; round++) {
            layOutTree(root);
        }
    }

    private static void layOutTree(java.awt.Component component) {
        component.doLayout();
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                layOutTree(child);
            }
        }
    }

    private static void focus(JTextField field) {
        for (FocusListener listener : field.getFocusListeners()) {
            listener.focusGained(new FocusEvent(field, FocusEvent.FOCUS_GAINED));
        }
    }
}
