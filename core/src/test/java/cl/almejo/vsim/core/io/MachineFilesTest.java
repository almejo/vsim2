package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineBody;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.MachineExamples;
import cl.almejo.vsim.core.machine.MachineNote;
import cl.almejo.vsim.core.machine.MachinePin;
import cl.almejo.vsim.core.machine.MachinePinEdits;
import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.MachineTests;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.OutputAssignment;
import cl.almejo.vsim.core.machine.StateColor;
import cl.almejo.vsim.core.machine.StateMachine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** File format 7: a machine travels in circuit files, library files and the clipboard, with its generated body. */
class MachineFilesTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitFormat format = new CircuitFormat(registry);

    @Test
    void aCircuitWithAMachineRoundTrips() throws CircuitFormatException {
        CircuitDocument document = circuitWith(richMachine());

        String json = format.write(document);
        CircuitDocument read = format.read(json);

        assertThat(json).contains("\"formatVersion\" : 7").contains("\"machine\" : {");
        assertThat(read.definition("maquina-1")).contains(document.definition("maquina-1").orElseThrow());
        assertThat(format.write(read)).isEqualTo(json);
    }

    @Test
    void aDefinitionWithoutAMachineWritesNoMachineKey() {
        CircuitDocument document = circuitWith(MachineDefinitions.create("maquina-1", "Máquina 1"));
        document.putDefinition(new ComponentDefinition("plain", "Plain", "", MachineBody.of(StateMachine.initial())
                .size(), List.of(), new CircuitFragment(List.of(), List.of(), List.of())));

        String json = format.write(document);

        assertThat(json.indexOf("\"machine\"")).isEqualTo(json.lastIndexOf("\"machine\""));
    }

    @Test
    void aLibraryFileWithAMachineRoundTrips() throws CircuitFormatException {
        ComponentDefinition definition = richMachine();
        DefinitionFormat library = new DefinitionFormat(registry);

        DefinitionBundle read = library.read(library.write(new DefinitionBundle(definition, List.of())));

        assertThat(read.root()).isEqualTo(definition);
    }

    @Test
    void aCopiedMachinePieceCarriesItsDefinition() throws CircuitFormatException {
        CircuitDocument document = circuitWith(richMachine());
        ComponentTypeRegistry resolving = registry.extendedWith(SubcircuitTypes.resolverFor(document));
        FragmentFormat clipboard = new FragmentFormat(registry);

        CircuitFragment pasted = clipboard.read(clipboard.write(CircuitFragment.of(document,
                List.of(new ElementId("c1")), resolving)));

        assertThat(pasted.definitions()).containsExactly(document.definition("maquina-1").orElseThrow());
    }

    /** Hand test U: every file with a machine logged «disagrees»: the written body has no defaults filled in. */
    @Test
    void aBodyVsimWroteItselfAgreesWithTheMachineItWasGeneratedFrom() throws Exception {
        ComponentDefinition definition = richMachine();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(format.write(circuitWith(definition)));
        DefinitionEntries.DefinitionEntry written = mapper.treeToValue(root.path("definitions").get(0),
                DefinitionEntries.DefinitionEntry.class);

        assertThat(DefinitionEntries.writtenBodyDisagrees(written, definition.machine().orElseThrow())).isFalse();
    }

    @Test
    void aBodyWithAnotherPinOrAnotherPlaceDisagrees() throws Exception {
        ComponentDefinition definition = richMachine();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(format.write(circuitWith(definition)));
        ObjectNode moved = (ObjectNode) root.path("definitions").get(0);
        ((ObjectNode) moved.path("components").get(0)).put("x", 5);
        ObjectNode fewer = (ObjectNode) mapper.readTree(format.write(circuitWith(definition))).path("definitions")
                .get(0);
        ((com.fasterxml.jackson.databind.node.ArrayNode) fewer.path("pinout")).remove(0);

        assertThat(DefinitionEntries.writtenBodyDisagrees(
                mapper.treeToValue(moved, DefinitionEntries.DefinitionEntry.class),
                definition.machine().orElseThrow())).isTrue();
        assertThat(DefinitionEntries.writtenBodyDisagrees(
                mapper.treeToValue(fewer, DefinitionEntries.DefinitionEntry.class),
                definition.machine().orElseThrow())).isTrue();
    }

    @Test
    void aBodyOnDiskThatDisagreesWithTheMachineIsRegenerated() throws Exception {
        ComponentDefinition definition = richMachine();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(format.write(circuitWith(definition)));
        ObjectNode written = (ObjectNode) root.path("definitions").get(0);
        written.putArray("components");
        written.putArray("pinout");

        CircuitDocument read = format.read(mapper.writeValueAsString(root));

        ComponentDefinition regenerated = read.definition("maquina-1").orElseThrow();
        assertThat(regenerated).isEqualTo(definition);
    }

    @Test
    void aFormatSixFileStillLoads() throws CircuitFormatException {
        CircuitDocument document = new CircuitDocument();
        document.add(new Component(new ElementId("c1"), StandardComponentTypes.CLOCK, GridPoint.ORIGIN, Rotation.R0,
                Map.of(), ""));
        String old = format.write(document).replace("\"formatVersion\" : 7", "\"formatVersion\" : 6");

        assertThat(format.read(old).components()).hasSize(1);
    }

    /** The detector with a Mealy output, the rising edge, a bus flag and a saved test: every field of the model. */
    private static ComponentDefinition richMachine() {
        StateMachine detector = MachineExamples.twoOnesDetector();
        List<MachineTransition> transitions = new ArrayList<>(detector.transitions());
        transitions.set(1, transitions.get(1).withOutputs(List.of(new OutputAssignment("W", true))));
        StateMachine machine = detector.withTransitions(transitions).withPinNames(List.of("x", "unused"),
                        List.of("Z", "W")).withEdge(StateMachine.EDGE_RISING).withBus(false, true)
                .withTests(List.of(MachineTest.empty("p1", "dos unos", 2)));
        return MachineDefinitions.create("maquina-1", "Máquina 1").withMachine(machine);
    }

    private static CircuitDocument circuitWith(ComponentDefinition definition) {
        CircuitDocument document = new CircuitDocument();
        document.putDefinition(definition);
        document.add(new Component(new ElementId("c1"), SubcircuitTypes.typeIdOf(definition.id()),
                new GridPoint(10, 10), Rotation.R0, Map.of(), ""));
        return document;
    }

    @Test
    void aPinWhoseIdIsItsNameIsAPlainStringAndARenamedOneKeepsItsId() throws CircuitFormatException {
        StateMachine machine = MachinePinEdits.renamed(MachineExamples.twoOnesDetector(), MachinePinEdits.Side.INPUT,
                "x", "a");
        CircuitDocument document = circuitWith(MachineDefinitions.create("maquina-1", "Máquina 1")
                .withMachine(machine));

        String json = format.write(document);
        CircuitDocument read = format.read(json);

        assertThat(json.replaceAll("\\s+", "")).contains("\"inputs\":[{\"id\":\"x\",\"name\":\"a\"}]")
                .contains("\"outputs\":[\"Z\"]");
        assertThat(read.definition("maquina-1").orElseThrow().machine().orElseThrow().inputs())
                .containsExactly(new MachinePin("x", "a"));
        assertThat(format.write(read)).isEqualTo(json);
    }

    @Test
    void aFileOfTheFirstHitosWithPinNamesOnlyLoadsWithIdsEqualToTheNames() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(format.write(circuitWith(richMachine())));
        ObjectNode machine = (ObjectNode) root.path("definitions").get(0).path("machine");
        machine.putArray("inputs").add("x").add("unused");

        StateMachine read = format.read(mapper.writeValueAsString(root)).definition("maquina-1").orElseThrow()
                .machine().orElseThrow();

        assertThat(read.inputs()).containsExactly(MachinePin.named("x"), MachinePin.named("unused"));
        assertThat(read.states()).allSatisfy(state -> assertThat(state.color()).isEmpty());
        assertThat(read.notes()).isEmpty();
    }

    @Test
    void stateColoursAndDiagramNotesRoundTrip() throws CircuitFormatException {
        StateMachine detector = MachineExamples.twoOnesDetector();
        StateMachine machine = detector.withState("s1", state -> state.withColor(Optional.of(StateColor.VIOLET)))
                .withNotes(List.of(new MachineNote("n1", new DiagramPoint(-30.5, 140), 200, 90, StickyColor.GREEN,
                        "S2 enciende Z")));
        CircuitDocument document = circuitWith(MachineDefinitions.create("maquina-1", "Máquina 1")
                .withMachine(machine));

        String json = format.write(document);
        CircuitDocument read = format.read(json);

        assertThat(json).contains("\"color\" : \"violet\"").contains("\"color\" : \"green\"");
        assertThat(read.definition("maquina-1").orElseThrow().machine()).contains(machine);
        assertThat(format.write(read)).isEqualTo(json);
    }

    @Test
    void anUnknownStateColourReadsAsNoColour() throws CircuitFormatException {
        StateMachine machine = MachineExamples.twoOnesDetector()
                .withState("s1", state -> state.withColor(Optional.of(StateColor.VIOLET)));
        String json = format.write(circuitWith(MachineDefinitions.create("maquina-1", "Máquina 1")
                .withMachine(machine))).replace("\"violet\"", "\"magenta\"");

        StateMachine read = format.read(json).definition("maquina-1").orElseThrow().machine().orElseThrow();

        assertThat(read.state("s1").orElseThrow().color()).isEmpty();
    }

    /**
     * A folded diagram note keeps its fold in the file (format 7, still unreleased: an optional field, no new format):
     * {@code "folded" : true} only on a folded note, so an unfolded one keeps its bytes and a file without the field
     * reads every note unfolded.
     */
    @Test
    void aFoldedDiagramNoteIsSavedFoldedAndANoteWithoutTheFieldReadsUnfolded() throws CircuitFormatException {
        MachineNote open = new MachineNote("n1", new DiagramPoint(0, 140), 200, 90, StickyColor.GREEN, "abierta");
        MachineNote folded = new MachineNote("n2", new DiagramPoint(300, 140), 200, 90, StickyColor.YELLOW,
                "doblada").withFolded(true);
        StateMachine machine = MachineExamples.twoOnesDetector().withNotes(List.of(open, folded));
        CircuitDocument document = circuitWith(MachineDefinitions.create("maquina-1", "Máquina 1")
                .withMachine(machine));

        String json = format.write(document);
        StateMachine read = format.read(json).definition("maquina-1").orElseThrow().machine().orElseThrow();

        assertThat(json).containsOnlyOnce("\"folded\" : true").doesNotContain("\"folded\" : false");
        assertThat(read.notes()).extracting(MachineNote::folded).containsExactly(false, true);
        assertThat(read).isEqualTo(machine);
        String withoutTheField = json.replaceAll(",\\s*\"folded\" : true", "");
        assertThat(withoutTheField).doesNotContain("folded");
        assertThat(format.read(withoutTheField).definition("maquina-1").orElseThrow().machine().orElseThrow()
                .notes()).extracting(MachineNote::folded).containsExactly(false, false);
    }

    // --- tests ---

    @Test
    void aTestIsWrittenAsOneStringPerPinWithOneCharacterPerColumn() throws CircuitFormatException {
        MachineTest test = MachineTest.empty("p1", "Dos 1 seguidos", 8);
        String inputs = "01101100";
        for (int column = 0; column < inputs.length(); column++) {
            test = MachineTests.withInput(test, column, "x", inputs.charAt(column) == '1');
        }
        test = MachineTests.withExpected(MachineTests.withExpected(test, 2, "Z", LogicValue.ONE), 4, "Z",
                LogicValue.ZERO);
        StateMachine machine = MachineExamples.twoOnesDetector().withTests(List.of(test,
                MachineTest.empty("p2", "Solo ceros", 3)));
        CircuitDocument document = circuitWith(MachineDefinitions.create("maquina-1", "Máquina 1")
                .withMachine(machine));

        String json = format.write(document);
        CircuitDocument read = format.read(json);

        assertThat(json.replaceAll("\\s+", "")).contains("{\"id\":\"p1\",\"name\":\"Dos1seguidos\",\"columns\":8,"
                + "\"inputs\":{\"x\":\"01101100\"},\"expected\":{\"Z\":\"--1-0---\"}}")
                .contains("{\"id\":\"p2\",\"name\":\"Soloceros\",\"columns\":3}");
        assertThat(read.definition("maquina-1").orElseThrow().machine()).contains(machine);
        assertThat(format.write(read)).isEqualTo(json);
    }

    @Test
    void theValuesOfAPinTheMachineNoLongerHasAreKept() throws CircuitFormatException {
        MachineTest test = MachineTests.withInput(MachineTest.empty("p1", "Prueba 1", 2), 1, "viejo", true);
        StateMachine machine = MachineExamples.twoOnesDetector().withTests(List.of(test));
        CircuitDocument document = circuitWith(MachineDefinitions.create("maquina-1", "Máquina 1")
                .withMachine(machine));

        CircuitDocument read = format.read(format.write(document));

        assertThat(read.definition("maquina-1").orElseThrow().machine()).contains(machine);
    }

    @Test
    void aTestWithOnlyANameReadsAsOneEmptyColumnWithAFreshId() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(format.write(circuitWith(richMachine())));
        ObjectNode machine = (ObjectNode) root.path("definitions").get(0).path("machine");
        machine.putArray("tests").addObject().put("name", "dos unos");

        StateMachine read = format.read(mapper.writeValueAsString(root)).definition("maquina-1").orElseThrow()
                .machine().orElseThrow();

        assertThat(read.tests()).containsExactly(MachineTest.empty("p1", "dos unos", 1));
    }

    @Test
    void aTestValueOfTheWrongLengthOrWithAnotherCharacterIsAnInvalidFile() throws Exception {
        String json = format.write(circuitWith(MachineDefinitions.create("maquina-1", "Máquina 1").withMachine(
                MachineExamples.twoOnesDetector().withTests(List.of(MachineTests.withExpected(
                        MachineTests.withInput(MachineTest.empty("p1", "Prueba 1", 4), 1, "x", true), 2, "Z",
                        LogicValue.ONE))))));
        assertThat(json).contains("\"0100\"").contains("\"--1-\"");

        assertThatThrownBy(() -> format.read(json.replace("\"0100\"", "\"010\"")))
                .isInstanceOf(CircuitFormatException.class);
        assertThatThrownBy(() -> format.read(json.replace("\"0100\"", "\"01-0\"")))
                .isInstanceOf(CircuitFormatException.class);
        assertThatThrownBy(() -> format.read(json.replace("\"--1-\"", "\"--1x\"")))
                .isInstanceOf(CircuitFormatException.class);
    }
}
