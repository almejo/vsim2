package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.io.MachineFragmentFormat;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.clipboard.FragmentTransferable;
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
import javax.swing.TransferHandler;
import javax.swing.text.JTextComponent;
import java.awt.Container;
import java.awt.datatransfer.StringSelection;
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
    private final ComponentLibrary library = new ComponentLibrary();
    private EditorServices services;
    private OpenDocument document;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT), preferences,
                library, List.of(new MachineModeExtension((snapshot, instancePath) -> Optional.empty())));
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

    /**
     * A click elsewhere changes the selection (and rebuilds Propiedades) before the field hears it lost the focus:
     * what was typed is applied first, as the label's field is (review of the hand fixes, I4).
     */
    @Test
    void aNameTypedOnThePieceIsKeptWhenTheSelectionChanges() {
        EditorView view = shown(new EditorView(document, services, NO_WINDOW));
        view.selection().select(new ElementId(MachineFixtures.copies(document).get(0)));
        ((JTextField) find(view.labelFeatures().properties(), MachineNameRows.FIELD_NAME)).setText("Semáforo");

        view.selection().clear();

        assertThat(machineName()).isEqualTo("Semáforo");
    }

    @Test
    void namesTypedInTheTabAreKeptWhenTheSelectionChanges() {
        EditorView view = shown(new EditorView(machineTab(), services, NO_WINDOW));
        MachineMode mode = MachineMode.of(view).orElseThrow();
        MachineInspectorRows rows = mode.inspectorRows();
        rows.machineNameField().orElseThrow().setText("Semáforo");

        mode.selection().selectState("s0");

        assertThat(machineName()).isEqualTo("Semáforo");
        rows.nameField().orElseThrow().setText("Inicio");

        mode.selection().selectState("s1");

        assertThat(mode.canvas().machine().orElseThrow().state("s0").orElseThrow().name()).isEqualTo("Inicio");
    }

    /**
     * A refused name stays while the user fixes it, but leaving the field puts back the name the machine has, with
     * the reason still under it (review, M3): the field never shows a name the machine does not have.
     */
    @Test
    void leavingAFieldWithARefusedNamePutsTheMachinesNameBack() {
        EditorView view = shown(new EditorView(machineTab(), services, NO_WINDOW));
        MachineInspectorRows rows = MachineMode.of(view).orElseThrow().inspectorRows();
        JTextField field = rows.machineNameField().orElseThrow();
        type(field, "otra");
        assertThat(field.getText()).isEqualTo("otra");

        loseFocus(field);

        assertThat(field.getText()).isEqualTo("Detector 11");
        assertThat(rows.problemText()).contains("Ya existe un componente llamado «otra».");
    }

    /** What a focus change tells {@code field}, as if it were on screen (headless tests show no window). */
    private static void loseFocus(JTextField field) {
        for (java.awt.event.FocusListener listener : field.getFocusListeners()) {
            if (listener instanceof java.awt.event.FocusAdapter) {
                listener.focusLost(new java.awt.event.FocusEvent(field, java.awt.event.FocusEvent.FOCUS_LOST));
            }
        }
    }

    /**
     * A name «Mis componentes» already uses for another component is taken too, as in the palette's «Renombrar…»
     * (review of the hand fixes, I3): the palette would show two entries with one name.
     */
    @Test
    void aNameOfAnotherComponentInTheLibraryIsTaken() {
        library.put(DefinitionBundle.of(MachineDefinitions.regenerated(MachineDefinitions.create("contador",
                "Contador").withMachine(StateMachine.initial())), id -> Optional.empty()));
        EditorView view = shown(new EditorView(machineTab(), services, NO_WINDOW));
        MachineInspectorRows rows = MachineMode.of(view).orElseThrow().inspectorRows();

        type(rows.machineNameField().orElseThrow(), "contador");

        assertThat(machineName()).isEqualTo("Detector 11");
        assertThat(rows.problemText()).contains("Ya existe un componente llamado «contador».");
    }

    /**
     * A machine that is in «Mis componentes» shows there with the library's name, so Propiedades does not rename it
     * (the palette and the library would keep the old name): it says where it is renamed (I3).
     */
    @Test
    void aMachineInTheLibraryIsRenamedFromThePaletteAndPropertiesSaysSo() {
        ComponentDefinition machine = document.document().definition(MachineFixtures.DEFINITION_ID).orElseThrow();
        library.put(DefinitionBundle.of(machine, document.document()::definition));
        EditorView tabView = shown(new EditorView(machineTab(), services, NO_WINDOW));
        MachineInspectorRows rows = MachineMode.of(tabView).orElseThrow().inspectorRows();

        type(rows.machineNameField().orElseThrow(), "Detector 11");
        assertThat(rows.problemText()).as("keeping the name is fine").isEmpty();
        type(rows.machineNameField().orElseThrow(), "Semáforo");

        assertThat(machineName()).isEqualTo("Detector 11");
        String reason = Messages.format("machine.name.inLibrary", "Detector 11");
        assertThat(rows.problemText()).contains(reason);

        EditorView circuitView = shown(new EditorView(document, services, NO_WINDOW));
        circuitView.selection().select(new ElementId(MachineFixtures.copies(document).get(0)));
        type((JTextField) find(circuitView.labelFeatures().properties(), MachineNameRows.FIELD_NAME), "Semáforo");
        assertThat(machineName()).isEqualTo("Detector 11");
        assertThat(((JTextComponent) find(circuitView.labelFeatures().properties(), MachineNameRows.PROBLEM_NAME))
                .getText()).isEqualTo(reason);
    }

    /**
     * Copied states travel as a fragment that is plain text too: Ctrl+V of them into a name or condition field must
     * not type their JSON there (review of the hand fixes, I2), while text from another program still pastes.
     */
    @Test
    void theMachinesTextFieldsRefuseCopiedStatesButTakePlainText() {
        EditorView circuitView = shown(new EditorView(document, services, NO_WINDOW));
        circuitView.selection().select(new ElementId(MachineFixtures.copies(document).get(0)));
        EditorView tabView = shown(new EditorView(machineTab(), services, NO_WINDOW));
        MachineMode mode = MachineMode.of(tabView).orElseThrow();
        List<JTextComponent> fields = new ArrayList<>();
        fields.add((JTextField) find(circuitView.labelFeatures().properties(), MachineNameRows.FIELD_NAME));
        fields.add(mode.inspectorRows().machineNameField().orElseThrow());
        mode.selection().selectState("s0");
        fields.add(mode.inspectorRows().nameField().orElseThrow());
        fields.add(mode.inspectorRows().outputsField().orElseThrow());
        mode.selection().selectTransition(mode.canvas().machine().orElseThrow().transitions().get(0).id());
        fields.add(mode.inspectorRows().conditionField().orElseThrow());
        fields.add(mode.inspectorRows().mealyOutputsField().orElseThrow());
        fields.add(mode.inlineEditor().editor().field());
        fields.add(mode.inlineEditor().editor().area());
        FragmentTransferable copiedStates = new FragmentTransferable(MachineFragmentFormat.write(
                mode.canvas().machine().orElseThrow()));

        for (JTextComponent field : fields) {
            TransferHandler handler = field.getTransferHandler();
            assertThat(handler.canImport(new TransferHandler.TransferSupport(field, copiedStates))).isFalse();
            assertThat(handler.importData(new TransferHandler.TransferSupport(field, copiedStates))).isFalse();
            assertThat(handler.canImport(new TransferHandler.TransferSupport(field, new StringSelection("S9"))))
                    .isTrue();
        }
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
