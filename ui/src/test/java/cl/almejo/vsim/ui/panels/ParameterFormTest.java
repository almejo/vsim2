package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import java.awt.Component;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ParameterFormTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    private ParameterForm form(String typeId, Map<String, Object> current) {
        return new ParameterForm(registry.require(typeId), current);
    }

    private static void typeInto(JSpinner spinner, String text) {
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setText(text);
    }

    @Test
    void showsOneEditorPerParameterWithTheCurrentValues() {
        ParameterForm form = form(StandardComponentTypes.CLOCK, Map.of("time-up", 250));
        assertThat(((JSpinner) form.editor("time-up")).getValue()).isEqualTo(250);
        assertThat(((JSpinner) form.editor("time-down")).getValue()).isEqualTo(1000);
        JSpinner timeDown = (JSpinner) form.editor("time-down");
        assertThat(((JSpinner.DefaultEditor) timeDown.getEditor()).getTextField().getText()).isEqualTo("1000");
    }

    @Test
    void unchangedValuesReportNoChanges() {
        ParameterForm form = form(StandardComponentTypes.NOT, Map.of("delay", 3));
        assertThat(form.validationError()).isEmpty();
        assertThat(form.hasChanges()).isFalse();
    }

    @Test
    void changedValuesAreReported() {
        ParameterForm form = form(StandardComponentTypes.NOT, Map.of());
        ((JSpinner) form.editor("delay")).setValue(5);
        assertThat(form.validationError()).isEmpty();
        assertThat(form.hasChanges()).isTrue();
        assertThat(form.rawValues()).containsEntry("delay", 5);
    }

    @Test
    void choiceEditorOffersTheParameterChoices() {
        ParameterForm form = form(StandardComponentTypes.LED, Map.of());
        @SuppressWarnings("unchecked")
        JComboBox<String> colors = (JComboBox<String>) form.editor("color");
        assertThat(colors.getItemCount()).isEqualTo(8);
        assertThat(colors.getSelectedItem()).isEqualTo("red");
        colors.setSelectedItem("green");
        assertThat(form.rawValues()).containsEntry("color", "green");
        assertThat(form.hasChanges()).isTrue();
    }

    @Test
    void outOfRangeTextReportsALocalizedError() {
        ParameterForm form = form(StandardComponentTypes.NOT, Map.of());
        typeInto((JSpinner) form.editor("delay"), "0");
        assertThat(form.validationError()).hasValueSatisfying(message -> assertThat(message)
                .contains("Retardo").contains("1.000.000"));
    }

    @Test
    void nonNumericTextReportsALocalizedError() {
        ParameterForm form = form(StandardComponentTypes.CLOCK, Map.of());
        typeInto((JSpinner) form.editor("time-up"), "rápido");
        assertThat(form.validationError())
                .hasValueSatisfying(message -> assertThat(message).contains("Tiempo en alto"));
    }

    @Test
    void typeWithoutParametersHasNothingToChange() {
        ComponentType display = registry.require(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY);
        ParameterForm form = new ParameterForm(display, Map.of());
        assertThat(form.rawValues()).isEmpty();
        assertThat(form.hasChanges()).isFalse();
    }

    @Test
    void choiceWithNothingSelectedReportsAnInvalidValue() {
        ParameterForm form = form(StandardComponentTypes.LED, Map.of());
        ((JComboBox<?>) form.editor("color")).setSelectedItem(null);
        assertThat(form.validationError()).contains("Valor inválido para Color");
    }

    @Test
    void spinnerTextFieldsKeepInvalidTextWhenFocusIsLost() {
        ParameterForm form = form(StandardComponentTypes.NOT, Map.of());
        JFormattedTextField field = ((JSpinner.DefaultEditor) ((JSpinner) form.editor("delay")).getEditor()).getTextField();
        assertThat(field.getFocusLostBehavior()).isEqualTo(JFormattedTextField.COMMIT);
    }

    @Test
    void commitListenersHearSpinnerAndChoiceChanges() {
        ParameterForm clock = form(StandardComponentTypes.CLOCK, Map.of());
        AtomicInteger commits = new AtomicInteger();
        clock.addCommitListener(commits::incrementAndGet);
        ((JSpinner) clock.editor("time-up")).setValue(250);
        assertThat(commits.get()).isPositive();
        assertThat(clock.rawValues()).containsEntry("time-up", 250);

        ParameterForm led = form(StandardComponentTypes.LED, Map.of());
        AtomicInteger choices = new AtomicInteger();
        led.addCommitListener(choices::incrementAndGet);
        ((JComboBox<?>) led.editor("color")).setSelectedItem("green");
        assertThat(choices.get()).isPositive();
    }

    private static JLabel labelOf(ParameterForm form, String parameterName) {
        for (Component child : form.getComponents()) {
            if (child instanceof JLabel label && label.getLabelFor() == form.editor(parameterName)) {
                return label;
            }
        }
        throw new AssertionError("no label for " + parameterName);
    }

    @Test
    void timeParametersSayTheirUnitAndExplainIt() {
        String tip = "En unidades de tiempo de simulación";
        ParameterForm clock = form(StandardComponentTypes.CLOCK, Map.of());
        assertThat(labelOf(clock, "time-up").getText()).isEqualTo("Tiempo en alto (t)");
        assertThat(labelOf(clock, "time-down").getText()).isEqualTo("Tiempo en bajo (t)");
        assertThat(labelOf(clock, "time-up").getToolTipText()).isEqualTo(tip);
        assertThat(clock.editor("time-up").getToolTipText()).isEqualTo(tip);
        ParameterForm gate = form(StandardComponentTypes.NOT, Map.of());
        assertThat(labelOf(gate, "delay").getText()).isEqualTo("Retardo (t)");
        assertThat(((JSpinner.DefaultEditor) ((JSpinner) gate.editor("delay")).getEditor()).getTextField()
                .getToolTipText()).isEqualTo(tip);
        ParameterForm diagram = form(StandardComponentTypes.TIME_DIAGRAM, Map.of());
        assertThat(labelOf(diagram, "sample-interval").getText()).isEqualTo("Intervalo de muestreo (t)");
        assertThat(labelOf(diagram, "history").getText()).isEqualTo("Historial (muestras)");
        assertThat(labelOf(diagram, "history").getToolTipText()).isNull();
        assertThat(labelOf(form(StandardComponentTypes.SWITCH, Map.of()), "value").getText()).isEqualTo("Valor");
    }

    @Test
    void anInvalidTimeKeepsThePlainNameInItsMessage() {
        ParameterForm gate = form(StandardComponentTypes.NOT, Map.of());
        typeInto((JSpinner) gate.editor("delay"), "0");
        assertThat(gate.validationError()).hasValueSatisfying(message -> assertThat(message)
                .contains("Retardo").doesNotContain("(t)"));
    }
}
