package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.PinOrder;
import cl.almejo.vsim.core.library.TestParts;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentFormPanelTest {

    private final ElementId first = new ElementId("c1");
    private final ElementId second = new ElementId("c2");
    private final ElementId output = new ElementId("c3");
    private final PinOrder order = new PinOrder(List.of(first, second), List.of(output));
    private final Map<ElementId, String> names = Map.of(first, "a", second, "b", output, "y");

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    private static List<String> items(JList<String> list) {
        List<String> items = new ArrayList<>();
        for (int index = 0; index < list.getModel().getSize(); index++) {
            items.add(list.getModel().getElementAt(index));
        }
        return items;
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
        assertThat(items(panel.inputList())).containsExactly("a", "b");
        assertThat(items(panel.outputList())).containsExactly("y");

        panel.inputList().setSelectedIndex(0);
        panel.inputDownButton().doClick(0);

        assertThat(form.inputNames()).containsExactly("b", "a");
        assertThat(items(panel.inputList())).containsExactly("b", "a");
        assertThat(panel.inputList().getSelectedIndex()).isEqualTo(1);
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
        ComponentForm form = ComponentForm.create(parts, converted -> new ComponentForm.PinPlan(order, names),
                List.of(), Set.of());
        ComponentFormPanel panel = new ComponentFormPanel(form);

        assertThat(panel.testPartsPanel().getParent()).isNotNull();
        assertThat(panel.testPartChoices()).extracting(JCheckBox::getText).containsExactly("Cin = 0", "Reloj", "S");
        assertThat(panel.testPartChoices()).allSatisfy(choice -> assertThat(choice.isSelected()).isTrue());
        assertThat(panel.testPartOutcomes()).extracting(JLabel::getText)
                .containsExactly("Entrada", "Entrada", "Salida");

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
}
