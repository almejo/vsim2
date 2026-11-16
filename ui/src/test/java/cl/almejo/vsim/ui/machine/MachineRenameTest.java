package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.InstanceNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JTextField;
import javax.swing.text.JTextComponent;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renaming a machine (hand test of hito 2, round 2: it was stuck as «Máquina 1»): a «Nombre» row in Propiedades, in
 * the machine's own rows inside its tab and on its piece in the circuit, renames its definition in one undo step, with
 * the rules of a component's name (required, not another component's of the circuit); the tab follows.
 */
class MachineRenameTest {

    private static final String OTHER_ID = "otra";

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<EditorView> views = new ArrayList<>();
    private final List<OpenDocument> places = new ArrayList<>();
    private EditorServices services;
    private OpenDocument document;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT), preferences,
                new ComponentLibrary(), List.of(new MachineModeExtension((snapshot, instancePath) -> Optional.empty())));
        document = MachineFixtures.circuitWithGeneratedMachines(1);
        document.execute(new PutDefinitionsCommand(List.of(MachineDefinitions.regenerated(
                MachineDefinitions.create(OTHER_ID, "Otra").withMachine(StateMachine.initial())))));
    }

    @AfterEach
    void close() {
        views.forEach(EditorView::dispose);
        places.forEach(OpenDocument::close);
        document.close();
    }

    private EditorView shown(EditorView view) {
        view.setSize(1200, 800);
        view.doLayout();
        views.add(view);
        return view;
    }

    private OpenDocument machineTab() {
        OpenDocument place = document.openBody(MachineFixtures.DEFINITION_ID,
                Optional.of(MachineFixtures.copies(document).get(0)));
        places.add(place);
        return place;
    }

    private String machineName() {
        return document.document().definition(MachineFixtures.DEFINITION_ID).map(ComponentDefinition::name)
                .orElseThrow();
    }

    private static void type(JTextField field, String text) {
        field.setText(text);
        field.postActionEvent();
    }

    @Test
    void theMachinesNameIsEditedInItsTabInOneUndoStepAndTheTabFollows() {
        OpenDocument place = machineTab();
        EditorView view = shown(new EditorView(place, services, NO_WINDOW));
        MachineInspectorRows rows = MachineMode.of(view).orElseThrow().inspectorRows();
        JTextField field = rows.machineNameField().orElseThrow();
        assertThat(field.getText()).isEqualTo("Detector 11");

        type(field, "  Semáforo  ");

        assertThat(machineName()).isEqualTo("Semáforo");
        assertThat(place.definitionName()).as("the tab's title").isEqualTo("Semáforo");
        assertThat(InstanceNames.route(MachineFixtures.copies(document).get(0), document.document()))
                .as("the breadcrumb and «Valores de»").containsExactly("Semáforo");
        assertThat(rows.problemText()).isEmpty();
        perform(view.actions().undo());
        assertThat(machineName()).isEqualTo("Detector 11");
        assertThat(rows.machineNameField().orElseThrow().getText()).isEqualTo("Detector 11");
    }

    @Test
    void aTakenOrEmptyNameIsRefusedWithTheComponentsReason() {
        EditorView view = shown(new EditorView(machineTab(), services, NO_WINDOW));
        MachineInspectorRows rows = MachineMode.of(view).orElseThrow().inspectorRows();

        type(rows.machineNameField().orElseThrow(), "otra");

        assertThat(machineName()).isEqualTo("Detector 11");
        assertThat(rows.problemText()).contains("Ya existe un componente llamado «otra».");

        type(rows.machineNameField().orElseThrow(), "   ");

        assertThat(machineName()).isEqualTo("Detector 11");
        assertThat(rows.problemText()).contains(Messages.text("template.name.empty"));
    }

    @Test
    void thePieceInTheCircuitHasTheMachinesNameToo() {
        EditorView view = shown(new EditorView(document, services, NO_WINDOW));
        view.selection().select(new ElementId(MachineFixtures.copies(document).get(0)));
        JTextField field = (JTextField) find(view.labelFeatures().properties(), MachineNameRows.FIELD_NAME);
        assertThat(field).isNotNull();
        assertThat(field.getText()).isEqualTo("Detector 11");

        type(field, "Semáforo");

        assertThat(machineName()).isEqualTo("Semáforo");
        perform(view.actions().undo());
        assertThat(machineName()).isEqualTo("Detector 11");

        type((JTextField) find(view.labelFeatures().properties(), MachineNameRows.FIELD_NAME), "Otra");

        assertThat(machineName()).isEqualTo("Detector 11");
        JTextComponent problem = (JTextComponent) find(view.labelFeatures().properties(), MachineNameRows.PROBLEM_NAME);
        assertThat(problem.getText()).isEqualTo("Ya existe un componente llamado «Otra».");
    }

    private static java.awt.Component find(Container root, String name) {
        for (java.awt.Component child : root.getComponents()) {
            if (name.equals(child.getName())) {
                return child;
            }
            if (child instanceof Container container) {
                java.awt.Component nested = find(container, name);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }
}
