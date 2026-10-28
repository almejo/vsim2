package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ConvertedSelection;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.PinOrder;
import cl.almejo.vsim.core.library.TestParts;
import java.awt.Color;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentFormPanelTest {

    private final ElementId first = new ElementId("c1");
    private final ElementId second = new ElementId("c2");
    private final ElementId output = new ElementId("c3");
    private final PinOrder order = new PinOrder(List.of(first, second), List.of(output));
    private final Map<ElementId, String> names = Map.of(first, "a", second, "b", output, "y");

    private static List<String> items(JTable table) {
        List<String> items = new ArrayList<>();
        for (int row = 0; row < table.getRowCount(); row++) {
            items.add((String) table.getValueAt(row, 0));
        }
        return items;
    }

    private static Component rendered(JTable table, int row) {
        return table.prepareRenderer(table.getCellRenderer(row, 0), row, 0);
    }

    @Test
    void thePinNamesAreEditedInPlaceAndCheckedWhileTyping() {
        ComponentForm form = ComponentForm.create(order, names, List.of(), Set.of());
        ComponentFormPanel panel = new ComponentFormPanel(form);
        panel.nameField().setText("Par");
        JTable inputs = panel.inputTable();
        assertThat(inputs.isCellEditable(0, 0)).isTrue();
        Color normal = rendered(inputs, 1).getForeground();

        assertThat(inputs.editCellAt(1, 0)).isTrue();
        JTextField editor = (JTextField) inputs.getEditorComponent();
        editor.setText("");
        assertThat(form.inputNames()).containsExactly("a", "");
        assertThat(form.canAccept()).isFalse();
        assertThat(panel.messageLabel().getText()).isEqualTo("Escribe un nombre para cada pin.");

        editor.setText("Y");
        assertThat(panel.messageLabel().getText()).isEqualTo("Hay más de un pin llamado «Y».");
        assertThat(inputs.isEditing()).isTrue();
        inputs.getCellEditor().stopCellEditing();
        Color marked = rendered(inputs, 1).getForeground();
        assertThat(marked).isNotEqualTo(normal);
        assertThat(((JComponent) rendered(inputs, 1)).getToolTipText()).isEqualTo("Hay más de un pin llamado «Y».");
        assertThat(((JComponent) rendered(panel.outputTable(), 0)).getToolTipText())
                .isEqualTo("Hay más de un pin llamado «y».");
        assertThat(((JComponent) rendered(inputs, 0)).getToolTipText()).isNull();

        panel.outputTable().setValueAt("Salida", 0, 0);
        assertThat(form.canAccept()).isTrue();
        assertThat(panel.messageLabel().getText()).isBlank();
        assertThat(items(inputs)).containsExactly("a", "Y");
        assertThat(rendered(inputs, 1).getForeground()).isEqualTo(normal);
    }

    @Test
    void cancellingAnEditGivesThePinItsNameBack() {
        ComponentForm form = ComponentForm.create(order, names, List.of(), Set.of());
        ComponentFormPanel panel = new ComponentFormPanel(form);
        JTable inputs = panel.inputTable();

        inputs.editCellAt(0, 0);
        ((JTextField) inputs.getEditorComponent()).setText("Dato");
        assertThat(form.inputNames()).containsExactly("Dato", "b");
        inputs.getCellEditor().cancelCellEditing();

        assertThat(form.inputNames()).containsExactly("a", "b");
        assertThat(items(inputs)).containsExactly("a", "b");
    }

    @Test
    void movingAPinWhileEditingItsNameKeepsTheNewName() {
        ComponentForm form = ComponentForm.create(order, names, List.of(), Set.of());
        ComponentFormPanel panel = new ComponentFormPanel(form);
        JTable inputs = panel.inputTable();
        inputs.setRowSelectionInterval(0, 0);
        inputs.editCellAt(0, 0);
        ((JTextField) inputs.getEditorComponent()).setText("Dato");

        panel.inputDownButton().doClick(0);

        assertThat(inputs.isEditing()).isFalse();
        assertThat(items(inputs)).containsExactly("b", "Dato");
        assertThat(inputs.getSelectedRow()).isEqualTo(1);
    }

    @Test
    void typingANameUpdatesTheFormAndTheMessage() {
        ComponentForm form = ComponentForm.create(order, names, List.of("Inversor"), Set.of());
        ComponentFormPanel panel = new ComponentFormPanel(form);
        assertThat(panel.messageLabel().getText()).isEqualTo("Escribe un nombre para el componente.");

        panel.nameField().setText("inversor");
        assertThat(form.name()).isEqualTo("inversor");
        assertThat(panel.messageLabel().getText()).startsWith("Ya existe");

        panel.nameField().setText("Negador");
        assertThat(panel.messageLabel().getText()).isBlank();
    }

    @Test
    void theButtonsMoveTheSelectedPin() {
        ComponentForm form = ComponentForm.create(order, names, List.of(), Set.of());
        ComponentFormPanel panel = new ComponentFormPanel(form);
        assertThat(items(panel.inputTable())).containsExactly("a", "b");
        assertThat(items(panel.outputTable())).containsExactly("y");

        panel.inputTable().setRowSelectionInterval(0, 0);
        panel.inputDownButton().doClick(0);

        assertThat(form.inputNames()).containsExactly("b", "a");
        assertThat(items(panel.inputTable())).containsExactly("b", "a");
        assertThat(panel.inputTable().getSelectedRow()).isEqualTo(1);
    }

    @Test
    void creatingOffersTheReplaceCheckboxAndUpdatingTheTargetChoice() {
        ComponentForm creating = ComponentForm.create(order, names, List.of(), Set.of());
        ComponentFormPanel createPanel = new ComponentFormPanel(creating);
        assertThat(createPanel.replaceCheckBox().getParent()).isSameAs(createPanel);
        assertThat(createPanel.targetChoice().getParent()).isNull();
        createPanel.replaceCheckBox().doClick(0);
        assertThat(creating.replaceSelection()).isFalse();

        ComponentDefinition doubleInverter = ExampleDefinitions.doubleInverter();
        ComponentForm updating = ComponentForm.update(order, names, List.of(),
                List.of(ExampleDefinitions.inverter(), doubleInverter));
        ComponentFormPanel updatePanel = new ComponentFormPanel(updating);
        assertThat(updatePanel.replaceCheckBox().getParent()).isNull();

        updatePanel.targetChoice().setSelectedItem(doubleInverter);

        assertThat(updating.target()).contains(doubleInverter);
        assertThat(updatePanel.nameField().getText()).isEqualTo("Doble inversor");
    }

    @Test
    void theDescriptionAreaEditsTheFormAndFollowsTheChosenTarget() {
        ComponentForm creating = ComponentForm.create(order, names, List.of(), Set.of());
        ComponentFormPanel createPanel = new ComponentFormPanel(creating);
        assertThat(createPanel.descriptionArea().getText()).isEmpty();

        createPanel.descriptionArea().setText("Suma dos bits.");

        assertThat(creating.description()).isEqualTo("Suma dos bits.");
        assertThat(createPanel.descriptionArea().getLineWrap()).isTrue();

        ComponentDefinition doubleInverter = ExampleDefinitions.doubleInverter().withDescription("Deja pasar.");
        ComponentForm updating = ComponentForm.update(order, names, List.of(),
                List.of(ExampleDefinitions.inverter().withDescription("Invierte."), doubleInverter));
        ComponentFormPanel updatePanel = new ComponentFormPanel(updating);
        assertThat(updatePanel.descriptionArea().getText()).isEqualTo("Invierte.");

        updatePanel.targetChoice().setSelectedItem(doubleInverter);

        assertThat(updatePanel.descriptionArea().getText()).isEqualTo("Deja pasar.");
        assertThat(updating.description()).isEqualTo("Deja pasar.");
    }

    @Test
    void theTestPartsListShowsOnlyWhenTheSelectionHasTestParts() {
        ComponentFormPanel withoutParts = new ComponentFormPanel(ComponentForm.create(order, names, List.of(),
                Set.of()));
        assertThat(withoutParts.testPartsPanel().getParent()).isNull();
        assertThat(withoutParts.testPartChoices()).isEmpty();

        ElementId carry = new ElementId("c7");
        ElementId clock = new ElementId("c8");
        ElementId led = new ElementId("c9");
        List<TestParts.TestPart> parts = List.of(
                new TestParts.TestPart(carry, StandardComponentTypes.CONSTANT, TestParts.Kind.CONSTANT, "Cin", "0"),
                new TestParts.TestPart(clock, StandardComponentTypes.CLOCK, TestParts.Kind.CLOCK, "", ""),
                new TestParts.TestPart(led, StandardComponentTypes.LED, TestParts.Kind.LED, "S", ""));
        ElementId carryPin = new ElementId("c10");
        ElementId clockPin = new ElementId("c11");
        ElementId ledPin = new ElementId("c12");
        ComponentForm form = ComponentForm.create(parts, converted -> {
            Map<ElementId, String> pinNames = new HashMap<>(names);
            Map<ElementId, ConvertedSelection.PartPins> given = new HashMap<>();
            List.of(carry, clock, led).forEach(part -> given.put(part,
                    new ConvertedSelection.PartPins(List.of(), Optional.empty())));
            if (converted.contains(carry)) {
                pinNames.put(carryPin, "Cin");
                given.put(carry, new ConvertedSelection.PartPins(List.of(carryPin), Optional.empty()));
            }
            if (converted.contains(clock)) {
                pinNames.put(clockPin, "A");
                given.put(clock, new ConvertedSelection.PartPins(List.of(clockPin), Optional.empty()));
            }
            if (converted.contains(led)) {
                pinNames.put(ledPin, "S");
                given.put(led, new ConvertedSelection.PartPins(List.of(ledPin), Optional.empty()));
            }
            return new ComponentForm.PinPlan(order, pinNames, given);
        }, List.of(), Set.of());
        ComponentFormPanel panel = new ComponentFormPanel(form);

        assertThat(panel.testPartsPanel().getParent()).isNotNull();
        assertThat(panel.testPartChoices()).extracting(JCheckBox::getText).containsExactly("Cin = 0", "Reloj", "S");
        assertThat(panel.testPartChoices()).allSatisfy(choice -> assertThat(choice.isSelected()).isTrue());
        assertThat(panel.testPartOutcomes()).extracting(JLabel::getText)
                .containsExactly("Entrada (Cin)", "Entrada (A)", "Salida (S)");

        panel.testPartChoices().get(1).doClick(0);
        panel.testPartChoices().get(2).doClick(0);
        panel.testPartChoices().get(0).doClick(0);

        assertThat(form.isConverted(clock)).isFalse();
        assertThat(form.isConverted(led)).isFalse();
        assertThat(panel.testPartOutcomes()).extracting(JLabel::getText)
                .containsExactly("Queda como constante", "Reloj interno", "Se quita");
    }

    @Test
    void bothDialogsExplainWhatComponentPinsDo() {
        ComponentFormPanel creating = new ComponentFormPanel(ComponentForm.create(order, names, List.of(), Set.of()));
        assertThat(creating.pinsHelpLabel().getParent()).isSameAs(creating);
        assertThat(creating.pinsHelpLabel().getText()).contains("interruptor y LED").contains("conexiones");

        ComponentFormPanel updating = new ComponentFormPanel(ComponentForm.update(order, names, List.of(),
                List.of(ExampleDefinitions.inverter())));
        assertThat(updating.pinsHelpLabel().getParent()).isSameAs(updating);
    }

    @Test
    void theUpdateDialogSaysThatExistingPinsKeepTheirPlaceInTheBox() {
        ComponentFormPanel updating = new ComponentFormPanel(ComponentForm.update(order, names, List.of(),
                List.of(ExampleDefinitions.inverter())));
        ComponentFormPanel creating = new ComponentFormPanel(ComponentForm.create(order, names, List.of(), Set.of()));

        assertThat(updating.updateNoteLabel()).isPresent();
        assertThat(updating.updateNoteLabel().orElseThrow().getText()).contains("conservan su lugar");
        assertThat(creating.updateNoteLabel()).isEmpty();
    }
}
