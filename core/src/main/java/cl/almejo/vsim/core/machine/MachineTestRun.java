package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.machine.MachineEvaluator.MachineStep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Runs a machine's test as the circuit runs the machine (decision 3 of hito 4). The run starts at the initial state;
 * column k shows the state during cycle k, that cycle's inputs, and the outputs just before edge k (the state's Moore
 * outputs and the Mealy outputs of the arrow those inputs select); then the edge happens, and its arrow's target is
 * the state of column k+1. Built on {@link MachineEvaluator}, the same code {@link StateMachineBehavior} runs, so a
 * test agrees with the simulation by construction: a bad condition never holds, two arrows that hold at once take the
 * first in model order, an input without a value is 0. Those are the machine's own problems, which say so; the run
 * does not stop for them.
 * <p>
 * Pure and headless: it never touches the engine, so the test table may re-run on the EDT after every draft edit (at
 * most {@link MachineTests#MAXIMUM_TESTS} × {@link MachineTests#MAXIMUM_COLUMNS} evaluations).
 */
public final class MachineTestRun {

    /** How one column, or a whole test, did. */
    public enum Verdict {
        /** Something was expected, and every expected value came out. */
        PASS,
        /** At least one expected value did not come out. */
        FAIL,
        /** Only «no importa»: nothing to compare. */
        NOTHING_EXPECTED
    }

    /** Why a test could not run any column. */
    public enum TestStop {
        /** The machine has no initial state, so no column has a state to start from. */
        NO_INITIAL_STATE
    }

    /**
     * One column of a run: the state during the cycle ({@code stateId}), the arrow its edge takes (empty when the
     * machine stays for lack of one), where the edge leads, every output pin's value just before the edge and, for the
     * outputs something was expected of, whether it came out; all by output pin id.
     */
    public record ColumnResult(String stateId, Optional<String> transitionId, String nextStateId,
                               Map<String, Boolean> outputs, Map<String, Boolean> matches, Verdict verdict) {

        public ColumnResult {
            Objects.requireNonNull(stateId, "stateId");
            Objects.requireNonNull(transitionId, "transitionId");
            Objects.requireNonNull(nextStateId, "nextStateId");
            outputs = Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
            matches = Collections.unmodifiableMap(new LinkedHashMap<>(matches));
            Objects.requireNonNull(verdict, "verdict");
        }
    }

    /** A run: one result per column of the test, or none with the reason it could not start. */
    public record TestResult(List<ColumnResult> columns, Optional<TestStop> stop) {

        public TestResult {
            columns = List.copyOf(columns);
            Objects.requireNonNull(stop, "stop");
        }

        /**
         * How the test did: {@link Verdict#FAIL} when a column fails, {@link Verdict#PASS} when some column passes
         * and none fails, {@link Verdict#NOTHING_EXPECTED} otherwise (also a test that could not start).
         */
        public Verdict verdict() {
            boolean passed = false;
            for (ColumnResult column : columns) {
                if (column.verdict() == Verdict.FAIL) {
                    return Verdict.FAIL;
                }
                passed |= column.verdict() == Verdict.PASS;
            }
            return passed ? Verdict.PASS : Verdict.NOTHING_EXPECTED;
        }
    }

    /** How a machine's tests did, for «Pruebas: 2, todas ✓»: how many, and how many by {@link Verdict}. */
    public record TestSummary(int tests, int passing, int failing, int nothingExpected) {
    }

    /** {@link #summary} per machine; machines are immutable values, held weakly as {@link MachineChecks} does. */
    private static final Map<StateMachine, TestSummary> SUMMARIES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private MachineTestRun() {
    }

    /** Runs {@code test} on {@code machine}; values of pins the machine does not have are ignored. */
    public static TestResult run(StateMachine machine, MachineTest test) {
        return run(MachineEvaluator.of(machine), Objects.requireNonNull(test, "test"));
    }

    /**
     * Runs every test of {@code machine}, in order, reading its conditions once: what the test table re-runs after a
     * draft edit (the chosen tab's columns and every tab's ✓ or ✗).
     */
    public static List<TestResult> runAll(StateMachine machine) {
        MachineEvaluator evaluator = MachineEvaluator.of(machine);
        return machine.tests().stream().map(test -> run(evaluator, test)).toList();
    }

    /** How the tests of {@code machine} do on it; kept per machine. */
    public static TestSummary summary(StateMachine machine) {
        Objects.requireNonNull(machine, "machine");
        TestSummary summary = SUMMARIES.get(machine);
        if (summary == null) {
            summary = summarized(machine);
            SUMMARIES.put(machine, summary);
        }
        return summary;
    }

    private static TestSummary summarized(StateMachine machine) {
        Map<Verdict, Integer> counts = new HashMap<>();
        for (TestResult result : runAll(machine)) {
            counts.merge(result.verdict(), 1, Integer::sum);
        }
        return new TestSummary(machine.tests().size(), counts.getOrDefault(Verdict.PASS, 0),
                counts.getOrDefault(Verdict.FAIL, 0), counts.getOrDefault(Verdict.NOTHING_EXPECTED, 0));
    }

    private static TestResult run(MachineEvaluator evaluator, MachineTest test) {
        StateMachine machine = evaluator.machine();
        Optional<String> initial = machine.initialStateId();
        if (initial.isEmpty()) {
            return new TestResult(List.of(), Optional.of(TestStop.NO_INITIAL_STATE));
        }
        List<ColumnResult> results = new ArrayList<>();
        String stateId = initial.get();
        for (TestColumn column : test.columns()) {
            MachineStep step = evaluator.next(stateId, inputsByName(machine, column)).orElseThrow();
            results.add(result(machine, stateId, step, column));
            stateId = step.nextStateId();
        }
        return new TestResult(results, Optional.empty());
    }

    /** The column's inputs as the conditions read them: by pin name, every input pin present (0 when unset). */
    private static Map<String, Boolean> inputsByName(StateMachine machine, TestColumn column) {
        Map<String, Boolean> values = new HashMap<>();
        for (MachinePin pin : machine.inputs()) {
            values.put(pin.name(), column.input(pin.id()));
        }
        return values;
    }

    private static ColumnResult result(StateMachine machine, String stateId, MachineStep step, TestColumn column) {
        Map<String, Boolean> outputs = new LinkedHashMap<>();
        Map<String, Boolean> matches = new LinkedHashMap<>();
        for (MachinePin pin : machine.outputs()) {
            boolean value = step.outputs().getOrDefault(pin.name(), false);
            outputs.put(pin.id(), value);
            LogicValue expected = column.expected(pin.id());
            if (expected != LogicValue.DONT_CARE) {
                matches.put(pin.id(), value == (expected == LogicValue.ONE));
            }
        }
        return new ColumnResult(stateId, step.transitionId(), step.nextStateId(), outputs, matches,
                verdict(matches));
    }

    private static Verdict verdict(Map<String, Boolean> matches) {
        if (matches.isEmpty()) {
            return Verdict.NOTHING_EXPECTED;
        }
        return matches.containsValue(false) ? Verdict.FAIL : Verdict.PASS;
    }
}
