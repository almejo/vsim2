package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.logic.CircuitGenerator;
import cl.almejo.vsim.core.logic.ExpressionLine;
import cl.almejo.vsim.core.logic.GateCost;
import cl.almejo.vsim.core.logic.TableCircuits;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JTextField;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.List;

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
        panel = new ExpressionsPanel(target, background, preferences, KarnaughCost.GENERATOR, wizardTables::add);
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
        assertThat(panel.wizardLink().isVisible()).isFalse();

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
        });
        assertThat(panel.resultText()).isEqualTo("✓ El circuito cumple la tabla.");
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
        assertThat(panel.wizardLink().isEnabled()).isFalse();
        assertThat(panel.wizardReasonText()).isEqualTo("El asistente funciona con 2 a 4 entradas y hasta 4 salidas");

        panel.titleField().setText("Mi tabla");
        type(0, "F = A + B C");
        assertThat(panel.wizardLink().isEnabled()).isTrue();
        assertThat(panel.wizardReasonText()).isEmpty();

        panel.wizardLink().doClick();

        assertThat(closed).isEqualTo(1);
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
        panel.requestClose();
        assertThat(discardQuestions).isZero();
        assertThat(closed).isEqualTo(1);
    }

    private static void focus(JTextField field) {
        for (FocusListener listener : field.getFocusListeners()) {
            listener.focusGained(new FocusEvent(field, FocusEvent.FOCUS_GAINED));
        }
    }
}
