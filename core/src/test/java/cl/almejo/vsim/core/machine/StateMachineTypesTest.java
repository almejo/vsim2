package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.command.EditMachineCommand;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.FlatComponent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The native type of a machine is equal for machines that behave alike (hito 4, task 2): a test, a note, a colour or a
 * state's place is not behaviour, so editing one keeps the running copy instead of building it again.
 */
class StateMachineTypesTest {

    private static final String MACHINE_ID = "maquina-1";

    private final StateMachine detector = MachineExamples.twoOnesDetector();

    @Test
    void machinesThatDifferOnlyInTestsNotesColoursOrPlacesHaveEqualTypes() {
        MachineTest test = MachineTests.withExpected(MachineTest.empty("p1", "Prueba 1", 4), 2, "Z",
                LogicValue.ONE);
        StateMachine dressed = detector.withTests(List.of(test))
                .withNotes(List.of(new MachineNote("n1", new DiagramPoint(0, 140), 200, 90, StickyColor.GREEN,
                        "S2 enciende Z")))
                .withState("s1", state -> state.withColor(Optional.of(StateColor.VIOLET))
                        .withPosition(new DiagramPoint(120, 80)));

        assertThat(typeOf(dressed)).isEqualTo(typeOf(detector));
        assertThat(typeOf(dressed).hashCode()).isEqualTo(typeOf(detector).hashCode());
        assertThat(StateMachineTypes.machineOf(typeOf(dressed))).as("the type still runs the whole model")
                .contains(dressed);
    }

    @Test
    void aDifferentConditionOutputEdgeOrInitialStateGivesADifferentType() {
        assertThat(typeOf(detector.withTransition("t1", arrow -> arrow.withCondition("x'"))))
                .isNotEqualTo(typeOf(detector));
        assertThat(typeOf(detector.withState("s2", state -> state.withOutputs(List.of()))))
                .isNotEqualTo(typeOf(detector));
        assertThat(typeOf(detector.withEdge(StateMachine.EDGE_RISING))).isNotEqualTo(typeOf(detector));
        assertThat(typeOf(detector.withInitialStateId(Optional.of("s1")))).isNotEqualTo(typeOf(detector));
    }

    /** A state's name is what the piece's face and the probe show while it runs: renaming it builds the copy again. */
    @Test
    void aRenamedStateGivesADifferentType() {
        assertThat(typeOf(detector.withState("s2", state -> state.withName("Dos"))))
                .isNotEqualTo(typeOf(detector));
    }

    @Test
    void anEditOfOnlyTheTestsKeepsTheFlattenedMachineSoTheRunningCopyStays() {
        CircuitDocument document = new CircuitDocument();
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        registry.addResolver(SubcircuitTypes.resolverFor(document));
        document.putDefinition(MachineDefinitions.create(MACHINE_ID, "Máquina 1").withMachine(detector));
        document.add(new Component(new ElementId("m"), SubcircuitTypes.typeIdOf(MACHINE_ID), new GridPoint(20, 0),
                Rotation.R0, Map.of(), ""));
        FlatComponent before = flatMachine(document, registry);

        new History(document).execute(new EditMachineCommand(MACHINE_ID, detector.withTests(List.of(
                MachineTest.empty("p1", "Prueba 1", 8))), "command.saveTests"));
        FlatComponent after = flatMachine(document, registry);

        assertThat(after.type()).isEqualTo(before.type());
        assertThat(after.parameters()).isEqualTo(before.parameters());
    }

    private static ComponentType typeOf(StateMachine machine) {
        ComponentDefinition definition = MachineDefinitions.create(MACHINE_ID, "Máquina 1").withMachine(machine);
        return StateMachineTypes.typeOf(definition);
    }

    private static FlatComponent flatMachine(CircuitDocument document, ComponentTypeRegistry registry) {
        return new CircuitFlattener(registry).flatten(document, new NetlistCompiler(registry).compile(document))
                .components().stream().filter(component -> component.id().equals("m")).findFirst().orElseThrow();
    }
}
