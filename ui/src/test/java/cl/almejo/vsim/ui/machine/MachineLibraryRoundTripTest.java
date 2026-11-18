package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.EditMachineCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.machine.MachineLimits;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.MachineTests;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.TemplatesExtension;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.geom.Point2D;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A machine through «Mis componentes» (hito 3): added from its piece's menu, read back from its file in another
 * session, placed in a new circuit where it simulates, edited there, and put back as the library has it with
 * «Actualizar desde la librería»; renamed from Propiedades, its file is renamed too.
 */
class MachineLibraryRoundTripTest {

    /** A switch starts at 1 by default; the clock and {@code x} start at 0. */
    private static final Map<String, Object> SWITCH_OFF = Map.of("value", "0");

    @TempDir
    Path directory;

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<AutoCloseable> closing = new ArrayList<>();

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        preferences.setLibraryDirectory(Optional.of(directory.resolve("library")));
    }

    @AfterEach
    void close() throws Exception {
        for (int index = closing.size() - 1; index >= 0; index--) {
            closing.get(index).close();
        }
    }

    /** A session: its own library read from the directory, and a window on {@code document}. */
    private record Session(ComponentLibrary library, LibraryService service, EditorView view) {
    }

    private Session session(OpenDocument document) {
        ComponentLibrary library = new ComponentLibrary();
        LibraryService service = new LibraryService(preferences, library, StandardComponentTypes.registry(),
                Runnable::run);
        service.reload();
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, library, List.of(new TemplatesExtension(service, new QuietLibraryDialogs()),
                new MachineModeExtension((snapshot, instancePath) -> Optional.empty())));
        EditorView view = new EditorView(document, services, NO_WINDOW);
        view.setSize(1200, 800);
        view.doLayout();
        closing.add(document::close);
        closing.add(service);
        closing.add(view::dispose);
        return new Session(library, service, view);
    }

    private static JMenuItem item(JPopupMenu menu, String text) {
        for (java.awt.Component child : menu.getComponents()) {
            if (child instanceof JMenuItem entry && text.equals(entry.getText())) {
                return entry;
            }
        }
        throw new AssertionError("no item " + text);
    }

    /** The detector saved to the library from its piece's menu, as another session reads it back. */
    private DefinitionBundle savedAndReadBack() {
        OpenDocument first = MachineFixtures.circuitWithGeneratedMachines(1);
        Session saving = session(first);
        Component piece = first.document().component(new ElementId(MachineFixtures.copies(first).get(0)))
                .orElseThrow();
        saving.view().selection().select(piece.id());
        item(saving.view().contextMenu(Optional.of(piece), new Point2D.Double()),
                Messages.text("action.addToLibrary")).doClick();
        return session(OpenDocument.createEmpty(StandardComponentTypes.registry())).library()
                .find(MachineFixtures.DEFINITION_ID).orElseThrow();
    }

    @Test
    void aMachineReadBackFromItsFileHasItsDiagramAndItsPins() {
        DefinitionBundle bundle = savedAndReadBack();

        ComponentDefinition machine = bundle.root();
        assertThat(machine.machine()).contains(MachineFixtures.detector());
        assertThat(machine.pinout()).extracting(pin -> pin.spec().name())
                .containsExactlyInAnyOrder("x", MachineLimits.RESET, MachineLimits.CLOCK, "Z");
    }

    @Test
    void placedInANewCircuitItSimulatesIsEditedAndComesBackFromTheLibrary() {
        DefinitionBundle bundle = savedAndReadBack();
        OpenDocument fresh = OpenDocument.createEmpty(StandardComponentTypes.registry());
        Session session = session(fresh);
        fresh.execute(new PutDefinitionsCommand(bundle.all()));
        ComponentDefinition machine = bundle.root();
        Component piece = UiTestSupport.place(fresh, SubcircuitTypes.typeIdOf(machine.id()), 20, 0, Map.of());
        Component input = switchOn(fresh, piece, machine, "x", "input");
        Component clock = switchOn(fresh, piece, machine, MachineLimits.CLOCK, "clock");
        OpenDocument place = fresh.openBody(machine.id(), Optional.of(piece.id().value()));
        closing.add(place::close);
        MachineCanvas canvas = new MachineCanvas(place, machine.id(), new MachineSelection(), () -> ColorScheme.LIGHT);
        closing.add(canvas::dispose);

        fresh.interact(input.id());
        fresh.interact(clock.id());
        fresh.interact(clock.id());
        UiTestSupport.awaitSimulation(fresh);
        canvas.showLatestState();
        assertThat(canvas.activeState()).hasValueSatisfying(state -> assertThat(state.stateId()).isEqualTo("s1"));

        StateMachine edited = machine.machine().orElseThrow().withState("s0",
                state -> new MachineState(state.id(), "Inicio", state.position(), state.outputs()));
        place.execute(new EditMachineCommand(machine.id(), edited));
        assertThat(stateName(fresh, machine.id())).isEqualTo("Inicio");
        assertThat(session.view().templateActions().orElseThrow().isOutdated(piece)).isTrue();

        session.view().templateActions().orElseThrow().updateFromLibrary(piece);

        assertThat(stateName(fresh, machine.id())).isEqualTo("S0");
    }

    // --- tests live only in the circuit (the user's rule, hito 4) ---

    @Test
    void aMachineAddedToTheLibraryLeavesItsTestsInTheCircuit() {
        OpenDocument first = MachineFixtures.circuitWithGeneratedMachines(1);
        first.execute(new EditMachineCommand(MachineFixtures.DEFINITION_ID, MachineFixtures.detector()
                .withTests(List.of(oneTest()))));
        Session saving = session(first);
        Component piece = first.document().component(new ElementId(MachineFixtures.copies(first).get(0)))
                .orElseThrow();
        saving.view().selection().select(piece.id());

        item(saving.view().contextMenu(Optional.of(piece), new Point2D.Double()),
                Messages.text("action.addToLibrary")).doClick();

        assertThat(session(OpenDocument.createEmpty(StandardComponentTypes.registry())).library()
                .find(MachineFixtures.DEFINITION_ID).orElseThrow().root().machine().orElseThrow().tests()).isEmpty();
        assertThat(saving.library().find(MachineFixtures.DEFINITION_ID).orElseThrow().root().machine()
                .orElseThrow().tests()).isEmpty();
        assertThat(tests(first)).containsExactly(oneTest());
        assertThat(saving.view().templateActions().orElseThrow().isOutdated(piece)).as("tests do not count")
                .isFalse();
    }

    @Test
    void updatingFromTheLibraryKeepsTheCircuitsOwnTests() {
        DefinitionBundle bundle = savedAndReadBack();
        OpenDocument fresh = OpenDocument.createEmpty(StandardComponentTypes.registry());
        Session session = session(fresh);
        fresh.execute(new PutDefinitionsCommand(bundle.all()));
        ComponentDefinition machine = bundle.root();
        Component piece = UiTestSupport.place(fresh, SubcircuitTypes.typeIdOf(machine.id()), 20, 0, Map.of());
        StateMachine edited = machine.machine().orElseThrow().withState("s0", state -> state.withName("Inicio"))
                .withTests(List.of(oneTest()));
        fresh.execute(new EditMachineCommand(machine.id(), edited));
        assertThat(session.view().templateActions().orElseThrow().isOutdated(piece)).isTrue();

        session.view().templateActions().orElseThrow().updateFromLibrary(piece);

        assertThat(stateName(fresh, machine.id())).isEqualTo("S0");
        assertThat(tests(fresh)).containsExactly(oneTest());
    }

    /** A test that sets x in its first column and expects Z = 1 in its second. */
    private static MachineTest oneTest() {
        MachineTest test = MachineTests.withInput(MachineTest.empty("p1", "Dos 1 seguidos", 2), 0, "x", true);
        return MachineTests.withExpected(test, 1, "Z", LogicValue.ONE);
    }

    private static List<MachineTest> tests(OpenDocument document) {
        return document.document().definition(MachineFixtures.DEFINITION_ID).flatMap(ComponentDefinition::machine)
                .orElseThrow().tests();
    }

    @Test
    void renamingAMachineOfTheLibraryFromItsPieceRenamesItsFile() {
        savedAndReadBack();
        OpenDocument document = MachineFixtures.circuitWithGeneratedMachines(1);
        Session session = session(document);
        session.view().selection().select(new ElementId(MachineFixtures.copies(document).get(0)));
        javax.swing.JTextField field = (javax.swing.JTextField) find(session.view().labelFeatures().properties(),
                MachineNameRows.FIELD_NAME);

        field.setText("Semáforo");
        field.postActionEvent();

        assertThat(session(OpenDocument.createEmpty(StandardComponentTypes.registry())).library()
                .find(MachineFixtures.DEFINITION_ID).orElseThrow().root().name()).isEqualTo("Semáforo");
        UiTestSupport.perform(session.view().actions().undo());
        assertThat(session(OpenDocument.createEmpty(StandardComponentTypes.registry())).library()
                .find(MachineFixtures.DEFINITION_ID).orElseThrow().root().name()).isEqualTo("Detector 11");
    }

    private static String stateName(OpenDocument document, String definitionId) {
        return document.document().definition(definitionId).flatMap(ComponentDefinition::machine).orElseThrow()
                .state("s0").orElseThrow().name();
    }

    /** A switch at 0 wired to {@code pinName} of {@code piece} from 4 units to its left. */
    private static Component switchOn(OpenDocument document, Component piece, ComponentDefinition machine,
                                      String pinName, String probeId) {
        GridPoint pin = document.registry().typeOf(piece).pinLocation(piece,
                machine.pinIndexOf(pinName).orElseThrow());
        GridPoint end = pin.translate(-4, 0);
        ComponentType switchType = document.registry().require(StandardComponentTypes.SWITCH);
        Component probe = switchType.newComponent(new ElementId(probeId), GridPoint.ORIGIN, Rotation.R0, SWITCH_OFF,
                "");
        GridPoint offset = switchType.pinLocation(probe, 0);
        Component placed = UiTestSupport.place(document, StandardComponentTypes.SWITCH, end.x() - offset.x(),
                end.y() - offset.y(), SWITCH_OFF);
        UiTestSupport.addWire(document, pin.x(), pin.y(), end.x(), end.y());
        return placed;
    }

    private static java.awt.Component find(java.awt.Container root, String name) {
        for (java.awt.Component child : root.getComponents()) {
            if (name.equals(child.getName())) {
                return child;
            }
            if (child instanceof java.awt.Container container) {
                java.awt.Component nested = find(container, name);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }
}
