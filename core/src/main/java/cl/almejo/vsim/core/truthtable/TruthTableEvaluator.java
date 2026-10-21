package cl.almejo.vsim.core.truthtable;

import cl.almejo.vsim.core.simulation.EventBudgetRun;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Computes a truth table (spec §5.2). Every row runs on a fresh {@link SimulationEngine}, so rows do not depend on
 * each other and the live simulation is never touched: the flat circuit is loaded, every input gets its value, and
 * the engine runs one event time after another until nothing is pending. A row still busy
 * {@link #settleTimeLimit} units after it started ({@value #SETTLE_TIME_LIMIT}, or {@value #DELAY_FACTOR} times the
 * slowest gate's delay when that is more), or after {@value #SETTLE_STEP_LIMIT} event times, did not settle. Runs on
 * any thread: it reads only the immutable {@link Preparation.Ready}.
 */
public final class TruthTableEvaluator {

    public static final long SETTLE_TIME_LIMIT = 100_000;
    /** The settle limit is at least this many times the largest gate delay in scope. */
    public static final long DELAY_FACTOR = 1_000;
    private static final String DELAY_PARAMETER = "delay";
    public static final int SETTLE_STEP_LIMIT = 1_000_000;
    /** How often (in event times) a long settle checks for cancellation. */
    private static final int CANCEL_CHECK_INTERVAL = 4096;

    private enum Outcome { SETTLED, UNSETTLED, CANCELLED }

    private TruthTableEvaluator() {
    }

    /**
     * Evaluates every row in order. {@code progress} receives the percentage done each time it grows (the last call
     * is 100); {@code cancelled} is polled between rows and during long settles. Empty when cancelled.
     */
    public static Optional<TruthTable> evaluate(Preparation.Ready ready, IntConsumer progress,
                                                BooleanSupplier cancelled) {
        int rowCount = ready.rowCount();
        long timeLimit = settleTimeLimit(ready.circuit());
        List<Row> rows = new ArrayList<>(rowCount);
        int reported = 0;
        for (int index = 0; index < rowCount; index++) {
            if (cancelled.getAsBoolean()) {
                return Optional.empty();
            }
            SimulationEngine engine = new SimulationEngine();
            engine.update(ready.circuit());
            List<Value> inputs = inputValues(ready.inputs(), index);
            for (int column = 0; column < inputs.size(); column++) {
                engine.setInput(ready.inputs().get(column).componentId(), inputs.get(column));
            }
            Outcome outcome = settle(engine, cancelled, timeLimit);
            if (outcome == Outcome.CANCELLED) {
                return Optional.empty();
            }
            rows.add(row(ready, engine, inputs, outcome == Outcome.SETTLED));
            int percent = (int) ((index + 1) * 100L / rowCount);
            if (percent > reported) {
                reported = percent;
                progress.accept(percent);
            }
        }
        return Optional.of(new TruthTable(ready.inputs(), ready.outputs(), rows, ready.hasFeedback()));
    }

    /**
     * The table for the AI (IA.1 Task 4): the rows of {@link #evaluate} in order, but at most {@code maximumRows} of
     * them and at most {@code eventBudget} events in all rows together (each row's loading included), so an
     * oscillating loop or a huge table returns in bounded time with the rows done so far. Runs on any thread.
     *
     * @throws IllegalArgumentException when a limit is not positive
     */
    public static BudgetedTruthTable evaluateWithin(Preparation.Ready ready, int maximumRows, long eventBudget) {
        if (maximumRows <= 0 || eventBudget <= 0) {
            throw new IllegalArgumentException("the limits must be positive: " + maximumRows + " rows, "
                    + eventBudget + " events");
        }
        int rowCount = ready.rowCount();
        int rowsToEvaluate = Math.min(rowCount, maximumRows);
        long timeLimit = settleTimeLimit(ready.circuit());
        List<Row> rows = new ArrayList<>(rowsToEvaluate);
        long eventsProcessed = 0;
        boolean budgetExceeded = false;
        for (int index = 0; index < rowsToEvaluate; index++) {
            SimulationEngine engine = new SimulationEngine();
            List<Value> inputs = inputValues(ready.inputs(), index);
            Outcome[] outcome = new Outcome[1];
            EventBudgetRun run = engine.runWithEventBudget(eventBudget - eventsProcessed, () -> {
                engine.update(ready.circuit());
                for (int column = 0; column < inputs.size(); column++) {
                    engine.setInput(ready.inputs().get(column).componentId(), inputs.get(column));
                }
                outcome[0] = settle(engine, () -> false, timeLimit);
            });
            eventsProcessed += run.eventsProcessed();
            if (run.exceeded()) {
                budgetExceeded = true;
                break;
            }
            rows.add(row(ready, engine, inputs, outcome[0] == Outcome.SETTLED));
            if (eventsProcessed >= eventBudget && index + 1 < rowsToEvaluate) {
                budgetExceeded = true;
                break;
            }
        }
        TruthTable table = new TruthTable(ready.inputs(), ready.outputs(), rows, ready.hasFeedback());
        return new BudgetedTruthTable(table, rowCount, eventsProcessed, rowsToEvaluate < rowCount, budgetExceeded);
    }

    private static Row row(Preparation.Ready ready, SimulationEngine engine, List<Value> inputs, boolean settled) {
        SimulationSnapshot snapshot = engine.snapshot();
        List<Value> outputs = ready.outputs().stream()
                .map(column -> snapshot.netValue(ready.outputNets().get(column.componentId())))
                .toList();
        return new Row(inputs, outputs, settled);
    }

    /** Row {@code index} counts in binary over all input bits, the first column holding the most significant ones. */
    static List<Value> inputValues(List<Column> inputs, int index) {
        List<Value> values = new ArrayList<>();
        int shift = inputs.stream().mapToInt(Column::width).sum();
        for (Column column : inputs) {
            shift -= column.width();
            values.add(Value.of(column.width(), (index >>> shift) & Value.maskFor(column.width())));
        }
        return values;
    }

    /**
     * How long a row of {@code circuit} may take to settle: {@value #SETTLE_TIME_LIMIT} units, or {@value #DELAY_FACTOR}
     * times the largest {@code delay} parameter of its components when that is more (ruling P2: a legal gate of delay
     * 200 000 must not read as oscillating).
     */
    static long settleTimeLimit(FlatCircuit circuit) {
        long slowest = circuit.components().stream()
                .map(component -> component.parameters().get(DELAY_PARAMETER))
                .filter(Number.class::isInstance)
                .mapToLong(delay -> ((Number) delay).longValue())
                .max()
                .orElse(0);
        return Math.max(SETTLE_TIME_LIMIT, DELAY_FACTOR * slowest);
    }

    private static Outcome settle(SimulationEngine engine, BooleanSupplier cancelled, long timeLimit) {
        long start = engine.now();
        for (int step = 0; step < SETTLE_STEP_LIMIT; step++) {
            OptionalLong next = engine.nextEventTime();
            if (next.isEmpty()) {
                return Outcome.SETTLED;
            }
            if (next.getAsLong() - start > timeLimit) {
                return Outcome.UNSETTLED;
            }
            if (step % CANCEL_CHECK_INTERVAL == CANCEL_CHECK_INTERVAL - 1 && cancelled.getAsBoolean()) {
                return Outcome.CANCELLED;
            }
            engine.runUntil(next.getAsLong());
        }
        return engine.nextEventTime().isEmpty() ? Outcome.SETTLED : Outcome.UNSETTLED;
    }
}
