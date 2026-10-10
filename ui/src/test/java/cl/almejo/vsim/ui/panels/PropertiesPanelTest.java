package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.clipboard.FragmentTransferable;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.TransferHandler;
import javax.swing.text.JTextComponent;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class PropertiesPanelTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final Selection selection = new Selection();
    private PropertiesPanel panel;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        panel = new PropertiesPanel(openDocument, selection);
    }

    @AfterEach
    void close() {
        panel.dispose();
        openDocument.close();
    }

    private Component gate(int x) {
        return place(openDocument, StandardComponentTypes.NOT, x, 0, Map.of());
    }

    private Component current(Component component) {
        return openDocument.document().component(component.id()).orElseThrow();
    }

    private static void typeInto(JSpinner spinner, String text) {
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setText(text);
    }

    @Test
    void nothingSelectedSaysSo() {
        assertThat(panel.heading()).isEqualTo("Sin selección");
        assertThat(panel.textField()).isNull();
    }

    @Test
    void severalElementsShowTheirCount() {
        Component first = gate(0);
        Component second = gate(10);
        selection.set(List.of(first.id(), second.id()));
        assertThat(panel.heading()).isEqualTo("2 elementos");
    }

    @Test
    void aWireShowsItsLength() {
        Wire wire = new Wire(openDocument.document().newWireId(), new GridPoint(0, 0), new GridPoint(6, 0));
        openDocument.execute(new AddWireCommand(wire));
        selection.select(wire.id());
        assertThat(panel.heading()).isEqualTo("Cable · largo 6");
    }

    @Test
    void aSubcircuitInstanceIsHeadedByItsDefinitionName() {
        openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        Component instance = place(openDocument, SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 0, 0,
                Map.of());
        selection.select(instance.id());
        assertThat(panel.heading()).isEqualTo("Inversor");
    }

    @Test
    void aComponentShowsTypeLabelAndParameters() {
        Component gate = gate(0);
        selection.select(gate.id());
        assertThat(panel.heading()).isEqualTo("NOT");
        assertThat(panel.textField().getText()).isEmpty();
        assertThat(((JSpinner) panel.parameterForm().editor("delay")).getValue()).isEqualTo(1);
    }

    @Test
    void enterThenFocusLossAddsOneUndoStep() {
        Component gate = gate(0);
        selection.select(gate.id());
        panel.textField().setText(" U7 ");
        panel.textField().postActionEvent();
        panel.textField().postActionEvent();
        panel.commitEdits();
        assertThat(current(gate).label()).isEqualTo("U7");
        assertThat(openDocument.history().undoDescription()).contains("command.label");
        openDocument.history().undo();
        assertThat(current(gate).label()).isEmpty();
        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    @Test
    void aSpinnerStepAppliesTheParameter() {
        Component gate = gate(0);
        selection.select(gate.id());
        ((JSpinner) panel.parameterForm().editor("delay")).setValue(5);
        assertThat(current(gate).parameters()).containsEntry("delay", 5);
        assertThat(openDocument.history().undoDescription()).contains("command.configure");
    }

    @Test
    void anInvalidValueShowsTheErrorAndAddsNoStep() {
        Component gate = gate(0);
        selection.select(gate.id());
        typeInto((JSpinner) panel.parameterForm().editor("delay"), "0");
        panel.commitEdits();
        assertThat(panel.error()).contains("Retardo");
        assertThat(current(gate).parameters()).containsEntry("delay", 1);
        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    @Test
    void selectionChangeCommitsThePendingEditToThePreviousElement() {
        Component first = gate(0);
        Component second = gate(10);
        selection.select(first.id());
        panel.textField().setText("A1");
        selection.select(second.id());
        assertThat(current(first).label()).isEqualTo("A1");
        assertThat(current(second).label()).isEmpty();
        assertThat(panel.textField().getText()).isEmpty();
    }

    @Test
    void undoRefreshesTheFieldsAndAStaleFieldCannotReapply() {
        Component gate = gate(0);
        selection.select(gate.id());
        panel.textField().setText("U1");
        panel.textField().postActionEvent();
        JTextField stale = panel.textField();
        openDocument.history().undo();
        assertThat(panel.textField()).isNotSameAs(stale);
        assertThat(panel.textField().getText()).isEmpty();
        stale.postActionEvent();
        assertThat(current(gate).label()).isEmpty();
        assertThat(openDocument.history().canRedo()).isTrue();
    }

    @Test
    void blankTextInPropertiesShowsAnErrorAndKeepsTheText() {
        TextLabel label = new TextLabel(openDocument.document().newLabelId(), new GridPoint(3, 3), "nota");
        openDocument.execute(new AddElementsCommand(List.of(label)));
        selection.select(label.id());
        assertThat(panel.heading()).isEqualTo("Texto libre");
        panel.textField().setText("   ");
        panel.textField().postActionEvent();
        assertThat(panel.error()).isEqualTo("El texto no puede quedar vacío");
        assertThat(openDocument.document().label(label.id()).orElseThrow().text()).isEqualTo("nota");
        panel.textField().setText("otra");
        panel.textField().postActionEvent();
        assertThat(openDocument.document().label(label.id()).orElseThrow().text()).isEqualTo("otra");
        assertThat(panel.error()).isEqualTo(" ");
    }

    /** Controller ruling R8: documents outlive windows (M3-C), so a disposed panel must stop listening. */
    @Test
    void aDisposedPanelNoLongerFollowsTheDocument() {
        Component gate = gate(0);
        selection.select(gate.id());
        JTextField shown = panel.textField();
        panel.dispose();
        openDocument.execute(new SetLabelCommand(gate.id(), "U5"));
        assertThat(panel.textField()).isSameAs(shown);
        assertThat(panel.textField().getText()).isEmpty();
    }

    @Test
    void editorsDoNothingOnceThePanelIsDisposed() {
        Component gate = gate(0);
        selection.select(gate.id());
        JTextField field = panel.textField();
        String before = openDocument.history().undoDescription().toString();
        panel.dispose();
        field.setText("U9");
        field.postActionEvent();
        panel.commitEdits();
        assertThat(current(gate).label()).isEmpty();
        assertThat(openDocument.history().undoDescription().toString()).isEqualTo(before);
    }

    /** Review I1: a drag out released over the label field must not write the fragment JSON into it. */
    @Test
    void editorFieldsRefuseCircuitFragmentsButAcceptPlainText() {
        Component gate = gate(0);
        selection.select(gate.id());
        JFormattedTextField delayField =
                ((JSpinner.DefaultEditor) ((JSpinner) panel.parameterForm().editor("delay")).getEditor()).getTextField();
        for (JTextComponent field : List.of(panel.textField(), delayField)) {
            TransferHandler handler = field.getTransferHandler();
            FragmentTransferable fragment = new FragmentTransferable("{\"formatVersion\":2,\"kind\":\"fragment\"}");
            assertThat(handler.canImport(new TransferHandler.TransferSupport(field, fragment))).isFalse();
            assertThat(handler.importData(new TransferHandler.TransferSupport(field, fragment))).isFalse();
            assertThat(handler.canImport(new TransferHandler.TransferSupport(field, new StringSelection("U7"))))
                    .isTrue();
        }
        panel.commitEdits();
        assertThat(current(gate).label()).isEmpty();
    }

    @Test
    void aLabelAndAParameterChangedTogetherAreOneUndoStep() {
        Component gate = gate(0);
        selection.select(gate.id());
        panel.textField().setText("U7");
        typeInto((JSpinner) panel.parameterForm().editor("delay"), "4");

        panel.commitEdits(); // what a selection change or closing the window does with both edits pending

        assertThat(current(gate).label()).isEqualTo("U7");
        assertThat(current(gate).parameters()).containsEntry("delay", 4);
        assertThat(openDocument.history().undoDescription()).contains("command.configure");
        openDocument.history().undo();
        assertThat(current(gate).label()).isEmpty();
        assertThat(current(gate).parameters()).containsEntry("delay", 1);
        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    @Test
    void aSelectionChangeWithBothEditsPendingIsOneUndoStepToo() {
        Component first = gate(0);
        Component second = gate(10);
        selection.select(first.id());
        panel.textField().setText("A1");
        typeInto((JSpinner) panel.parameterForm().editor("delay"), "3");

        selection.select(second.id());

        assertThat(current(first).label()).isEqualTo("A1");
        assertThat(current(first).parameters()).containsEntry("delay", 3);
        openDocument.history().undo();
        assertThat(current(first).label()).isEmpty();
        assertThat(current(first).parameters()).containsEntry("delay", 1);
    }
}
