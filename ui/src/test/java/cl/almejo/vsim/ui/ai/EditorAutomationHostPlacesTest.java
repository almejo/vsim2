package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.EditorAccess;
import cl.almejo.vsim.automation.PlaceRef;
import cl.almejo.vsim.automation.PlaceSummary;
import cl.almejo.vsim.automation.edit.EditOperation;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.UndoOutcome;
import cl.almejo.vsim.automation.edit.EditRequest;
import cl.almejo.vsim.automation.edit.TaskStep;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.InsideEditRefusedException;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.automation.read.CircuitView;
import cl.almejo.vsim.automation.read.MemoryView;
import cl.almejo.vsim.automation.read.ReadFilter;
import cl.almejo.vsim.automation.simulate.InputValue;
import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.command.SetInstanceMemoryContentsCommand;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.PieceMemories;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static cl.almejo.vsim.ui.UiTestSupport.awaitSimulation;
import static cl.almejo.vsim.ui.UiTestSupport.onEditor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The AI's places (spec §12): a definition's body with one instance's values, read and edited through the document's
 * body places; without a place the main circuit, whatever the user shows.
 */
class EditorAutomationHostPlacesTest {

    private static final String BLOCK = "bloque";
    private static final String MEMORY_BLOCK = "memoria-chica";
    private static final ElementId INNER_SWITCH = new ElementId("c2");
    private static final ElementId INNER_MEMORY = new ElementId("c3");

    private final List<OpenDocument> open = new ArrayList<>();
    private final List<String> shown = new ArrayList<>();
    private final EditorWindows windows = new EditorWindows() {
        @Override
        public OpenDocument openNewCircuit(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<SettingsOverride> settingsOverride(OpenDocument document) {
            return Optional.empty();
        }

        @Override
        public void openPlace(OpenDocument document, Optional<String> definitionId, Optional<String> contextPath) {
            shown.add(definitionId.orElse("main") + "@" + contextPath.orElse("-"));
        }
    };
    private final EditorAutomationHost host = new EditorAutomationHost(() -> List.copyOf(open), windows,
            new PreferenceProposals(AppPreferences.inMemory()));
    private final AutomationApi api = new AutomationApi(host);
    private OpenDocument circuit;
    private DocumentId id;

    @BeforeEach
    void openACircuitWithTwoBlocksAndTwoMemoryBlocks() throws Exception {
        circuit = OpenDocument.createEmpty(StandardComponentTypes.registry());
        open.add(circuit);
        onEditor(() -> {
            circuit.execute(new PutDefinitionsCommand(List.of(block(), memoryBlock())));
            place("c5", BLOCK, 0);
            place("c9", BLOCK, 30);
            place("c20", MEMORY_BLOCK, 0);
            place("c21", MEMORY_BLOCK, 30);
            circuit.execute(SetInstanceMemoryContentsCommand.set(
                    PieceMemories.placeOf("c20", INNER_MEMORY), MemoryContents.empty().with(0, 0x22)));
            return null;
        });
        id = host.onEditorThread(EditorAccess::openDocuments).get(5, TimeUnit.SECONDS).getFirst().id();
    }

    @AfterEach
    void closeDocuments() {
        open.forEach(OpenDocument::close);
    }

    @Test
    void aPlaceQualifiedIdKeepsItsDocumentAndItsPlace() {
        PlaceRef place = new PlaceRef(BLOCK, Optional.of("c5"));

        DocumentId inPlace = id.in(place);

        assertThat(inPlace.base()).isEqualTo(id);
        assertThat(inPlace.place()).contains(place);
        assertThat(id.place()).isEmpty();
        assertThat(id.in(PlaceRef.main())).isEqualTo(id);
        assertThat(id.in(new PlaceRef(BLOCK, Optional.empty())).place())
                .contains(new PlaceRef(BLOCK, Optional.empty()));
    }

    @Test
    void listPlacesGivesTheMainCircuitAndEveryDefinitionWithItsUses() {
        List<PlaceSummary> places = api.listPlaces(id);

        assertThat(places).extracting(PlaceSummary::definition).containsExactly("main", BLOCK, MEMORY_BLOCK);
        PlaceSummary block = places.get(1);
        assertThat(block.name()).isEqualTo("Bloque");
        assertThat(block.uses()).extracting(PlaceSummary.PlaceUse::path).containsExactly("c5", "c9");
    }

    @Test
    void readCircuitWithAPlaceReadsTheBody() {
        CircuitView main = api.readCircuit(id, ReadFilter.FULL, Optional.empty());
        CircuitView body = api.readCircuit(id.in(new PlaceRef(BLOCK, Optional.of("c5"))), ReadFilter.FULL,
                Optional.empty());

        assertThat(main.parts()).extracting(part -> part.id()).contains("c5", "c9").doesNotContain("c2");
        assertThat(body.parts()).extracting(part -> part.id()).contains("c2").doesNotContain("c5");
    }

    @Test
    void withoutAPlaceTheMainCircuitIsReadEvenWhenABodyIsOpen() throws Exception {
        OpenDocument activeBody = onEditor(() -> circuit.openBody(BLOCK, Optional.of("c5")));
        try {
            CircuitView view = api.readCircuit(id, ReadFilter.FULL, Optional.empty());

            assertThat(view.parts()).extracting(part -> part.id()).contains("c5").doesNotContain("c2");
        } finally {
            onEditor(() -> {
                activeBody.close();
                return null;
            });
        }
    }

    @Test
    void editWithAPlaceIsOneUndoStepOnTheDocument() throws Exception {
        DocumentId inPlace = id.in(new PlaceRef(BLOCK, Optional.of("c5")));
        int partsBefore = onEditor(() -> circuit.document().definition(BLOCK).orElseThrow().body().components().size());
        long version = api.readCircuit(inPlace, ReadFilter.SUMMARY, Optional.empty()).version().value();

        api.editStep(new EditRequest(inPlace, new DocumentVersion(version), "op-1", Optional.empty(),
                List.of(new EditOperation.PlacePart(Optional.of("lamp"), "led", 10, 10, Rotation.R0, Map.of(), "")),
                false), Optional.empty());

        onEditor(() -> {
            assertThat(circuit.document().definition(BLOCK).orElseThrow().body().components())
                    .hasSize(partsBefore + 1);
            circuit.history().undo();
            assertThat(circuit.document().definition(BLOCK).orElseThrow().body().components()).hasSize(partsBefore);
            return null;
        });
    }

    @Test
    void setInputsWithAPlaceDrivesTheContextInstanceOnly() throws Exception {
        DocumentId inPlace = id.in(new PlaceRef(BLOCK, Optional.of("c5")));
        awaitSimulation(circuit);
        long version = api.readCircuit(inPlace, ReadFilter.SUMMARY, Optional.empty()).version().value();

        api.setInputs(inPlace, new DocumentVersion(version), "op-1", List.of(new InputValue("c2", "1")));

        OpenDocument first = onEditor(() -> circuit.openBody(BLOCK, Optional.of("c5")));
        OpenDocument second = onEditor(() -> circuit.openBody(BLOCK, Optional.of("c9")));
        try {
            awaitSimulation(circuit);
            assertThat(first.latestSnapshot().componentState("c2"))
                    .isNotEqualTo(second.latestSnapshot().componentState("c2"));
        } finally {
            onEditor(() -> {
                first.close();
                second.close();
                return null;
            });
        }
    }

    @Test
    void readMemoryWithAPlaceReturnsThePiecesOwnContents() {
        MemoryView ownContents = api.readMemory(id.in(new PlaceRef(MEMORY_BLOCK, Optional.of("c20"))), "c3", false,
                0, 1);
        MemoryView definitionContents = api.readMemory(id.in(new PlaceRef(MEMORY_BLOCK, Optional.of("c21"))), "c3",
                false, 0, 1);

        assertThat(ownContents.words()).containsExactly("22");
        assertThat(definitionContents.words()).containsExactly("11");
    }

    @Test
    void writingMemoryWordsInAPiecesPlaceWritesThatPiecesOwnContentsInOneUndoStep() throws Exception {
        DocumentId inPiece = id.in(new PlaceRef(MEMORY_BLOCK, Optional.of("c21")));
        DocumentId withoutValues = id.in(new PlaceRef(MEMORY_BLOCK, Optional.empty()));
        long version = api.readCircuit(inPiece, ReadFilter.SUMMARY, Optional.empty()).version().value();
        EditOperation write = new EditOperation.SetMemoryContents("c3", 1, List.of("33"));

        api.editStep(new EditRequest(inPiece, new DocumentVersion(version), "op-1", Optional.empty(), List.of(write),
                false), Optional.empty());

        MemoryView own = api.readMemory(inPiece, "c3", false, 0, 2);
        assertThat(own.words()).containsExactly("11", "33");
        assertThat(api.readMemory(withoutValues, "c3", false, 0, 2).words()).containsExactly("11", "00");
        assertThat(api.readMemory(id.in(new PlaceRef(MEMORY_BLOCK, Optional.of("c20"))), "c3", false, 0, 1).words())
                .containsExactly("22");
        onEditor(() -> {
            assertThat(circuit.history().canUndo()).isTrue();
            circuit.history().undo();
            assertThat(PieceMemories.stored(circuit.document(),
                    PieceMemories.placeOf("c21", INNER_MEMORY))).isEmpty();
            return null;
        });
        assertThat(api.readMemory(inPiece, "c3", false, 0, 2).words()).containsExactly("11", "00");
    }

    @Test
    void writingWithoutContextWritesTheComponentsWords() throws Exception {
        DocumentId withoutValues = id.in(new PlaceRef(MEMORY_BLOCK, Optional.empty()));
        long version = api.readCircuit(withoutValues, ReadFilter.SUMMARY, Optional.empty()).version().value();

        api.editStep(new EditRequest(withoutValues, new DocumentVersion(version), "op-2", Optional.empty(),
                List.of(new EditOperation.SetMemoryContents("c3", 0, List.of("33"))), false), Optional.empty());

        assertThat(api.readMemory(withoutValues, "c3", false, 0, 1).words()).containsExactly("33");
        assertThat(api.readMemory(id.in(new PlaceRef(MEMORY_BLOCK, Optional.of("c20"))), "c3", false, 0, 1).words())
                .containsExactly("22");
    }

    @Test
    void aPieceMemoryWriteCannotShareACallWithOtherOperations() throws Exception {
        DocumentId inPiece = id.in(new PlaceRef(MEMORY_BLOCK, Optional.of("c21")));
        long version = api.readCircuit(inPiece, ReadFilter.SUMMARY, Optional.empty()).version().value();

        assertThatThrownBy(() -> api.editStep(new EditRequest(inPiece, new DocumentVersion(version), "op-3",
                Optional.empty(), List.of(new EditOperation.SetMemoryContents("c3", 0, List.of("33")),
                new EditOperation.PlacePart(Optional.of("lamp"), "led", 10, 10, Rotation.R0, Map.of(), "")), false),
                Optional.empty())).isInstanceOfSatisfying(AutomationException.class,
                refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.NOT_ALLOWED));
        assertThat(api.readMemory(inPiece, "c3", false, 0, 1).words()).containsExactly("11");
    }

    @Test
    void aContextThatNamesNoInstanceIsRefusedNeverAnotherPiece() {
        DocumentId gone = id.in(new PlaceRef(MEMORY_BLOCK, Optional.of("c99")));

        assertThatThrownBy(() -> api.readMemory(gone, "c3", false, 0, 1)).isInstanceOfSatisfying(
                AutomationException.class, refusal -> assertThat(refusal.error())
                        .isEqualTo(AutomationError.INVALID_REFERENCE));
        assertThatThrownBy(() -> api.readCircuit(gone, ReadFilter.SUMMARY, Optional.empty()))
                .isInstanceOfSatisfying(AutomationException.class, refusal -> assertThat(refusal.error())
                        .isEqualTo(AutomationError.INVALID_REFERENCE));
        assertThatThrownBy(() -> api.openPlace(id, new PlaceRef(MEMORY_BLOCK, Optional.of("c99"))))
                .isInstanceOfSatisfying(AutomationException.class, refusal -> assertThat(refusal.error())
                        .isEqualTo(AutomationError.INVALID_REFERENCE));
        assertThat(shown).isEmpty();
    }

    @Test
    void theAisUndoSeesThroughAnInsideEdit() throws Exception {
        DocumentId inPlace = id.in(new PlaceRef(BLOCK, Optional.of("c5")));
        TaskInfo task = api.beginTask(id, "Lámpara dentro", List.of());
        int partsBefore = onEditor(() -> circuit.document().definition(BLOCK).orElseThrow().body().components().size());
        long version = api.readCircuit(inPlace, ReadFilter.SUMMARY, Optional.empty()).version().value();
        api.editStep(new EditRequest(inPlace, new DocumentVersion(version), "op-1",
                Optional.of(new TaskStep(task.taskId(), "1", "1", "Pongo la lámpara")),
                List.of(new EditOperation.PlacePart(Optional.of("lamp"), "led", 10, 10, Rotation.R0, Map.of(), "")),
                false), Optional.empty());

        UndoOutcome outcome = onEditor(() -> api.undoTask(task.taskId()));

        assertThat(outcome.undone()).isEqualTo(1);
        assertThat(onEditor(() -> circuit.document().definition(BLOCK).orElseThrow().body().components().size()))
                .isEqualTo(partsBefore);
    }

    @Test
    void anInsideRefusalComesBackAsTheToolErrorWithItsExplanation() {
        DocumentId inPlace = id.in(new PlaceRef(BLOCK, Optional.of("c5")));
        Command refusing = new Command() {
            @Override
            public void apply(CircuitDocument document) {
                throw new InsideEditRefusedException("inside.cannotNestItself", List.of());
            }

            @Override
            public void revert(CircuitDocument document) {
            }

            @Override
            public String description() {
                return "command.test";
            }
        };

        assertThatThrownBy(() -> host.onCircuit(inPlace, circuit -> {
            circuit.execute(refusing);
            return Boolean.TRUE;
        }).get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(AutomationException.class)
                .cause().hasMessageContaining(Messages.format("inside.cannotNestItself"));
    }

    @Test
    void aPlaceThatIsNotADefinitionIsAnInvalidReference() {
        assertThatThrownBy(() -> api.readCircuit(id.in(new PlaceRef("nada", Optional.empty())), ReadFilter.SUMMARY,
                Optional.empty())).isInstanceOfSatisfying(AutomationException.class,
                refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_REFERENCE));
    }

    @Test
    void openPlaceShowsTheTabInTheAttachedWindow() {
        api.openPlace(id, new PlaceRef(BLOCK, Optional.of("c9")));
        api.openPlace(id, PlaceRef.main());

        assertThat(shown).containsExactly("bloque@c9", "main@-");
    }

    @Test
    void everyReadEditRunOrOpeningTellsWhichPlaceTheAiTouched() throws Exception {
        List<String> touched = new ArrayList<>();
        host.addPlaceListener((circuitId, touchedCircuit, place) -> touched.add(circuitId.value() + " "
                + (touchedCircuit == circuit) + " " + place.definition() + "@" + place.context().orElse("-")));
        DocumentId inPlace = id.in(new PlaceRef(BLOCK, Optional.of("c5")));

        api.readCircuit(id, ReadFilter.SUMMARY, Optional.empty());
        long version = api.readCircuit(inPlace, ReadFilter.SUMMARY, Optional.empty()).version().value();
        api.setInputs(inPlace, new DocumentVersion(version), "op-1", List.of(new InputValue("c2", "1")));
        api.openPlace(id, new PlaceRef(MEMORY_BLOCK, Optional.of("c21")));
        onEditor(() -> null); // the listeners hear on the editor thread

        assertThat(touched).containsExactly(id.value() + " true main@-", id.value() + " true bloque@c5",
                id.value() + " true bloque@c5", id.value() + " true memoria-chica@c21");
    }

    @Test
    void listingDocumentsAndPlacesTouchesNoPlace() throws Exception {
        List<String> touched = new ArrayList<>();
        host.addPlaceListener((circuitId, touchedCircuit, place) -> touched.add(place.definition()));

        api.listPlaces(id);
        host.onEditorThread(EditorAccess::openDocuments).get(5, TimeUnit.SECONDS);
        onEditor(() -> null); // the listeners hear on the editor thread

        assertThat(touched).isEmpty();
    }

    /** Round E (5q): the catalogue is the types the circuit may use, no work in any of its places. */
    @Test
    void readingTheCatalogueTouchesNoPlace() throws Exception {
        List<String> touched = new ArrayList<>();
        host.addPlaceListener((circuitId, touchedCircuit, place) -> touched.add(place.definition()));

        api.describeComponents(id, Optional.empty());
        api.describeComponents(id.in(new PlaceRef(BLOCK, Optional.of("c5"))), Optional.empty());
        api.describeComponent(id, StandardComponentTypes.SWITCH, Map.of());
        onEditor(() -> null); // the listeners hear on the editor thread

        assertThat(touched).isEmpty();
    }

    @Test
    void transientBodiesDoNotLeakListenersOnTheDocument() throws Exception {
        int baseline = onEditor(circuit::changeListenerCount);

        api.readCircuit(id.in(new PlaceRef(BLOCK, Optional.of("c5"))), ReadFilter.SUMMARY, Optional.empty());
        onEditor(() -> null); // the closing queued behind the read has run

        assertThat(onEditor(circuit::changeListenerCount)).isEqualTo(baseline);
    }

    private void place(String idText, String definitionId, int x) {
        circuit.execute(new AddElementsCommand(List.of(new Component(new ElementId(idText),
                SubcircuitTypes.typeIdOf(definitionId), new GridPoint(x, 10), Rotation.R0, Map.of(), ""))));
    }

    private ComponentDefinition block() {
        var registry = circuit.registry();
        Component input = registry.require(InterfacePins.INPUT_PIN).newComponent(new ElementId("c1"),
                new GridPoint(0, 10), Rotation.R0, Map.of(), "a");
        Component inner = registry.require(StandardComponentTypes.SWITCH).newComponent(INNER_SWITCH,
                new GridPoint(0, 0), Rotation.R0, Map.of(StandardComponentTypes.VALUE, "0"), "");
        return new ComponentDefinition(BLOCK, "Bloque", "", new GridSize(4, 4),
                List.of(new DefinitionPin(PinSpec.input("a", 0, 2), input.id())),
                new CircuitFragment(List.of(input, inner), List.of(), List.of()));
    }

    private ComponentDefinition memoryBlock() {
        var registry = circuit.registry();
        Component input = registry.require(InterfacePins.INPUT_PIN).newComponent(new ElementId("c1"),
                new GridPoint(0, 10), Rotation.R0, Map.of(), "a");
        Component memory = registry.require(StandardComponentTypes.RAM).newComponent(INNER_MEMORY,
                new GridPoint(0, 20), Rotation.R0,
                Map.of("contents", MemoryContents.empty().with(0, 0x11).encode()), "");
        return new ComponentDefinition(MEMORY_BLOCK, "Memoria chica", "", new GridSize(4, 4),
                List.of(new DefinitionPin(PinSpec.input("a", 0, 2), input.id())),
                new CircuitFragment(List.of(input, memory), List.of(), List.of()));
    }
}
