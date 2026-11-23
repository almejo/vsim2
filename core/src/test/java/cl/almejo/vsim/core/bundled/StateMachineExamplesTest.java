package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.examples.CircuitBuilder;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.MachineKind;
import cl.almejo.vsim.core.machine.MachineTestRun;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The two state machine examples: wired cleanly, they do what their text says, and their tests in the machine pass. */
class StateMachineExamplesTest {

    /** The clock of both examples is 1000 up, 1000 down, low first: its falling edges are at 2000, 4000, … */
    private static final long FIRST_FALLING_EDGE = 2000;
    private static final long PERIOD = 2000;

    @Test
    void bothExamplesAreWiredAndExplainedCleanly() {
        ExampleChecks.assertExample(StateMachineExamples.semaforo());
        ExampleChecks.assertExample(StateMachineExamples.sequenceDetector());
    }

    @Test
    void eachExampleHasItsMachineWithItsTestsAllPassing() {
        for (CircuitBuilder builder : both()) {
            StateMachine machine = machineOf(builder.document());
            assertThat(machine.tests()).as(builder.document().name()).isNotEmpty();
            MachineTestRun.TestSummary summary = MachineTestRun.summary(machine);
            assertThat(summary.passing()).as("%s: %s", builder.document().name(), summary).isEqualTo(summary.tests());
            assertThat(summary.failing()).isZero();
            assertThat(summary.nothingExpected()).isZero();
        }
    }

    @Test
    void theSemaforoIsMooreAndTheDetectorIsMealy() {
        MachineKind semaforo = MachineKind.of(machineOf(StateMachineExamples.semaforo().document()));
        MachineKind detector = MachineKind.of(machineOf(StateMachineExamples.sequenceDetector().document()));

        assertThat(semaforo.moore()).isTrue();
        assertThat(semaforo.mealy()).isFalse();
        assertThat(detector.mealy()).isTrue();
        assertThat(detector.moore()).isFalse();
    }

    @Test
    void eachExampleCarriesAStickyNoteAndTheMachinesNote() {
        for (CircuitBuilder builder : both()) {
            assertThat(builder.document().stickyNotes()).as(builder.document().name()).hasSize(1);
            assertThat(machineOf(builder.document()).notes()).hasSize(1);
        }
    }

    @Test
    void theSemaforoCyclesRedGreenYellowOnEveryFallingEdgeAndTheResetGoesBackToRed() {
        ExampleSimulation simulation = new ExampleSimulation(StateMachineExamples.semaforo(), Map.of());
        List<String> lights = new ArrayList<>();
        simulation.runUntil(FIRST_FALLING_EDGE - 200);
        lights.add(lights(simulation));
        for (int edge = 0; edge < 4; edge++) {
            simulation.runUntil(FIRST_FALLING_EDGE + PERIOD * edge + 200);
            lights.add(lights(simulation));
        }

        assertThat(lights).containsExactly("rojo", "verde", "amarillo", "rojo", "verde");

        simulation.toggle("Reiniciar");
        simulation.runUntil(FIRST_FALLING_EDGE + PERIOD * 4 + 400);
        assertThat(lights(simulation)).isEqualTo("rojo");
    }

    @Test
    void theDetectorLightsZWhenTheLastThreeBitsAre101AndTheStreamsOverlap() {
        ExampleSimulation simulation = new ExampleSimulation(StateMachineExamples.sequenceDetector(), Map.of());
        int[] bits = {1, 0, 1, 0, 1, 1, 0, 1, 0, 0};
        List<Integer> expectedZ = List.of(0, 0, 1, 0, 1, 0, 0, 1, 0, 0);
        List<Integer> seenZ = new ArrayList<>();
        for (int edge = 0; edge < bits.length; edge++) {
            long edgeTime = FIRST_FALLING_EDGE + PERIOD * edge;
            simulation.runUntil(edgeTime - 600);
            simulation.setSwitch("x", bits[edge]);
            simulation.runUntil(edgeTime - 100);
            seenZ.add(simulation.led("Z").equals(Value.ONE) ? 1 : 0);
        }

        assertThat(seenZ).isEqualTo(expectedZ);
    }

    @Test
    void theCommittedFilesOpenWithTheirMachinesAndEveryTestPassing() throws Exception {
        for (String id : BundledExamples.stateMachineIds()) {
            CircuitDocument document = BundledExamples.open(id, StandardComponentTypes.registry());
            StateMachine machine = machineOf(document);
            MachineTestRun.TestSummary summary = MachineTestRun.summary(machine);
            assertThat(summary.tests()).as(id).isGreaterThanOrEqualTo(1);
            assertThat(summary.passing()).as(id).isEqualTo(summary.tests());
            assertThat(document.stickyNotes()).as(id).hasSize(1);
        }
    }

    private static List<CircuitBuilder> both() {
        return List.of(StateMachineExamples.semaforo(), StateMachineExamples.sequenceDetector());
    }

    private static String lights(ExampleSimulation simulation) {
        List<String> on = new ArrayList<>();
        for (String name : List.of("Rojo", "Amarillo", "Verde")) {
            if (simulation.led(name).equals(Value.ONE)) {
                on.add(name.toLowerCase());
            }
        }
        assertThat(on).as("exactly one light").hasSize(1);
        return on.get(0);
    }

    private static StateMachine machineOf(CircuitDocument document) {
        Component piece = document.components().stream()
                .filter(component -> SubcircuitTypes.definitionIdOf(component.typeId()).isPresent())
                .findFirst().orElseThrow();
        return document.definition(SubcircuitTypes.definitionIdOf(piece.typeId()).orElseThrow())
                .orElseThrow().machine().orElseThrow();
    }
}
