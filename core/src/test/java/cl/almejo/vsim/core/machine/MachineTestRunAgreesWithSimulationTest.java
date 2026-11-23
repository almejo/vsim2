package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.machine.MachineTestRun.ColumnResult;
import cl.almejo.vsim.core.machine.MachineTestRun.TestResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.core.machine.MachineExamples.arrow;
import static cl.almejo.vsim.core.machine.MachineExamples.state;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review focus 5 of hito 4: a test says what the circuit does. The same input sequence goes through a real netlist
 * (switches, a clock pulsed with the machine's edge, LEDs) and through {@link MachineTestRun}; the state and the
 * outputs just before each edge must be the same, column by column, for Moore, Mealy and mixed machines on both edges.
 */
class MachineTestRunAgreesWithSimulationTest {

    /** x then y per column, varied enough to walk every arrow of the machines below. */
    private static final String X_SEQUENCE = "0110111001011010";
    private static final String Y_SEQUENCE = "0011010110011100";

    @Test
    void aMooreMachineAgreesOnTheFallingEdge() {
        assertAgreement(MachineExamples.twoOnesDetector());
    }

    /** A hand-made machine beyond every limit still simulates: its 17 inputs and 17 outputs are pins that work. */
    @Test
    void aMachineBeyondItsLimitsStillSimulates() {
        StateMachine machine = MachinesBeyondTheLimits.everyLimit();
        MachineBench bench = new MachineBench(machine);
        Map<String, Boolean> allOn = new LinkedHashMap<>();
        machine.inputNames().forEach(name -> allOn.put(name, true));

        bench.setInputs(allOn);
        assertThat(bench.stateId()).isEqualTo("s0");
        assertThat(bench.outputs()).hasSize(MachinesBeyondTheLimits.OUTPUTS).doesNotContainValue(false);
        bench.pulse();
        assertThat(bench.stateId()).isEqualTo("s1");
        allOn.put("i16", false);
        bench.setInputs(allOn);
        bench.pulse();
        bench.pulse();
        assertThat(bench.stateId()).as("back to S0, and i16 = 0 keeps it there").isEqualTo("s0");
    }

    @Test
    void aMooreMachineAgreesOnTheRisingEdge() {
        assertAgreement(MachineExamples.twoOnesDetector().withEdge(StateMachine.EDGE_RISING));
    }

    @Test
    void aMealyMachineAgreesOnBothEdges() {
        assertAgreement(mealy());
        assertAgreement(mealy().withEdge(StateMachine.EDGE_RISING));
    }

    @Test
    void aMixedMachineWithANondeterministicPairAndABadConditionAgreesOnBothEdges() {
        assertAgreement(mixed());
        assertAgreement(mixed().withEdge(StateMachine.EDGE_RISING));
        ColumnResult bothHold = MachineTestRun.run(mixed(), sequence(mixed())).columns().get(11);
        assertThat(bothHold.stateId()).as("x = y = 1 in S0").isEqualTo("s0");
        assertThat(bothHold.transitionId()).as("the first arrow wins").contains("t0");
    }

    private static void assertAgreement(StateMachine machine) {
        MachineTest test = sequence(machine);
        TestResult run = MachineTestRun.run(machine, test);
        MachineBench bench = new MachineBench(machine);
        List<String> simulatedStates = new ArrayList<>();
        List<Map<String, Boolean>> simulatedOutputs = new ArrayList<>();
        for (TestColumn column : test.columns()) {
            bench.setInputs(byName(machine, column));
            simulatedStates.add(bench.stateId());
            simulatedOutputs.add(bench.outputs());
            bench.pulse();
        }
        String lastState = bench.stateId();

        assertThat(run.columns()).extracting(ColumnResult::stateId).as("states")
                .containsExactlyElementsOf(simulatedStates);
        assertThat(run.columns()).extracting(ColumnResult::outputs).as("outputs before each edge")
                .containsExactlyElementsOf(simulatedOutputs);
        assertThat(run.columns().get(run.columns().size() - 1).nextStateId()).as("where the last edge leads")
                .isEqualTo(lastState);
        assertThat(Set.copyOf(simulatedStates)).as("the sequence walks the machine")
                .hasSize(machine.states().size());
    }

    /** The test that gives {@code machine}'s inputs {@link #X_SEQUENCE} and {@link #Y_SEQUENCE} (ids are names). */
    private static MachineTest sequence(StateMachine machine) {
        MachineTest test = MachineTest.empty("p1", "Secuencia", X_SEQUENCE.length());
        for (int column = 0; column < X_SEQUENCE.length(); column++) {
            if (machine.inputNames().contains("x")) {
                test = MachineTests.withInput(test, column, "x", X_SEQUENCE.charAt(column) == '1');
            }
            if (machine.inputNames().contains("y")) {
                test = MachineTests.withInput(test, column, "y", Y_SEQUENCE.charAt(column) == '1');
            }
        }
        return test;
    }

    private static Map<String, Boolean> byName(StateMachine machine, TestColumn column) {
        Map<String, Boolean> values = new LinkedHashMap<>();
        for (MachinePin pin : machine.inputs()) {
            values.put(pin.name(), column.input(pin.id()));
        }
        return values;
    }

    /** S0 goes to S1; S1 loops, with Mealy Z = 1 when x. */
    private static StateMachine mealy() {
        return new StateMachine(
                List.of(state("s0", "S0", 0, List.of()), state("s1", "S1", 100, List.of())),
                List.of(arrow("t0", "s0", "s1", ""),
                        new MachineTransition("t1", "s1", "s1", "x", List.of(new OutputAssignment("Z", true))),
                        arrow("t2", "s1", "s1", "")),
                Optional.of("s0"), List.of("x"), List.of("Z"), false, false, StateMachine.EDGE_FALLING, List.of());
    }

    /**
     * Moore {@code Z} and Mealy {@code W}; in S0 both {@code x} and {@code y} may hold (the first arrow wins, as in
     * the circuit), and S1 has an arrow whose condition does not parse (it never holds).
     */
    private static StateMachine mixed() {
        List<OutputAssignment> mealyW = List.of(new OutputAssignment("W", true));
        return new StateMachine(
                List.of(state("s0", "S0", 0, List.of()),
                        state("s1", "S1", 100, List.of(new OutputAssignment("Z", true))),
                        state("s2", "S2", 200, List.of())),
                List.of(new MachineTransition("t0", "s0", "s1", "x", mealyW),
                        arrow("t1", "s0", "s2", "y"),
                        arrow("t2", "s0", "s0", ""),
                        new MachineTransition("t3", "s1", "s2", "x y'", mealyW),
                        arrow("t4", "s1", "s0", "x +"),
                        arrow("t5", "s1", "s1", ""),
                        arrow("t6", "s2", "s0", "y'"),
                        new MachineTransition("t7", "s2", "s2", "y", mealyW)),
                Optional.of("s0"), List.of("x", "y"), List.of("Z", "W"), false, false, StateMachine.EDGE_FALLING,
                List.of());
    }
}
