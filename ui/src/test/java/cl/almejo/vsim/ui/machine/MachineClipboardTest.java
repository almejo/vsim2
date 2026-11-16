package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.Application;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * Copiar, Cortar, Pegar, Duplicar and Seleccionar todo in a machine's tab (hand test of hito 2): the window's own
 * actions and keys act on the diagram's selection; the copied states take the arrows between them; a paste gives new
 * ids, free names and a place beside the originals, selected, in one undo step; pasting works into another machine,
 * and a circuit's parts and a machine's states each refuse the other's tab with a reason on the status line.
 */
class MachineClipboardTest {

    private static final String OTHER_ID = "otra";

    @TempDir
    Path directory;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<EditorView> views = new ArrayList<>();
    private final List<OpenDocument> places = new ArrayList<>();
    private LibraryService service;
    private EditorServices services;
    private OpenDocument document;
    private EditorView view;
    private MachineMode mode;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        preferences.setLibraryDirectory(Optional.of(directory.resolve("library")));
        ComponentLibrary library = new ComponentLibrary();
        service = new LibraryService(preferences, library, registry, Runnable::run);
        List<EditorExtension> extensions = new ArrayList<>(
                Application.editorExtensions(service, SwingLibraryDialogs.INSTANCE).stream()
                        .filter(extension -> !(extension instanceof MachineModeExtension))
                        .toList());
        extensions.add(new MachineModeExtension((snapshot, instancePath) -> Optional.empty()));
        services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT), preferences,
                library, extensions);
        document = MachineFixtures.circuitWithGeneratedMachines(1);
        document.execute(new PutDefinitionsCommand(List.of(MachineDefinitions.regenerated(
                MachineDefinitions.create(OTHER_ID, "Otra").withMachine(StateMachine.initial())))));
        view = machineTab(MachineFixtures.DEFINITION_ID, Optional.of(MachineFixtures.copies(document).get(0)));
        mode = MachineMode.of(view).orElseThrow();
    }

    private EditorView machineTab(String definitionId, Optional<String> copy) {
        OpenDocument place = document.openBody(definitionId, copy);
        places.add(place);
        return shown(new EditorView(place, services, NO_WINDOW));
    }

    private EditorView shown(EditorView shownView) {
        shownView.setSize(1200, 800);
        shownView.doLayout();
        views.add(shownView);
        MachineMode.of(shownView).ifPresent(found -> found.canvas().setBounds(0, 0, 900, 600));
        return shownView;
    }

    @AfterEach
    void close() {
        views.forEach(EditorView::dispose);
        places.forEach(OpenDocument::close);
        document.close();
        service.close();
    }

    private StateMachine machine() {
        return mode.canvas().machine().orElseThrow();
    }

    private void select(String... stateIds) {
        mode.selection().set(new LinkedHashSet<>(List.of(stateIds)), new LinkedHashSet<>());
    }

    private MachineState named(StateMachine machine, String name) {
        return machine.states().stream().filter(state -> state.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void copyAndPasteGiveNewStatesWithTheArrowsBetweenThemSelectedInOneUndoStep() {
        mode.selection().set(new LinkedHashSet<>(List.of("s1", "s2")), new LinkedHashSet<>(Set.of("t1")));

        perform(view.selectionActions().copy());
        perform(view.selectionActions().paste());

        StateMachine pasted = machine();
        assertThat(pasted.states()).extracting(MachineState::name).containsExactly("S0", "S1", "S2", "S3", "S4");
        MachineState copyOfS1 = named(pasted, "S3");
        MachineState copyOfS2 = named(pasted, "S4");
        assertThat(copyOfS1.id()).isNotIn("s0", "s1", "s2");
        assertThat(copyOfS2.outputs()).isEqualTo(pasted.state("s2").orElseThrow().outputs());
        // moved down and right together, clear of every state that was there
        MachineState s1 = pasted.state("s1").orElseThrow();
        MachineState s2 = pasted.state("s2").orElseThrow();
        double shift = copyOfS1.position().x() - s1.position().x();
        assertThat(shift).isPositive();
        assertThat(copyOfS1.position().y() - s1.position().y()).isCloseTo(shift, offset(0.5));
        assertThat(copyOfS2.position().x() - s2.position().x()).isCloseTo(shift, offset(0.5));
        MachineDiagramGeometry geometry = MachineDiagramGeometry.of(pasted);
        for (MachineState copy : List.of(copyOfS1, copyOfS2)) {
            for (String original : List.of("s0", "s1", "s2")) {
                assertThat(geometry.centre(copy.id()).distance(geometry.centre(original))
                        - geometry.radius(copy.id()) - geometry.radius(original))
                        .isGreaterThanOrEqualTo(MachineEdits.PASTE_GAP);
            }
        }
        // only S1 → S2 and S2's loop join copied states; t1 comes from S0, which was not copied
        List<MachineTransition> added = pasted.transitions().subList(6, pasted.transitions().size());
        assertThat(added).extracting(MachineTransition::condition).containsExactly("x", "x");
        assertThat(added.get(0).fromId()).isEqualTo(copyOfS1.id());
        assertThat(added.get(0).toId()).isEqualTo(copyOfS2.id());
        assertThat(added.get(1).isSelfLoop()).isTrue();
        assertThat(mode.selection().stateIds()).containsExactlyInAnyOrder(copyOfS1.id(), copyOfS2.id());
        assertThat(mode.selection().transitionIds()).containsExactlyInAnyOrder(added.get(0).id(), added.get(1).id());

        perform(view.actions().undo());

        assertThat(machine().states()).hasSize(3);
    }

    @Test
    void aTakenNameGetsTheNextNumberAndEachPasteGoesFurther() {
        mode.canvas().edit(current -> MachineEdits.withStateName(current, "s1", "AMARILLO"));
        select("s1");

        perform(view.selectionActions().copy());
        perform(view.selectionActions().paste());
        perform(view.selectionActions().paste());

        assertThat(machine().states()).extracting(MachineState::name)
                .containsExactly("S0", "AMARILLO", "S2", "AMARILLO (2)", "AMARILLO (3)");
        DiagramPoint second = named(machine(), "AMARILLO (2)").position();
        DiagramPoint third = named(machine(), "AMARILLO (3)").position();
        assertThat(third.x()).isGreaterThan(second.x());
        assertThat(third.y()).isGreaterThan(second.y());
    }

    @Test
    void duplicateCopiesTheSelectionBesideItWithoutTheClipboard() {
        select("s2");

        perform(view.selectionActions().duplicate());

        assertThat(machine().states()).extracting(MachineState::name).containsExactly("S0", "S1", "S2", "S3");
        assertThat(machine().transitions()).hasSize(7); // S2's loop came along
        perform(view.actions().undo());
        assertThat(machine().states()).hasSize(3);
    }

    @Test
    void cutTakesTheStatesWithTheirArrowsInOneUndoStepAndPasteBringsThemBack() {
        select("s1", "s2");

        perform(view.selectionActions().cut());

        assertThat(machine().states()).extracting(MachineState::name).containsExactly("S0");
        assertThat(machine().transitions()).extracting(MachineTransition::id).containsExactly("t6");
        perform(view.actions().undo());
        assertThat(machine().states()).hasSize(3);
        perform(view.actions().redo());

        perform(view.selectionActions().paste());

        assertThat(machine().states()).extracting(MachineState::name).containsExactly("S0", "S1", "S2");
        assertThat(named(machine(), "S1").position()).as("nothing in the way: where it was")
                .isEqualTo(new DiagramPoint(160, 0));
    }

    @Test
    void aPasteIntoAnotherMachineBringsItsNamesAsPins() {
        select("s1", "s2");
        perform(view.selectionActions().copy());
        EditorView other = machineTab(OTHER_ID, Optional.empty());
        MachineMode otherMode = MachineMode.of(other).orElseThrow();

        perform(other.selectionActions().paste());

        StateMachine otherMachine = otherMode.canvas().machine().orElseThrow();
        assertThat(otherMachine.states()).extracting(MachineState::name).containsExactly("S0", "S1", "S2");
        assertThat(otherMachine.inputNames()).contains("x");
        assertThat(otherMachine.outputNames()).contains("Z");
        assertThat(otherMode.selection().stateIds()).hasSize(2);
    }

    /** Sixteen outputs is the most a machine has (review of the hand fixes, M5): a paste that needs one more says so. */
    @Test
    void aPasteThatNeedsMoreThanSixteenOutputsIsRefusedWithTheReason() {
        select("s1", "s2"); // S2 sets Z
        perform(view.selectionActions().copy());
        EditorView other = machineTab(OTHER_ID, Optional.empty());
        MachineMode otherMode = MachineMode.of(other).orElseThrow();
        List<String> sixteen = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            sixteen.add("O" + index);
        }
        otherMode.canvas().edit(current -> current.withPinNames(current.inputNames(), sixteen));

        perform(other.selectionActions().paste());

        assertThat(otherMode.canvas().machine().orElseThrow().states()).hasSize(1);
        assertThat(other.statusBar().text()).contains(Messages.format("machine.limit.tooManyOutputs", 16));
    }

    /** Sixty-four states is the most: «Nuevo estado» on a full machine says why nothing appears (M5). */
    @Test
    void aNewStateOnAFullMachineSaysWhyNothingAppears() {
        mode.canvas().edit(current -> {
            StateMachine full = current;
            while (full.states().size() < 64) {
                full = MachineEdits.withNewState(full, new DiagramPoint(full.states().size() * 100, 400));
            }
            return full;
        });

        perform(mode.tool().newStateAction());

        assertThat(machine().states()).hasSize(64);
        assertThat(view.statusBar().text()).contains(Messages.format("machine.limit.tooManyStates", 64));
    }

    /** A new output name in the arrow's outputs beyond the sixteenth is refused in Propiedades with the reason (M5). */
    @Test
    void aSeventeenthOutputTypedInPropertiesIsRefusedWithTheReason() {
        List<String> outputs = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            outputs.add("O" + index);
        }
        mode.canvas().edit(current -> current.withPinNames(current.inputNames(), outputs));
        mode.selection().selectTransition("t3");
        MachineInspectorRows rows = mode.inspectorRows();
        javax.swing.JTextField mealy = rows.mealyOutputsField().orElseThrow();

        mealy.setText("W=1");
        mealy.postActionEvent();

        assertThat(machine().transition("t3").orElseThrow().outputs()).isEmpty();
        assertThat(rows.problemText()).contains(Messages.format("machine.limit.tooManyOutputs", 16));

        mode.inlineEditor().editCondition("t3");
        mode.inlineEditor().editor().textComponent().setText("x / W=1");

        assertThat(mode.inlineEditor().editor().problemText().getText())
                .isEqualTo(Messages.format("machine.limit.tooManyOutputs", 16));
    }

    @Test
    void aCircuitsPartsAreNotPastedIntoAMachine() {
        EditorView circuit = shown(new EditorView(document, services, NO_WINDOW));
        circuit.canvas().selection().set(Set.of(new ElementId(MachineFixtures.copies(document).get(0))));
        perform(circuit.selectionActions().copy());

        perform(view.selectionActions().paste());

        assertThat(machine().states()).hasSize(3);
        assertThat(view.statusBar().text()).contains("estados y flechas");
    }

    @Test
    void aMachinesStatesAreNotPastedIntoACircuit() {
        select("s1");
        perform(view.selectionActions().copy());
        EditorView circuit = shown(new EditorView(document, services, NO_WINDOW));
        int parts = document.document().components().size();

        perform(circuit.selectionActions().paste());

        assertThat(document.document().components()).hasSize(parts);
        assertThat(circuit.statusBar().text()).contains("diagrama de una máquina");
    }

    @Test
    void theActionsWorkOnTheDiagramAndCopyNeedsAState() {
        mode.selection().clear();
        assertThat(view.selectionActions().copy().isEnabled()).isFalse();
        assertThat(view.selectionActions().paste().isEnabled()).isTrue();

        perform(view.selectionActions().selectAll());

        assertThat(mode.selection().stateIds()).containsExactlyInAnyOrder("s0", "s1", "s2");
        assertThat(mode.selection().transitionIds()).hasSize(6);
        assertThat(view.selectionActions().copy().isEnabled()).isTrue();
        assertThat(view.selectionActions().cut().isEnabled()).isTrue();
        assertThat(view.selectionActions().duplicate().isEnabled()).isTrue();
    }
}
