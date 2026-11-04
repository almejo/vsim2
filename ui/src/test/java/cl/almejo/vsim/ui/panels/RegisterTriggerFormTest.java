package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.Test;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The register's «Disparo» in Propiedades. */
class RegisterTriggerFormTest {

    @Test
    void theRegisterOffersDisparoWithItsFourLabelsAndShowsTheCurrentOne() {
        ParameterForm form = new ParameterForm(
                StandardComponentTypes.registry().require(StandardComponentTypes.REGISTER), Map.of("trigger", "high"));
        @SuppressWarnings("unchecked")
        JComboBox<Object> combo = (JComboBox<Object>) form.editor("trigger");

        assertThat(form.label("trigger").getText()).isEqualTo("Disparo");
        List<String> labels = new ArrayList<>();
        for (int index = 0; index < combo.getItemCount(); index++) {
            labels.add(((JLabel) combo.getRenderer().getListCellRendererComponent(new JList<>(),
                    combo.getItemAt(index), index, false, false)).getText());
        }
        assertThat(labels).containsExactly("Flanco de bajada", "Flanco de subida", "Nivel alto", "Nivel bajo");
        assertThat(combo.getSelectedItem()).isEqualTo("high");
        assertThat(Messages.choiceName("trigger", "low")).isEqualTo("Nivel bajo");
    }
}
