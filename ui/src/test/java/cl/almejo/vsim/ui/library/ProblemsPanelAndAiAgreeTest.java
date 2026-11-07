package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.read.ProblemView;
import cl.almejo.vsim.automation.read.ProblemsReader;
import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.io.LibraryProblem;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.ai.EditorAutomationHost;
import cl.almejo.vsim.ui.ai.EditorWindows;
import cl.almejo.vsim.ui.ai.PreferenceProposals;
import cl.almejo.vsim.ui.ai.SettingsOverride;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.LoosenedPinsTestSupport;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.memory.LinkedMemoryWatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fix round D (5p): the AI reads every row the Problemas panel shows, no exceptions (rule: a problem is never hidden,
 * not from the AI either). One circuit with a row of every source — the simulation, the wiring, this session's loose
 * pins, pins placed elsewhere and wires of removed pins, the pieces a library update would break, linked files missing
 * and unreadable, pieces' own contents that do not apply — and a broken library file: the panel and
 * {@code vsim_read_problems} must list the same rows.
 */
class ProblemsPanelAndAiAgreeTest {

    /** A copy of the inverter under another id, edited inside: the library's inverter check stays apart from it. */
    private static final String EDITED = "editado";

    @TempDir
    Path directory;

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final List<OpenDocument> bodies = new ArrayList<>();
    private TemplateFixture fixture;

    @AfterEach
    void close() {
        bodies.forEach(OpenDocument::close);
        fixture.close();
    }

    @Test
    void thePanelAndTheAiListTheSameRowsOfEveryKind() throws Exception {
        fixture = new TemplateFixture(directory.resolve("library"), openDocument);
        withABrokenLibraryFile();
        withAShortCircuitAndALoneTunnel();
        withAPieceWhoseTerminalHasNothingInsideAndIsLoosened();
        withAPinPlacedElsewhereAndAWireOfARemovedPin();
        withAPieceALibraryUpdateWouldBreak();
        withLinkedFilesMissingAndUnreadable();
        withOwnContentsThatDoNotApply();
        SwingUtilities.invokeAndWait(() -> { });
        fixture.view.problems().showSnapshot(UiTestSupport.awaitSimulation(openDocument));

        List<String> panelKinds = Stream.concat(
                        fixture.view.problems().navigator().problems().stream().map(ProblemsReader::kindOf),
                        fixture.service.problems().stream().map(ProblemsReader::kindOf))
                .sorted().toList();
        List<String> aiKinds = aiProblems().stream().map(ProblemView::kind).sorted().toList();

        assertThat(fixture.view.problems().libraryProblems()).hasSize(fixture.service.problems().size());
        assertThat(aiKinds).as("the AI's rows are the panel's").isEqualTo(panelKinds);
        assertThat(aiKinds).contains("SHORT_CIRCUIT", "TUNNEL_ENTRANCE_WITHOUT_EXITS",
                "INTERFACE_UNCONNECTED_INSIDE", "PIN_LOOSENED_BY_SHAPE_CHANGE", "PIN_PLACED_ELSEWHERE",
                "WIRE_OF_REMOVED_PIN", "OUTDATED_CANNOT_UPDATE", "LINKED_FILE_MISSING", "LINKED_FILE_UNREADABLE",
                "OWN_CONTENTS_OVER_LINK", "OWN_CONTENTS_FOR_NOTHING", "LIBRARY_INVALID");
    }

    /** Two switches at 1 and 0 driving one wire, and a tunnel entrance nobody exits. */
    private void withAShortCircuitAndALoneTunnel() {
        Component one = UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 0, 40,
                Map.of(StandardComponentTypes.VALUE, "1"));
        Component zero = UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 0, 44,
                Map.of(StandardComponentTypes.VALUE, "0"));
        GridPoint first = pin(one, 0);
        GridPoint second = pin(zero, 0);
        UiTestSupport.addWire(openDocument, first.x(), first.y(), first.x() + 2, first.y());
        UiTestSupport.addWire(openDocument, first.x() + 2, first.y(), first.x() + 2, second.y());
        UiTestSupport.addWire(openDocument, second.x(), second.y(), first.x() + 2, second.y());
        UiTestSupport.place(openDocument, StandardComponentTypes.TUNNEL_ENTRANCE, 0, 60, Map.of(Tunnels.NAME, "SOLO"));
    }

    /** A piece whose only terminal has nothing inside, the terminal marked as loosened by a shape change. */
    private void withAPieceWhoseTerminalHasNothingInsideAndIsLoosened() {
        Component input = openDocument.registry().require(InterfacePins.INPUT_PIN).newComponent(
                new ElementId("interface"), GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        openDocument.execute(new PutDefinitionsCommand(List.of(new ComponentDefinition("interface-only", "Interfaz",
                "", new GridSize(4, 4), List.of(new DefinitionPin(PinSpec.input("A", 0, 1), input.id())),
                new CircuitFragment(List.of(input), List.of(), List.of())))));
        Component piece = place("c70", SubcircuitTypes.typeIdOf("interface-only"), 40, 40);
        LoosenedPinsTestSupport.set(openDocument, Set.of(new PinRef(piece.id(), 0)));
    }

    /**
     * A piece of {@value #EDITED} with a wire on its input «a» and another where a new input «b» goes: inside, «b» is
     * added (it goes elsewhere on the piece) and «a» removed (its wire stays loose).
     */
    private void withAPinPlacedElsewhereAndAWireOfARemovedPin() {
        ComponentDefinition inverter = ExampleDefinitions.inverter();
        openDocument.execute(new PutDefinitionsCommand(List.of(new ComponentDefinition(EDITED, "Editado", "",
                inverter.size(), inverter.pinout(), inverter.body()))));
        Component piece = place("c20", SubcircuitTypes.typeIdOf(EDITED), 10, 10);
        GridPoint input = pin(piece, 0);
        openDocument.execute(new AddElementsCommand(List.of(
                new Wire(new ElementId("w80"), new GridPoint(input.x() - 4, input.y()), input),
                new Wire(new ElementId("w90"), new GridPoint(6, 14), new GridPoint(10, 14)))));
        OpenDocument body = openDocument.openBody(EDITED, Optional.of(piece.id().value()));
        bodies.add(body);
        body.execute(new AddElementsCommand(List.of(openDocument.registry().require(InterfacePins.INPUT_PIN)
                .newComponent(body.document().newComponentId(), new GridPoint(0, 10), Rotation.R0, Map.of(),
                        "b"))));
        body.execute(new RemoveElementsCommand(List.of(new ElementId("c1"))));
    }

    /** A double inverter placed while the library has an inverter with its output moved: it cannot be updated. */
    private void withAPieceALibraryUpdateWouldBreak() throws Exception {
        openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter(),
                ExampleDefinitions.doubleInverter())));
        place("c90", SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER), 60, 10);
        ComponentDefinition inverter = ExampleDefinitions.inverter();
        DefinitionPin output = inverter.pinout().get(1);
        fixture.library.put(new DefinitionBundle(inverter.withGeometry(inverter.size(),
                List.of(inverter.pinout().get(0), new DefinitionPin(PinSpec.output("y", 4, 3), output.source())),
                inverter.appearance()), List.of()));
        SwingUtilities.invokeAndWait(() -> { });
    }

    /** A ROM linked to a file that is not there and a RAM linked to one that is not UTF-8, as the watcher sees them. */
    private void withLinkedFilesMissingAndUnreadable() throws Exception {
        Path unreadable = directory.resolve("roto.hex");
        Files.write(unreadable, new byte[]{(byte) 0xC3, (byte) 0x28});
        UiTestSupport.place(openDocument, StandardComponentTypes.ROM, 80, 40,
                Map.of(MemoryTypes.LINK, directory.resolve("falta.hex").toString()));
        UiTestSupport.place(openDocument, StandardComponentTypes.RAM, 100, 40,
                Map.of(MemoryTypes.LINK, unreadable.toString()));
        LinkedMemoryWatcher watcher = new LinkedMemoryWatcher(openDocument);
        watcher.poll();
        fixture.view.problems().showLinkedFileProblems(watcher.linkedFileProblems());
    }

    /** A piece keeping its own contents for its linked memory and for a memory its component no longer has. */
    private void withOwnContentsThatDoNotApply() {
        Component linkedMemory = openDocument.registry().require(StandardComponentTypes.RAM).newComponent(
                new ElementId("c10"), GridPoint.ORIGIN, Rotation.R0, Map.of(MemoryTypes.LINK, "programa.hex"),
                "Programa");
        openDocument.execute(new PutDefinitionsCommand(List.of(new ComponentDefinition("memoria-chica",
                "Memoria chica", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(linkedMemory), List.of(), List.of())))));
        Component piece = openDocument.registry().require(SubcircuitTypes.typeIdOf("memoria-chica")).newComponent(
                new ElementId("c60"), new GridPoint(120, 10), Rotation.R0, Map.of(), "Memoria 1");
        openDocument.execute(new AddElementsCommand(List.of(piece.withMemoryContents(
                Map.of("c10", "@0 9", "c99", "@0 8")))));
    }

    private void withABrokenLibraryFile() throws Exception {
        Path library = directory.resolve("library");
        Files.createDirectories(library);
        Files.writeString(library.resolve("roto.vsimcomponent.json"), "{ not json");
        fixture.service.reload();
        SwingUtilities.invokeAndWait(() -> { });
    }

    private List<ProblemView> aiProblems() {
        EditorWindows windows = new EditorWindows() {
            @Override
            public List<DefinitionBundle> libraryComponents() {
                return fixture.library.entries();
            }

            @Override
            public List<LibraryProblem> libraryProblems() {
                return fixture.service.problems();
            }

            @Override
            public OpenDocument openNewCircuit(String name) {
                throw new UnsupportedOperationException("no windows");
            }

            @Override
            public Optional<SettingsOverride> settingsOverride(OpenDocument document) {
                return Optional.empty();
            }
        };
        EditorAutomationHost host = new EditorAutomationHost(() -> List.of(openDocument), windows,
                new PreferenceProposals(AppPreferences.inMemory()));
        DocumentId id = host.documentId(openDocument);
        return new AutomationApi(host).readProblems(id).problems();
    }

    private Component place(String id, String typeId, int x, int y) {
        Component component = new Component(new ElementId(id), typeId, new GridPoint(x, y), Rotation.R0, Map.of(),
                "");
        openDocument.execute(new AddElementsCommand(List.of(component)));
        return component;
    }

    private GridPoint pin(Component component, int index) {
        return openDocument.registry().typeOf(component).pinLocation(component, index);
    }
}
