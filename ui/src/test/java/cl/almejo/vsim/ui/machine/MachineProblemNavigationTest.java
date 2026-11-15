package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.MachineFinding;
import cl.almejo.vsim.core.simulation.MachineProblem;
import cl.almejo.vsim.core.simulation.Problem;
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
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A click on a machine's row in Problemas (plan 2026-11-14, hito 2 task 8): from the circuit's tab it opens the
 * machine's tab at the copy the problem is about (the first copy for a problem of the model, «Sin valores» when no
 * piece places it) and selects what is wrong there.
 */
class MachineProblemNavigationTest {

    private static final String WRAPPER = "envoltorio";

    @TempDir
    Path directory;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<EditorView> views = new ArrayList<>();
    private final List<OpenDocument> places = new ArrayList<>();
    private final List<String> shown = new ArrayList<>();
    private LibraryService service;
    private EditorServices services;
    private OpenDocument circuit;

    /** The window's tabs: a place shown is a new view of the body, as a real window opens it. */
    private final WindowCommands window = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }

        @Override
        public Optional<EditorView> showPlace(OpenDocument document, Optional<String> definitionId,
                                              Optional<String> contextPath) {
            shown.add(definitionId.orElse("-") + " " + contextPath.orElse("-"));
            if (definitionId.isEmpty()) {
                return Optional.empty();
            }
            OpenDocument body = document.openBody(definitionId.get(), contextPath);
            places.add(body);
            return Optional.of(view(body));
        }
    };

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
        circuit = OpenDocument.createEmpty(registry);
    }

    @AfterEach
    void close() {
        views.forEach(EditorView::dispose);
        places.forEach(OpenDocument::close);
        circuit.close();
        service.close();
    }

    private EditorView view(OpenDocument shownPlace) {
        EditorView created = new EditorView(shownPlace, services, window);
        created.setSize(1200, 800);
        created.doLayout();
        views.add(created);
        return created;
    }

    private void putTheTroubledMachine() {
        circuit.execute(new PutDefinitionsCommand(List.of(
                MachineDefinitions.regenerated(MachineFixtures.definition(MachineProblemsOnDiagramTest.troubled())))));
    }

    /** The troubled machine as piece {@code m} inside piece {@code w} of the circuit. */
    private void nestTheMachine() {
        putTheTroubledMachine();
        Component copy = new Component(new ElementId("m"), SubcircuitTypes.typeIdOf(MachineFixtures.DEFINITION_ID),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        circuit.execute(new PutDefinitionsCommand(List.of(new ComponentDefinition(WRAPPER, "Envoltorio", "",
                new GridSize(4, 4), List.of(), new CircuitFragment(List.of(copy), List.of(), List.of())))));
        circuit.execute(new cl.almejo.vsim.core.command.AddElementsCommand(List.of(new Component(new ElementId("w"),
                SubcircuitTypes.typeIdOf(WRAPPER), new GridPoint(40, 0), Rotation.R0, Map.of(), ""))));
    }

    private static MachineProblem problemOf(EditorView view, MachineFinding.Kind kind) {
        for (Problem problem : view.problems().navigator().problems()) {
            if (problem instanceof MachineProblem machine && machine.finding().kind() == kind) {
                return machine;
            }
        }
        throw new AssertionError("no " + kind + " in " + view.problems().navigator().problems());
    }

    private EditorView lastOpened() {
        return views.getLast();
    }

    @Test
    void aClickOnTheRowOfANestedCopyOpensTheMachineTabThereAndSelectsTheStates() {
        nestTheMachine();
        EditorView main = view(circuit);

        assertThat(main.problems().navigator().navigateTo(
                problemOf(main, MachineFinding.Kind.UNREACHABLE_STATE))).isTrue();

        assertThat(shown).containsExactly(MachineFixtures.DEFINITION_ID + " w/m");
        EditorView opened = lastOpened();
        assertThat(opened.openDocument().contextPath()).contains("w/m");
        MachineMode mode = MachineMode.of(opened).orElseThrow();
        assertThat(mode.selection().stateIds()).containsExactly("d");
    }

    @Test
    void aClickOnAnErrorOfTheMachineSelectsItsArrows() {
        nestTheMachine();
        EditorView main = view(circuit);

        main.problems().navigator().navigateTo(problemOf(main, MachineFinding.Kind.NONDETERMINISTIC));

        assertThat(MachineMode.of(lastOpened()).orElseThrow().selection().transitionIds())
                .containsExactlyInAnyOrder("t1", "t2");
    }

    @Test
    void aMachineNoPieceUsesIsOpenedWithoutValues() {
        putTheTroubledMachine();
        EditorView main = view(circuit);

        main.problems().navigator().navigateTo(problemOf(main, MachineFinding.Kind.UNREACHABLE_STATE));

        assertThat(shown).containsExactly(MachineFixtures.DEFINITION_ID + " -");
        assertThat(MachineMode.of(lastOpened()).orElseThrow().selection().stateIds()).containsExactly("d");
    }

    @Test
    void inTheMachinesOwnTabTheRowRevealsInPlace() {
        nestTheMachine();
        OpenDocument body = circuit.openBody(MachineFixtures.DEFINITION_ID, Optional.of("w/m"));
        places.add(body);
        EditorView machineTab = view(body);

        machineTab.problems().navigator().navigateTo(problemOf(machineTab, MachineFinding.Kind.UNREACHABLE_STATE));

        assertThat(shown).isEmpty();
        assertThat(MachineMode.of(machineTab).orElseThrow().selection().stateIds()).containsExactly("d");
    }
}
