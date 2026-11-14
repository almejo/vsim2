package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.MachineBody;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.MachineExamples;
import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.OutputAssignment;
import cl.almejo.vsim.core.machine.StateMachine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
                .withTests(List.of(new MachineTest("dos unos")));
        return MachineDefinitions.create("maquina-1", "Máquina 1").withMachine(machine);
    }

    private static CircuitDocument circuitWith(ComponentDefinition definition) {
        CircuitDocument document = new CircuitDocument();
        document.putDefinition(definition);
        document.add(new Component(new ElementId("c1"), SubcircuitTypes.typeIdOf(definition.id()),
                new GridPoint(10, 10), Rotation.R0, Map.of(), ""));
        return document;
    }
}
