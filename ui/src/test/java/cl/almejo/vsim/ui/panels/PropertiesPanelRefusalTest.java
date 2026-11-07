package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.LedMatrixTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.Selection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.addWire;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** When Propiedades refuses a change because a pin would join the wrong wire, its control goes back to the part's value. */
class PropertiesPanelRefusalTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final Selection selection = new Selection();
    private final PropertiesPanel panel = new PropertiesPanel(openDocument, selection);

    @AfterEach
    void close() {
        panel.dispose();
        openDocument.close();
    }

    @Test
    void aRefusedMostrarQBarraSwitchGoesBackToNo() {
        ComponentType type = openDocument.registry().require(StandardComponentTypes.FLIP_FLOP_DATA);
        Component flipFlop = place(openDocument, StandardComponentTypes.FLIP_FLOP_DATA, 10, 10,
                Map.of(StandardComponentTypes.SHOW_INVERTED, false));
        Component shown = flipFlop.withParameters(type.resolveParameters(Map.of(StandardComponentTypes.SHOW_INVERTED, true)));
        GridPoint output = type.pinLocation(flipFlop, 1);
        GridPoint future = type.pinLocation(shown, 3);
        addWire(openDocument, output.x(), output.y(), 2 * future.x() - output.x(), 2 * future.y() - output.y());
        selection.select(flipFlop.id());
        JCheckBox box = (JCheckBox) panel.parameterForm().editor(StandardComponentTypes.SHOW_INVERTED);

        box.doClick();

        assertThat(panel.error()).isNotBlank();
        assertThat(box.isSelected()).isFalse();
        assertThat(openDocument.document().component(flipFlop.id()).orElseThrow().parameters())
                .containsEntry(StandardComponentTypes.SHOW_INVERTED, false);
    }

    @Test
    void aRefusedBarridoChoiceGoesBackToPorFilas() {
        ComponentType type = openDocument.registry().require(StandardComponentTypes.LED_MATRIX);
        Component matrix = place(openDocument, StandardComponentTypes.LED_MATRIX, 10, 10, Map.of());
        // wires on every row pin: the three scan pins cannot take them
        for (int pin = 0; pin < 8; pin++) {
            GridPoint location = type.pinLocation(matrix, pin);
            addWire(openDocument, location.x(), location.y(), location.x() - 4, location.y());
            addWire(openDocument, location.x() - 4, location.y(), location.x() - 4, location.y() + 20 + pin);
        }
        selection.select(matrix.id());
        @SuppressWarnings("unchecked")
        JComboBox<Object> combo = (JComboBox<Object>) panel.parameterForm().editor(LedMatrixTypes.INPUT);

        combo.setSelectedItem(LedMatrixTypes.SCAN);

        assertThat(panel.error()).isNotBlank();
        assertThat(combo.getSelectedItem()).isEqualTo(LedMatrixTypes.BY_ROWS);
    }

    @Test
    void showValuesPutsASpinnerAndATextFieldBackWithoutCommitting() {
        ComponentType type = openDocument.registry().require(StandardComponentTypes.CONSTANT);
        ParameterForm form = new ParameterForm(type, Map.of("width", 8, "value", "5"));
        int[] commits = {0};
        form.addCommitListener(() -> commits[0]++);
        ((javax.swing.JSpinner) form.editor("width")).setValue(4);
        ((javax.swing.text.JTextComponent) form.editor("value")).setText("9");
        commits[0] = 0;

        form.showValues(type.resolveParameters(Map.of("width", 8, "value", "5")));

        assertThat(((javax.swing.JSpinner) form.editor("width")).getValue()).isEqualTo(8);
        assertThat(((javax.swing.text.JTextComponent) form.editor("value")).getText()).isEqualTo("5");
        assertThat(commits[0]).isZero();
    }
}
