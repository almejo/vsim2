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
import cl.almejo.vsim.core.machine.LoopSide;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.MachineExamples;
import cl.almejo.vsim.core.machine.MachineNote;
import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.MachineTests;
import cl.almejo.vsim.core.machine.MachineText;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.OutputAssignment;
import cl.almejo.vsim.core.machine.StateColor;
import cl.almejo.vsim.core.machine.StateMachine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Format 7 is frozen for the machines (pre-release round of 0.9): {@code machine-format-7/circuit.vsim} and
 * {@code library.vsim} were written by the code of that day with every machine feature, and today's reader must keep
 * reading them as the same model. The rule for later milestones: they add only optional fields, and an absent field
 * means what it means today. To regenerate the files on purpose (never to make a failing test pass), run with
 * {@code VSIM_WRITE_FROZEN=1}.
 */
class MachineFormatFrozenTest {

    private static final String CIRCUIT_FILE = "machine-format-7/circuit.vsim";
    private static final String LIBRARY_FILE = "machine-format-7/library.vsim";

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitFormat format = new CircuitFormat(registry);
    private final DefinitionFormat libraryFormat = new DefinitionFormat(registry);

    /** The detector with every feature of a machine: Moore and Mealy outputs, loops, buses, edge, notes, texts, tests. */
    static StateMachine everyFeature() {
        StateMachine detector = MachineExamples.twoOnesDetector();
        List<MachineTransition> arrows = new ArrayList<>(detector.transitions());
        arrows.set(1, arrows.get(1).withOutputs(List.of(new OutputAssignment("W", true))));
        arrows.set(0, arrows.get(0).withLoopSide(Optional.of(LoopSide.BOTTOM)));
        arrows.add(new MachineTransition("t6", "s2", "s2", "x y", List.of(new OutputAssignment("W", false)),
                Optional.of(LoopSide.TOP)));
        MachineTest test = MachineTest.empty("p1", "dos unos", 4);
        test = MachineTests.withInput(test, 1, "x", true);
        test = MachineTests.withInput(test, 2, "x", true);
        test = MachineTests.withExpected(test, 3, "Z", LogicValue.ONE);
        return detector.withPinNames(List.of("x", "y"), List.of("Z", "W")).withTransitions(arrows)
                .withState("s1", state -> state.withColor(Optional.of(StateColor.VIOLET)))
                .withEdge(StateMachine.EDGE_RISING).withBus(true, true)
                .withNotes(List.of(new MachineNote("n1", new DiagramPoint(-30.5, 140), 200, 90, StickyColor.GREEN,
                        "S2 enciende Z")))
                .withTexts(List.of(new MachineText("t1", new DiagramPoint(12.5, -40), "una\nnota")))
                .withTests(List.of(test, MachineTest.empty("p2", "vacía", 2)));
    }

    static CircuitDocument circuit() {
        ComponentDefinition original = MachineDefinitions.create("maquina-1", "Máquina 1").withMachine(everyFeature());
        ComponentDefinition duplicate = MachineDefinitions.create("maquina-1-copia", "Máquina 1 copia")
                .withMachine(everyFeature());
        CircuitDocument document = new CircuitDocument();
        document.putDefinition(original);
        document.putDefinition(duplicate);
        document.add(new Component(new ElementId("c1"), SubcircuitTypes.typeIdOf(original.id()),
                new GridPoint(10, 10), Rotation.R0, Map.of(), ""));
        document.add(new Component(new ElementId("c2"), SubcircuitTypes.typeIdOf(duplicate.id()),
                new GridPoint(40, 10), Rotation.R0, Map.of(), ""));
        return document;
    }

    private static ComponentDefinition libraryMachine() {
        return MachineDefinitions.create("maquina-1", "Máquina 1").withMachine(everyFeature());
    }

    private static String resource(String name) throws IOException {
        try (InputStream stream = MachineFormatFrozenTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(stream).as("the frozen file " + name).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The text without its {@code savedWith} line: the app's version changes with every release, the format does not. */
    private static String withoutVersion(String text) {
        return text.replaceAll("\"savedWith\" : \"[^\"]*\",?", "");
    }

    @Test
    void writeTheFrozenFilesOnPurpose() throws IOException {
        if (System.getenv("VSIM_WRITE_FROZEN") == null) {
            return;
        }
        Path directory = Path.of("src/test/resources/machine-format-7");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("circuit.vsim"), format.write(circuit()));
        Files.writeString(directory.resolve("library.vsim"),
                libraryFormat.write(new DefinitionBundle(libraryMachine(), List.of())));
    }

    @Test
    void theFrozenCircuitReadsAsTheModelItWasWrittenFrom() throws Exception {
        CircuitDocument read = format.read(resource(CIRCUIT_FILE));

        assertThat(resource(CIRCUIT_FILE)).contains("\"formatVersion\" : 7");
        assertThat(read.definition("maquina-1").orElseThrow().machine()).contains(everyFeature());
        assertThat(read.definition("maquina-1-copia").orElseThrow().machine()).contains(everyFeature());
        assertThat(read.components()).hasSize(2);
        StateMachine machine = read.definition("maquina-1").orElseThrow().machine().orElseThrow();
        assertThat(machine.edge()).isEqualTo(StateMachine.EDGE_RISING);
        assertThat(machine.inputsAsBus()).isTrue();
        assertThat(machine.outputsAsBus()).isTrue();
        assertThat(machine.transition("t0").orElseThrow().loopSide()).contains(LoopSide.BOTTOM);
        assertThat(machine.transition("t1").orElseThrow().outputs()).containsExactly(new OutputAssignment("W", true));
        assertThat(machine.state("s2").orElseThrow().outputs()).contains(new OutputAssignment("Z", true));
        assertThat(machine.notes()).hasSize(1);
        assertThat(machine.texts()).hasSize(1);
        assertThat(machine.tests()).hasSize(2);
    }

    @Test
    void theFrozenCircuitIsWrittenBackByteForByte() throws Exception {
        assertThat(withoutVersion(format.write(format.read(resource(CIRCUIT_FILE)))))
                .isEqualTo(withoutVersion(resource(CIRCUIT_FILE)));
    }

    @Test
    void theFrozenLibraryFileReadsAsTheModelItWasWrittenFrom() throws Exception {
        DefinitionBundle read = libraryFormat.read(resource(LIBRARY_FILE));

        assertThat(read.root()).isEqualTo(libraryMachine());
        assertThat(withoutVersion(libraryFormat.write(read))).isEqualTo(withoutVersion(resource(LIBRARY_FILE)));
    }

    @Test
    void todaysWriterOutputReadsBackInTheSameReader() throws Exception {
        String written = format.write(circuit());

        CircuitDocument read = format.read(written);

        assertThat(read.definition("maquina-1").orElseThrow().machine()).contains(everyFeature());
        assertThat(format.write(read)).isEqualTo(written);
        String library = libraryFormat.write(new DefinitionBundle(libraryMachine(), List.of()));
        assertThat(libraryFormat.read(library).root()).isEqualTo(libraryMachine());
    }

    @Test
    void theRecoveryOnlyDraftsNeverLeakIntoANormalSave() throws Exception {
        String plain = format.write(circuit());
        String copy = RecoveryTestDrafts.withDrafts(plain, Map.of("maquina-1", everyFeature()));

        assertThat(plain).doesNotContain(RecoveryTestDrafts.FIELD);
        assertThat(copy).contains(RecoveryTestDrafts.FIELD);
        assertThat(format.write(format.read(copy))).doesNotContain(RecoveryTestDrafts.FIELD).isEqualTo(plain);
        assertThat(libraryFormat.write(new DefinitionBundle(libraryMachine(), List.of())))
                .doesNotContain(RecoveryTestDrafts.FIELD);
    }
}
