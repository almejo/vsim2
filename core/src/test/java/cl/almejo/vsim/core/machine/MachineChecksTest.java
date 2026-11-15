package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.component.VisibleState.MachineVisibleState;
import cl.almejo.vsim.core.machine.MachineFinding.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.core.machine.MachineExamples.arrow;
import static cl.almejo.vsim.core.machine.MachineExamples.state;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/** The problems of a machine's diagram (hito 2): one test per kind, with the mockup's examples. */
class MachineChecksTest {

    /** The plan's budget for the biggest machine, checked from scratch. */
    private static final long BIGGEST_MACHINE_MILLISECONDS = 200;

    @Test
    void theDetectorOfTheMockupHasNoProblem() {
        assertThat(MachineChecks.check(MachineExamples.twoOnesDetector())).isEmpty();
    }

    @Test
    void aMachineWithoutStatesSaysSo() {
        StateMachine empty = machine(List.of(), List.of(), Optional.empty(), List.of(), List.of());

        assertThat(MachineChecks.check(empty)).extracting(MachineFinding::kind).containsExactly(Kind.NO_STATES);
    }

    @Test
    void noInitialStateIsAnErrorAndSkipsTheReachability() {
        StateMachine machine = MachineExamples.twoOnesDetector().withInitialStateId(Optional.empty());

        assertThat(MachineChecks.check(machine)).extracting(MachineFinding::kind)
                .containsExactly(Kind.NO_INITIAL_STATE);
        assertThat(Kind.NO_INITIAL_STATE.isWarning()).isFalse();
    }

    @Test
    void aBadConditionIsReportedOnceWithItsColumnAndLeftOutOfTheOtherChecks() {
        StateMachine machine = MachineExamples.twoOnesDetector()
                .withTransition("t2", transition -> transition.withCondition("x +"));

        List<MachineFinding> findings = MachineChecks.check(machine);

        assertThat(findings).filteredOn(finding -> finding.transitionIds().contains("t2")).singleElement()
                .satisfies(finding -> {
                    assertThat(finding.kind()).isEqualTo(Kind.BAD_CONDITION);
                    assertThat(finding.column()).isEqualTo(
                            MachineConditions.parse("x +").problem().orElseThrow().column());
                });
        assertThat(findings).extracting(MachineFinding::kind).doesNotContain(Kind.NONDETERMINISTIC)
                .as("S2 is reached only through t2, which never holds").contains(Kind.UNREACHABLE_STATE);
    }

    @Test
    void twoOtherwiseArrowsFromOneStateAreAnError() {
        StateMachine detector = MachineExamples.twoOnesDetector();
        List<MachineTransition> transitions = new ArrayList<>(detector.transitions());
        transitions.add(arrow("t9", "s1", "s2", ""));

        List<MachineFinding> findings = MachineChecks.check(detector.withTransitions(transitions));

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(Kind.TWO_OTHERWISE);
            assertThat(finding.stateIds()).containsExactly("s1");
            assertThat(finding.transitionIds()).containsExactly("t3", "t9");
        });
    }

    @Test
    void twoArrowsTrueAtOnceGiveTheInputsThatShowIt() {
        StateMachine machine = machine(List.of(state("a", "A", 0, List.of()), state("b", "B", 100, List.of()),
                        state("c", "C", 200, List.of())),
                List.of(arrow("t1", "a", "b", "x"), arrow("t2", "a", "c", "x + y"), arrow("t3", "a", "a", ""),
                        arrow("t4", "b", "a", ""), arrow("t5", "c", "a", "")),
                Optional.of("a"), List.of("x", "y"), List.of());

        assertThat(MachineChecks.check(machine)).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(Kind.NONDETERMINISTIC);
            assertThat(finding.stateIds()).containsExactly("a");
            assertThat(finding.transitionIds()).containsExactly("t1", "t2");
            assertThat(finding.witness()).containsExactly(entry("x", true), entry("y", false));
        });
    }

    @Test
    void twoArrowsTrueAtOnceToTheSameStateWithTheSameOutputsAreFine() {
        StateMachine machine = machine(List.of(state("a", "A", 0, List.of()), state("b", "B", 100, List.of())),
                List.of(arrow("t1", "a", "b", "x"), arrow("t2", "a", "b", "x + y"), arrow("t3", "a", "a", ""),
                        arrow("t4", "b", "a", "")),
                Optional.of("a"), List.of("x", "y"), List.of());

        assertThat(MachineChecks.check(machine)).isEmpty();
    }

    @Test
    void twoArrowsToTheSameStateWithOtherMealyOutputsAreNot() {
        StateMachine machine = machine(List.of(state("a", "A", 0, List.of()), state("b", "B", 100, List.of())),
                List.of(arrow("t1", "a", "b", "x").withOutputs(List.of(new OutputAssignment("Z", true))),
                        arrow("t2", "a", "b", "x + y"), arrow("t3", "a", "a", ""), arrow("t4", "b", "a", "")),
                Optional.of("a"), List.of("x", "y"), List.of("Z"));

        assertThat(MachineChecks.check(machine)).extracting(MachineFinding::kind)
                .containsExactly(Kind.NONDETERMINISTIC);
    }

    @Test
    void otherwiseArrowsNeverMakeTwoArrowsTrueAtOnce() {
        assertThat(MachineChecks.check(MachineExamples.twoOnesDetector()))
                .extracting(MachineFinding::kind).doesNotContain(Kind.NONDETERMINISTIC);
    }

    @Test
    void aConditionReadingAnOutputOrAReservedPinIsAnError() {
        StateMachine machine = MachineExamples.twoOnesDetector()
                .withTransition("t1", transition -> transition.withCondition("x Z"))
                .withTransition("t4", transition -> transition.withCondition("x CLK"));

        List<MachineFinding> findings = MachineChecks.check(machine).stream()
                .filter(finding -> finding.kind() == Kind.READS_OUTPUT_OR_RESERVED).toList();

        assertThat(findings).extracting(MachineFinding::transitionIds).containsExactly(List.of("t1"), List.of("t4"));
        assertThat(findings).extracting(MachineFinding::names).containsExactly(List.of("Z"), List.of("CLK"));
    }

    @Test
    void namesBeyondSixteenInputsOrOutputsAreErrors() {
        List<String> inputs = new ArrayList<>();
        List<String> outputs = new ArrayList<>();
        for (int index = 0; index < MachineLimits.MAXIMUM_INPUTS; index++) {
            inputs.add("x" + index);
            outputs.add("Z" + index);
        }
        StringBuilder everyInput = new StringBuilder("q");
        inputs.forEach(name -> everyInput.append(" + ").append(name));
        List<OutputAssignment> everyOutput = new ArrayList<>();
        outputs.forEach(name -> everyOutput.add(new OutputAssignment(name, true)));
        everyOutput.add(new OutputAssignment("W", true));
        StateMachine machine = machine(List.of(state("a", "A", 0, everyOutput)),
                List.of(arrow("t1", "a", "a", everyInput.toString())), Optional.of("a"), inputs, outputs);

        List<MachineFinding> findings = MachineChecks.check(machine).stream()
                .filter(finding -> finding.kind() == Kind.TOO_MANY_NAMES).toList();

        assertThat(findings).extracting(MachineFinding::names).containsExactly(List.of("q"), List.of("W"));
        assertThat(findings.get(0).transitionIds()).containsExactly("t1");
        assertThat(findings.get(1).stateIds()).containsExactly("a");
    }

    @Test
    void aStateNoArrowReachesIsAWarning() {
        StateMachine detector = MachineExamples.twoOnesDetector();
        List<MachineState> states = new ArrayList<>(detector.states());
        states.add(state("c", "C", 300, List.of(new OutputAssignment("Z", false))));

        assertThat(MachineChecks.check(detector.withStates(states))).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(Kind.UNREACHABLE_STATE);
            assertThat(finding.kind().isWarning()).isTrue();
            assertThat(finding.stateIds()).containsExactly("c");
        });
    }

    @Test
    void aStateThatDoesNotSayWhatHappensForSomeInputStaysAndIsWarned() {
        StateMachine machine = machine(List.of(state("a", "A", 0, List.of()), state("b", "B", 100, List.of())),
                List.of(arrow("t1", "a", "b", ""), arrow("t2", "b", "a", "x")),
                Optional.of("a"), List.of("x"), List.of());

        assertThat(MachineChecks.check(machine)).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(Kind.STAYS_FOR_SOME_INPUT);
            assertThat(finding.stateIds()).containsExactly("b");
            assertThat(finding.witness()).containsExactly(entry("x", false));
        });
    }

    @Test
    void aStateWithoutArrowsSimplyStays() {
        assertThat(MachineChecks.check(StateMachine.initial())).isEmpty();
    }

    @Test
    void anOutputNoStateOrArrowAssignsIsWarnedOnce() {
        StateMachine detector = MachineExamples.twoOnesDetector();
        StateMachine machine = detector.withPinNames(detector.inputNames(), List.of("Z", "Y"));

        assertThat(MachineChecks.check(machine)).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(Kind.OUTPUT_NEVER_ASSIGNED);
            assertThat(finding.names()).containsExactly("Y");
        });
    }

    @Test
    void anInputNoConditionReadsIsWarned() {
        StateMachine detector = MachineExamples.twoOnesDetector();
        StateMachine machine = detector.withPinNames(List.of("x", "w"), detector.outputNames());

        assertThat(MachineChecks.check(machine)).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(Kind.UNUSED_INPUT);
            assertThat(finding.names()).containsExactly("w");
        });
    }

    @Test
    void anArrowGivingAnOutputAnotherValueThanItsStateIsWarned() {
        StateMachine machine = MachineExamples.twoOnesDetector().withTransition("t5",
                transition -> transition.withOutputs(List.of(new OutputAssignment("Z", false))));

        assertThat(MachineChecks.check(machine)).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(Kind.MOORE_MEALY_CLASH);
            assertThat(finding.stateIds()).containsExactly("s2");
            assertThat(finding.transitionIds()).containsExactly("t5");
            assertThat(finding.names()).containsExactly("Z");
        });
    }

    @Test
    void theFindingsOfOneMachineAreComputedOnce() {
        StateMachine machine = MachineExamples.twoOnesDetector().withInitialStateId(Optional.empty());

        assertThat(MachineChecks.check(machine)).isSameAs(MachineChecks.check(machine));
    }

    @Test
    void theBiggestMachineIsCheckedQuickly() {
        StateMachine biggest = biggestMachine();
        MachineChecks.check(MachineExamples.twoOnesDetector().withEdge(StateMachine.EDGE_RISING)); // warm up

        long start = System.nanoTime();
        List<MachineFinding> findings = MachineChecks.check(biggest);
        long milliseconds = (System.nanoTime() - start) / 1_000_000;

        assertThat(findings).extracting(MachineFinding::kind).contains(Kind.NONDETERMINISTIC);
        assertThat(milliseconds).as("64 states, 16 inputs").isLessThan(BIGGEST_MACHINE_MILLISECONDS);
    }

    @Test
    void anUndefinedInputOrClockAtTheEdgeIsWarnedPerCopy() {
        MachineVisibleState running = new MachineVisibleState("s1", "S1", Optional.of("s2"), Optional.of("t2"),
                List.of("x"), true);

        List<MachineFinding> findings = MachineChecks.runtime(running);

        assertThat(findings).extracting(MachineFinding::kind)
                .containsExactly(Kind.INPUT_UNDEFINED_AT_EDGE, Kind.CLOCK_UNDEFINED);
        assertThat(findings.get(0).names()).containsExactly("x");
        assertThat(findings).allMatch(finding -> finding.kind().isWarning());
        assertThat(MachineChecks.runtime(new MachineVisibleState("s1", "S1", Optional.empty(), Optional.empty())))
                .isEmpty();
    }

    /** 64 states and 16 inputs; each state has two arrows over every input that hold together only when every input is 1. */
    private static StateMachine biggestMachine() {
        List<String> inputs = new ArrayList<>();
        for (int index = 0; index < MachineLimits.MAXIMUM_INPUTS; index++) {
            inputs.add("x" + index);
        }
        String all = String.join(" ", inputs);
        String any = String.join(" + ", inputs);
        List<MachineState> states = new ArrayList<>();
        List<MachineTransition> transitions = new ArrayList<>();
        for (int index = 0; index < MachineLimits.MAXIMUM_STATES; index++) {
            states.add(state("s" + index, "S" + index, index * 100, List.of()));
        }
        for (int index = 0; index < MachineLimits.MAXIMUM_STATES; index++) {
            int next = (index + 1) % MachineLimits.MAXIMUM_STATES;
            int after = (index + 2) % MachineLimits.MAXIMUM_STATES;
            transitions.add(arrow("a" + index, "s" + index, "s" + next, all));
            transitions.add(arrow("b" + index, "s" + index, "s" + after, any));
            transitions.add(arrow("c" + index, "s" + index, "s" + index, ""));
        }
        return machine(states, transitions, Optional.of("s0"), inputs, List.of());
    }

    private static StateMachine machine(List<MachineState> states, List<MachineTransition> transitions,
                                        Optional<String> initial, List<String> inputs, List<String> outputs) {
        return new StateMachine(states, transitions, initial, inputs, outputs, false, false,
                StateMachine.EDGE_FALLING, List.of());
    }
}
