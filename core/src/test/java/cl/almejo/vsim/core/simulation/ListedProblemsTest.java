package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.KeptOldShape;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.MachineCopies;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.MachineExamples;
import cl.almejo.vsim.core.machine.MachineFinding;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The rows of the Problemas list of a circuit, the one source of the editor's list and the AI's read (fix round D: the
 * AI sees every row).
 */
class ListedProblemsTest {

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentTypeRegistry registry;
    private final Component inverter;

    ListedProblemsTest() {
        document.putDefinition(ExampleDefinitions.inverter());
        registry = StandardComponentTypes.registry().extendedWith(SubcircuitTypes.resolverFor(document));
        inverter = registry.require(SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER)).newComponent(
                new ElementId("c20"), new GridPoint(10, 10), Rotation.R0, Map.of(), "U1");
        document.add(inverter);
    }

    @Test
    void everySourceComesInTheListsOrderAndTheSimulationsConnectivityIsTheEditorsInstead() {
        ShortCircuit shortCircuit = new ShortCircuit("n:1", List.of(new DriverRef("c1", 0, Value.ONE),
                new DriverRef("c2", 0, Value.ZERO)), 0);
        ConnectivityProblem published = new ConnectivityProblem("n:old", "c20", 0, GridPoint.ORIGIN,
                ConnectivityProblem.Cause.UNCONNECTED_INTERFACE);
        ConnectivityProblem current = new ConnectivityProblem("n:new", "c20", 1, GridPoint.ORIGIN,
                ConnectivityProblem.Cause.UNCONNECTED_INTERFACE);
        ConnectivityProblem outdated = new ConnectivityProblem(OutdatedPieceChecks.VERSION_PREFIX + "inverter",
                "c20", -1, inverter.position(), ConnectivityProblem.Cause.OUTDATED_WOULD_BREAK);
        LinkedFileProblem linked = new LinkedFileProblem("c7", "rom.hex", LinkedFileProblem.Kind.MISSING, "");
        ListedProblems.Sources sources = new ListedProblems.Sources(List.of(shortCircuit, published),
                List.of(current), Set.of(new PinRef(inverter.id(), 0)),
                List.of(new KeptOldShape(inverter.id(), ExampleDefinitions.INVERTER, List.of("y"))),
                List.of(outdated), List.of(linked));

        List<Problem> listed = ListedProblems.of(document, registry, sources);

        GridPoint pinA = registry.typeOf(inverter).pinLocation(inverter, 0);
        GridPoint pinY = registry.typeOf(inverter).pinLocation(inverter, 1);
        assertThat(listed).containsExactly(shortCircuit, current,
                new ConnectivityProblem("loosened:c20:0", "c20", 0, pinA,
                        ConnectivityProblem.Cause.LOOSENED_BY_SHAPE_CHANGE),
                new ConnectivityProblem("placedElsewhere:c20:1", "c20", 1, pinY,
                        ConnectivityProblem.Cause.PIN_PLACED_ELSEWHERE),
                outdated, linked);
    }

    @Test
    void aWireFoundAtAnEdgeIsListedOnceWhileThisSessionStillNamesItsRemovedPin() {
        GridPoint end = new GridPoint(10, 11);
        ConnectivityProblem session = new ConnectivityProblem(ConnectivityProblem.REMOVED_PIN_PREFIX + "c20:b", "c20",
                -1, end, ConnectivityProblem.Cause.WIRE_OF_REMOVED_PIN);
        ConnectivityProblem found = new ConnectivityProblem(ConnectivityProblem.EDGE_WIRE_PREFIX + "c20:10,11", "c20",
                -1, end, ConnectivityProblem.Cause.WIRE_AT_PIECE_EDGE);
        ConnectivityProblem elsewhere = new ConnectivityProblem(ConnectivityProblem.EDGE_WIRE_PREFIX + "c20:14,13",
                "c20", -1, new GridPoint(14, 13), ConnectivityProblem.Cause.WIRE_AT_PIECE_EDGE);
        ListedProblems.Sources sources = new ListedProblems.Sources(List.of(), List.of(found, elsewhere, session),
                Set.of(), List.of(), List.of(), List.of());

        assertThat(ListedProblems.of(document, registry, sources)).containsExactly(elsewhere, session);
        assertThat(ListedProblems.withoutRepeatedWires(List.of(found, elsewhere, session)))
                .containsExactly(elsewhere, session);
    }

    @Test
    void theSessionRowsOfAPieceNoLongerOnTheCanvasAreNotListed() {
        ListedProblems.Sources sources = new ListedProblems.Sources(List.of(), List.of(),
                Set.of(new PinRef(new ElementId("c99"), 0), new PinRef(inverter.id(), 7)),
                List.of(new KeptOldShape(new ElementId("c99"), ExampleDefinitions.INVERTER, List.of("y"))),
                List.of(), List.of());

        assertThat(ListedProblems.of(document, registry, sources)).isEmpty();
    }

    @Test
    void aPiecesOwnContentsThatDoNotApplyAreListedLastFromTheDocument() {
        Component linkedMemory = registry.require(StandardComponentTypes.RAM).newComponent(new ElementId("c10"),
                GridPoint.ORIGIN, Rotation.R0, Map.of(MemoryTypes.LINK, "programa.hex"), "Programa");
        document.putDefinition(new ComponentDefinition("memoria-chica", "Memoria chica", "", new GridSize(4, 4),
                List.of(), new CircuitFragment(List.of(linkedMemory), List.of(), List.of())));
        Component piece = registry.require(SubcircuitTypes.typeIdOf("memoria-chica")).newComponent(
                new ElementId("c30"), new GridPoint(40, 40), Rotation.R0, Map.of(), "");
        document.add(piece.withMemoryContents(Map.of("c10", "@0 9", "c99", "@0 8")));

        List<Problem> listed = ListedProblems.of(document, registry,
                ListedProblems.Sources.published(SimulationSnapshot.EMPTY, List.of()));

        assertThat(listed).map(ConnectivityProblem.class::cast)
                .extracting(ConnectivityProblem::netId, ConnectivityProblem::elementId, ConnectivityProblem::cause)
                .containsExactly(tuple("ownContents:c10", "c30/c10", ConnectivityProblem.Cause.OWN_CONTENTS_OVER_LINK),
                        tuple("ownContents:c99", "c30", ConnectivityProblem.Cause.OWN_CONTENTS_FOR_NOTHING));
    }

    @Test
    void aMachinesProblemsAreListedForItsDefinitionAndItsRuntimeOnesForEachCopy() {
        document.putDefinition(MachineDefinitions.create("maquina-1", "Detector")
                .withMachine(MachineExamples.twoOnesDetector()));
        document.putDefinition(MachineDefinitions.create("maquina-2", "Sin inicio")
                .withMachine(StateMachine.initial().withInitialStateId(Optional.empty())));
        Component copy = new Component(new ElementId("m"), SubcircuitTypes.typeIdOf("maquina-1"), GridPoint.ORIGIN,
                Rotation.R0, Map.of(), "");
        document.putDefinition(new ComponentDefinition("envoltorio", "Envoltorio", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(copy), List.of(), List.of())));
        document.add(new Component(new ElementId("w"), SubcircuitTypes.typeIdOf("envoltorio"), new GridPoint(40, 0),
                Rotation.R0, Map.of(), ""));
        VisibleState.MachineVisibleState running = new VisibleState.MachineVisibleState("s1", "S1",
                Optional.of("s2"), Optional.of("t2"), List.of("x"), false);
        SimulationSnapshot snapshot = new SimulationSnapshot(0, 0, 0, true, Map.of(), Map.of("w/m", running),
                List.of(), Optional.empty());

        List<Problem> listed = ListedProblems.of(document, registry, ListedProblems.Sources.published(snapshot,
                List.of()));

        assertThat(listed).map(MachineProblem.class::cast)
                .extracting(MachineProblem::definitionId, MachineProblem::instancePath,
                        problem -> problem.finding().kind())
                .containsExactly(
                        tuple("maquina-2", Optional.empty(), MachineFinding.Kind.NO_INITIAL_STATE),
                        tuple("maquina-1", Optional.of("w/m"), MachineFinding.Kind.INPUT_UNDEFINED_AT_EDGE));
        assertThat(listed).extracting(Problem::netId).doesNotHaveDuplicates()
                .allMatch(netId -> netId.startsWith("machine:"));
        assertThat(MachineCopies.firstPathOf(document, "maquina-1")).contains("w/m");
        assertThat(MachineCopies.firstPathOf(document, "maquina-2")).isEmpty();
    }

    /** A circuit no editor shows: what the simulation published, its connectivity included, and the given files. */
    @Test
    void publishedSourcesKeepTheSimulationsConnectivity() {
        ConnectivityProblem published = new ConnectivityProblem("n:old", "c20", 0, GridPoint.ORIGIN,
                ConnectivityProblem.Cause.UNCONNECTED_INTERFACE);
        LinkedFileProblem linked = new LinkedFileProblem("c7", "rom.hex", LinkedFileProblem.Kind.MISSING, "");
        SimulationSnapshot snapshot = new SimulationSnapshot(0, 0, 0, true, Map.of(), Map.of(), List.of(published),
                Optional.empty());

        assertThat(ListedProblems.of(document, registry, ListedProblems.Sources.published(snapshot,
                List.of(linked)))).containsExactly(published, linked);
    }
}
