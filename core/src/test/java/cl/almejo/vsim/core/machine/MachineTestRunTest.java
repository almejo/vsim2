package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.machine.MachineTestRun.ColumnResult;
import cl.almejo.vsim.core.machine.MachineTestRun.TestResult;
import cl.almejo.vsim.core.machine.MachineTestRun.TestStop;
import cl.almejo.vsim.core.machine.MachineTestRun.TestSummary;
import cl.almejo.vsim.core.machine.MachineTestRun.Verdict;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.core.machine.MachineExamples.arrow;
import static cl.almejo.vsim.core.machine.MachineExamples.state;
import static org.assertj.core.api.Assertions.assertThat;

/** A test runs from the initial state, column by column, as the circuit runs the machine edge by edge. */
class MachineTestRunTest {

    /** The mockup's input sequence for the detector of two 1s: x = 0110 1100. */
    private static final String MOCKUP_INPUTS = "01101100";

    private final StateMachine detector = MachineExamples.twoOnesDetector();

    @Test
    void theDetectorGivesTheStateAndZOfEveryColumn() {
        TestResult result = MachineTestRun.run(detector, withInputs("x", MOCKUP_INPUTS));

        assertThat(result.stop()).isEmpty();
        assertThat(result.columns()).extracting(ColumnResult::stateId)
                .containsExactly("s0", "s0", "s1", "s2", "s0", "s1", "s2", "s0");
        assertThat(result.columns()).extracting(column -> column.outputs().get("Z"))
                .containsExactly(false, false, false, true, false, false, true, false);
        assertThat(result.columns()).extracting(ColumnResult::nextStateId)
                .containsExactly("s0", "s1", "s2", "s0", "s1", "s2", "s0", "s0");
        assertThat(result.columns().get(1).transitionId()).contains("t1");
    }

    @Test
    void aMealyOutputTakesTheInputsOfItsOwnColumn() {
        StateMachine mealy = new StateMachine(
                List.of(state("s0", "S0", 0, List.of()), state("s1", "S1", 100, List.of())),
                List.of(arrow("t0", "s0", "s1", ""),
                        new MachineTransition("t1", "s1", "s1", "x", List.of(new OutputAssignment("Z", true))),
                        arrow("t2", "s1", "s1", "")),
                Optional.of("s0"), List.of("x"), List.of("Z"), false, false, StateMachine.EDGE_FALLING, List.of());

        TestResult result = MachineTestRun.run(mealy, withInputs("x", "0101"));

        assertThat(result.columns()).extracting(column -> column.outputs().get("Z"))
                .containsExactly(false, true, false, true);
        assertThat(result.columns()).extracting(ColumnResult::transitionId)
                .containsExactly(Optional.of("t0"), Optional.of("t1"), Optional.of("t2"), Optional.of("t1"));
    }

    @Test
    void aRisingEdgeMachineGivesTheSameColumns() {
        MachineTest test = withInputs("x", MOCKUP_INPUTS);

        TestResult rising = MachineTestRun.run(detector.withEdge(StateMachine.EDGE_RISING), test);

        assertThat(rising).isEqualTo(MachineTestRun.run(detector, test));
    }

    @Test
    void aMachineWithoutAnInitialStateRunsNoColumn() {
        StateMachine noInitial = detector.withInitialStateId(Optional.empty());

        TestResult result = MachineTestRun.run(noInitial, withInputs("x", MOCKUP_INPUTS));

        assertThat(result.stop()).contains(TestStop.NO_INITIAL_STATE);
        assertThat(result.columns()).isEmpty();
    }

    @Test
    void aColumnPassesFailsOrExpectsNothing() {
        MachineTest test = withInputs("x", MOCKUP_INPUTS);
        test = MachineTests.withExpected(test, 3, "Z", LogicValue.ONE);
        test = MachineTests.withExpected(test, 4, "Z", LogicValue.ONE);

        TestResult result = MachineTestRun.run(detector, test);

        assertThat(result.columns()).extracting(ColumnResult::verdict).containsExactly(
                Verdict.NOTHING_EXPECTED, Verdict.NOTHING_EXPECTED, Verdict.NOTHING_EXPECTED, Verdict.PASS,
                Verdict.FAIL, Verdict.NOTHING_EXPECTED, Verdict.NOTHING_EXPECTED, Verdict.NOTHING_EXPECTED);
        assertThat(result.columns().get(3).matches()).isEqualTo(Map.of("Z", true));
        assertThat(result.columns().get(4).matches()).isEqualTo(Map.of("Z", false));
        assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
    }

    @Test
    void noImportaNeverFails() {
        TestResult result = MachineTestRun.run(detector, withInputs("x", MOCKUP_INPUTS));

        assertThat(result.columns()).allSatisfy(column -> {
            assertThat(column.verdict()).isEqualTo(Verdict.NOTHING_EXPECTED);
            assertThat(column.matches()).isEmpty();
        });
        assertThat(result.verdict()).isEqualTo(Verdict.NOTHING_EXPECTED);
    }

    @Test
    void aTestWhoseExpectedValuesAllHoldPasses() {
        MachineTest test = MachineTests.withExpected(withInputs("x", MOCKUP_INPUTS), 6, "Z", LogicValue.ONE);

        assertThat(MachineTestRun.run(detector, test).verdict()).isEqualTo(Verdict.PASS);
    }

    @Test
    void theRunFollowsPinsByIdSoARenameChangesNothing() {
        MachineTest test = MachineTests.withExpected(withInputs("x", MOCKUP_INPUTS), 3, "Z", LogicValue.ONE);
        StateMachine renamed = MachinePinEdits.renamed(MachinePinEdits.renamed(detector,
                MachinePinEdits.Side.INPUT, "x", "a"), MachinePinEdits.Side.OUTPUT, "Z", "Y");

        TestResult result = MachineTestRun.run(renamed, test);

        assertThat(result.columns()).extracting(ColumnResult::stateId)
                .containsExactly("s0", "s0", "s1", "s2", "s0", "s1", "s2", "s0");
        assertThat(result.columns().get(3).outputs()).isEqualTo(Map.of("Z", true));
        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    }

    @Test
    void valuesOfPinsTheMachineDoesNotHaveAreIgnored() {
        MachineTest test = MachineTests.withExpected(MachineTests.withInput(withInputs("x", MOCKUP_INPUTS), 0,
                "nadie", true), 0, "fantasma", LogicValue.ONE);

        TestResult result = MachineTestRun.run(detector, test);

        assertThat(result.columns().get(0).verdict()).isEqualTo(Verdict.NOTHING_EXPECTED);
        assertThat(result.columns().get(0).outputs()).containsOnlyKeys("Z");
    }

    @Test
    void theSummaryCountsPassingFailingAndNothingExpected() {
        MachineTest passing = MachineTests.withExpected(withInputs("x", MOCKUP_INPUTS), 3, "Z", LogicValue.ONE);
        MachineTest failing = MachineTests.withExpected(withInputs("x", MOCKUP_INPUTS), 3, "Z", LogicValue.ZERO)
                .withId("p2");
        MachineTest nothing = withInputs("x", MOCKUP_INPUTS).withId("p3");

        TestSummary summary = MachineTestRun.summary(detector.withTests(List.of(passing, failing, nothing)));

        assertThat(summary).isEqualTo(new TestSummary(3, 1, 1, 1));
        assertThat(MachineTestRun.summary(detector)).isEqualTo(new TestSummary(0, 0, 0, 0));
    }

    @Test
    void runningAllTestsGivesEachTestsOwnRunInOrder() {
        MachineTest first = withInputs("x", MOCKUP_INPUTS);
        MachineTest second = withInputs("x", "1111").withId("p2");
        StateMachine machine = detector.withTests(List.of(first, second));

        assertThat(MachineTestRun.runAll(machine)).containsExactly(MachineTestRun.run(machine, first),
                MachineTestRun.run(machine, second));
    }

    /** A test {@code p1} whose input {@code pinId} takes the digits of {@code values}, one column each. */
    static MachineTest withInputs(String pinId, String values) {
        MachineTest test = MachineTest.empty("p1", "Prueba 1", values.length());
        for (int column = 0; column < values.length(); column++) {
            test = MachineTests.withInput(test, column, pinId, values.charAt(column) == '1');
        }
        return test;
    }
}
