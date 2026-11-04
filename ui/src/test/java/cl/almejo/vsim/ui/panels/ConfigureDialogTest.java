package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.theme.FlatLafInstaller;
import cl.almejo.vsim.ui.theme.ReadableColors;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigureDialogTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    private static final Function<Map<String, Object>, Optional<String>> NOTHING_REFUSED = values -> Optional.empty();

    private ParameterForm notForm() {
        return new ParameterForm(registry.require(StandardComponentTypes.BUFFER), Map.of());
    }

    private static void typeInto(JSpinner spinner, String text) {
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setText(text);
    }

    @Test
    void theDialogShowsTheFormWithoutTheDescription() {
        ParameterForm form = new ParameterForm(registry.require(StandardComponentTypes.CLOCK), Map.of());

        JPanel content = ConfigureDialog.content(form, ConfigureDialog.newErrorLabel());

        assertThat(content.getComponents()).contains(form)
                .noneMatch(component -> component instanceof DescriptionBox);
    }

    @Test
    void okWithAnInvalidValueKeepsTheDialogOpenAndShowsTheError() {
        ParameterForm form = notForm();
        typeInto((JSpinner) form.editor("delay"), "0");
        JLabel error = new JLabel(" ");
        assertThat(ConfigureDialog.acceptChoice(JOptionPane.OK_OPTION, form, error, NOTHING_REFUSED)).isFalse();
        assertThat(error.getText()).contains("Retardo");
    }

    @Test
    void okAfterFixingTheValueClosesAndClearsTheError() {
        ParameterForm form = notForm();
        JLabel error = new JLabel(" ");
        typeInto((JSpinner) form.editor("delay"), "0");
        assertThat(ConfigureDialog.acceptChoice(JOptionPane.OK_OPTION, form, error, NOTHING_REFUSED)).isFalse();
        typeInto((JSpinner) form.editor("delay"), "4");
        assertThat(ConfigureDialog.acceptChoice(JOptionPane.OK_OPTION, form, error, NOTHING_REFUSED)).isTrue();
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
        assertThat(ConfigureDialog.acceptChoice(JOptionPane.OK_OPTION, form, error, NOTHING_REFUSED)).isFalse();
        assertThat(error.getText()).isEqualTo("No cabe en 4 bits");
    }

    @Test
    void okThatTheDocumentRefusesKeepsTheDialogOpenAndShowsWhy() {
        ParameterForm form = notForm();
        JLabel error = new JLabel(" ");

        assertThat(ConfigureDialog.acceptChoice(JOptionPane.OK_OPTION, form, error,
                values -> Optional.of("El pin b4 quedaría unido"))).isFalse();
        assertThat(error.getText()).isEqualTo("El pin b4 quedaría unido");
    }

    @Test
    void cancelAndCloseAlwaysCloseWithoutValidating() {
        ParameterForm form = notForm();
        typeInto((JSpinner) form.editor("delay"), "0");
        JLabel error = new JLabel(" ");
        assertThat(ConfigureDialog.acceptChoice(JOptionPane.CANCEL_OPTION, form, error, NOTHING_REFUSED)).isTrue();
        assertThat(ConfigureDialog.acceptChoice(JOptionPane.CLOSED_OPTION, form, error, NOTHING_REFUSED)).isTrue();
        assertThat(error.getText()).isEqualTo(" ");
    }

    @Test
    void theErrorLineIsInTheReadableErrorRedOfTheThemeShownNow() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            UIManager.setLookAndFeel(FlatLafInstaller.lookAndFeel("linux", true));
            assertThat(ConfigureDialog.newErrorLabel().getForeground()).isEqualTo(ReadableColors.errorText());
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }
}
