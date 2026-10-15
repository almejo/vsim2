package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigureDialogTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    private ParameterForm notForm() {
        return new ParameterForm(registry.require(StandardComponentTypes.NOT), Map.of());
    }

    private static void typeInto(JSpinner spinner, String text) {
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setText(text);
    }

    @Test
    void okWithAnInvalidValueKeepsTheDialogOpenAndShowsTheError() {
        ParameterForm form = notForm();
        typeInto((JSpinner) form.editor("delay"), "0");
        JLabel error = new JLabel(" ");
        assertThat(ConfigureDialog.acceptChoice(JOptionPane.OK_OPTION, form, error)).isFalse();
        assertThat(error.getText()).contains("Retardo");
    }

    @Test
    void okAfterFixingTheValueClosesAndClearsTheError() {
        ParameterForm form = notForm();
        JLabel error = new JLabel(" ");
        typeInto((JSpinner) form.editor("delay"), "0");
        assertThat(ConfigureDialog.acceptChoice(JOptionPane.OK_OPTION, form, error)).isFalse();
        typeInto((JSpinner) form.editor("delay"), "4");
        assertThat(ConfigureDialog.acceptChoice(JOptionPane.OK_OPTION, form, error)).isTrue();
        assertThat(error.getText()).isEqualTo(" ");
        assertThat(form.rawValues()).containsEntry("delay", 4);
    }

    @Test
    void okWithAConstantThatDoesNotFitShowsTheMessageAndStaysOpen() {
        ParameterForm form = new ParameterForm(
                StandardComponentTypes.registry().require(StandardComponentTypes.CONSTANT),
                Map.of("width", 4, "value", "1"));
        ((javax.swing.JTextField) form.editor("value")).setText("0x1F");
        JLabel error = new JLabel(" ");
        assertThat(ConfigureDialog.acceptChoice(JOptionPane.OK_OPTION, form, error)).isFalse();
        assertThat(error.getText()).isEqualTo("No cabe en 4 bits");
    }

    @Test
    void cancelAndCloseAlwaysCloseWithoutValidating() {
        ParameterForm form = notForm();
        typeInto((JSpinner) form.editor("delay"), "0");
        JLabel error = new JLabel(" ");
        assertThat(ConfigureDialog.acceptChoice(JOptionPane.CANCEL_OPTION, form, error)).isTrue();
        assertThat(ConfigureDialog.acceptChoice(JOptionPane.CLOSED_OPTION, form, error)).isTrue();
        assertThat(error.getText()).isEqualTo(" ");
    }
}
