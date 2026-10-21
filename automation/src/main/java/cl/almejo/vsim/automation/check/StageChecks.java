package cl.almejo.vsim.automation.check;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.PinReference;
import cl.almejo.vsim.automation.check.StageCheck.Check;
import cl.almejo.vsim.automation.check.StageCheck.CheckResult;
import cl.almejo.vsim.automation.check.StageCheck.ExpectedRow;
import cl.almejo.vsim.automation.check.StageCheck.ExpectedSignal;
import cl.almejo.vsim.automation.check.StageCheck.LayoutView;
import cl.almejo.vsim.automation.check.StageCheck.Status;
import cl.almejo.vsim.automation.read.ProblemView;
import cl.almejo.vsim.automation.read.ProblemsView;
import cl.almejo.vsim.automation.simulate.SignalValue;
import cl.almejo.vsim.automation.simulate.SignalsView;
import cl.almejo.vsim.automation.simulate.SimulationOperations;
import cl.almejo.vsim.automation.simulate.TruthTableView;
import cl.almejo.vsim.automation.simulate.TruthTableView.ColumnView;
import cl.almejo.vsim.automation.simulate.TruthTableView.RowView;
import cl.almejo.vsim.automation.simulate.ValueText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Runs a stage's checks (spec §3, A§9) and reports only what VSim measured: a check passes only when it finished and
 * every expectation held; a refusal or anything cut short makes it {@link Status#NOT_FINISHED}, never a pass. The AI's
 * own words never replace these results.
 */
public final class StageChecks {

    /** At most this many checks in one call. */
    public static final int MAXIMUM_CHECKS = 20;
    /** At most this many mismatches, problems or faults are listed per check (all are counted). */
    static final int LISTED = 20;

    /** What the checks need from the rest of the automation API. */
    public interface Measurements {
        ProblemsView problems(DocumentId document);

        LayoutView layout(DocumentId document, Set<String> elementIds);

        TruthTableView truthTable(DocumentId document, Set<String> elementIds, int maximumRows, int eventBudget);

        SignalsView signals(DocumentId document, List<PinReference> pins);
    }

    private final Measurements measurements;

    public StageChecks(Measurements measurements) {
        this.measurements = Objects.requireNonNull(measurements, "measurements");
    }

    /**
     * Runs every check in order. A circuit that is not open fails the whole call; any other refusal only makes its
     * check NOT_FINISHED with the refusal in its details.
     */
    public StageCheck.Result run(StageCheck.Request request) {
        if (request.checks().isEmpty()) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "A stage check runs at least one check");
        }
        if (request.checks().size() > MAXIMUM_CHECKS) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "At most " + MAXIMUM_CHECKS
                    + " checks per call", Map.of("maximum", MAXIMUM_CHECKS));
        }
        DocumentVersion before = measurements.problems(request.document()).version();
        List<CheckResult> results = new ArrayList<>();
        for (Check check : request.checks()) {
            results.add(runOne(request.document(), check));
        }
        DocumentVersion after = measurements.problems(request.document()).version();
        boolean passed = results.stream().allMatch(result -> result.status() == Status.PASSED);
        return new StageCheck.Result(request.document(), before, after, request.taskId(), request.stage(), results,
                passed);
    }

    private CheckResult runOne(DocumentId document, Check check) {
        try {
            return switch (check) {
                case Check.TruthTable truthTable -> truthTable(document, truthTable);
                case Check.Problems problems -> problems(document);
                case Check.Layout layout -> layout(document, layout);
                case Check.Signals signals -> signals(document, signals);
            };
        } catch (AutomationException refusal) {
            if (refusal.error() == AutomationError.DOCUMENT_CLOSED) {
                throw refusal;
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("error", refusal.error().name());
            details.put("message", refusal.getMessage());
            details.put("recovery", refusal.recovery());
            return new CheckResult(check.kind(), Status.NOT_FINISHED, "VSim could not run this check: "
                    + refusal.getMessage(), details);
        }
    }

    // --- truth table ---

    private CheckResult truthTable(DocumentId document, Check.TruthTable check) {
        if (check.expected().isEmpty()) {
            return new CheckResult(check.kind(), Status.NOT_FINISHED, "No expected rows were given: nothing to "
                    + "compare (give the rows the table must have)", Map.of());
        }
        TruthTableView table = measurements.truthTable(document, check.elementIds(), check.maximumRows(),
                check.eventBudget());
        Map<List<String>, RowView> rows = new HashMap<>();
        table.rows().forEach(row -> rows.put(row.inputs(), row));
        List<Map<String, Object>> mismatches = new ArrayList<>();
        int failed = 0;
        int missing = 0;
        for (ExpectedRow expected : check.expected()) {
            List<String> inputs = inputCells(table, expected);
            RowView row = rows.get(inputs);
            if (row == null) {
                missing++;
                continue;
            }
            Map<String, String> differences = differences(table, expected, row);
            if (!row.settled() || !differences.isEmpty()) {
                failed++;
                if (mismatches.size() < LISTED) {
                    Map<String, Object> mismatch = new LinkedHashMap<>();
                    mismatch.put("inputs", expected.inputs());
                    mismatch.put("expected", expected.outputs());
                    mismatch.put("measured", measuredOutputs(table, row));
                    mismatch.put("settled", row.settled());
                    mismatches.add(mismatch);
                }
            }
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("version", table.version().value());
        details.put("inputs", table.inputs().stream().map(ColumnView::name).toList());
        details.put("outputs", table.outputs().stream().map(ColumnView::name).toList());
        details.put("totalRows", table.totalRows());
        details.put("evaluatedRows", table.rows().size());
        details.put("expectedRows", check.expected().size());
        details.put("failedRows", failed);
        details.put("rowsNotEvaluated", missing);
        details.put("eventsProcessed", table.eventsProcessed());
        details.put("mismatches", mismatches);
        if (failed > 0) {
            return new CheckResult(check.kind(), Status.FAILED, failed + " of " + check.expected().size()
                    + " expected rows differ from what VSim measured", details);
        }
        if (missing > 0 || table.budgetExceeded()) {
            return new CheckResult(check.kind(), Status.NOT_FINISHED, missing + " expected rows were not evaluated ("
                    + notFinishedBecause(table) + "); the rows evaluated match", details);
        }
        return new CheckResult(check.kind(), Status.PASSED, "All " + check.expected().size()
                + " expected rows match what VSim measured", details);
    }

    /**
     * Why a table stopped short, and how to go on: a row that never settles (an oscillation, or a latch whose rows
     * hold their state, which a fresh copy cannot know) runs until the budget is gone, so the AI needs to hear that
     * it may raise the budget or check that behaviour with pin values instead.
     */
    private static String notFinishedBecause(TruthTableView table) {
        if (table.budgetExceeded()) {
            return "the event budget of " + table.eventBudget() + " events ran out after " + table.rows().size()
                    + " rows; a row that never settles, such as an oscillation or a latch left to hold its state, "
                    + "can use it all: give a larger eventBudget (at most "
                    + SimulationOperations.MAXIMUM_EVENT_BUDGET
                    + "), or leave such rows out and check them with signals after vsim_set_inputs";
        }
        return "the row limit was reached: give a larger maximumRows (at most "
                + SimulationOperations.MAXIMUM_ROWS + ")";
    }

    /** The cells of the row {@code expected} names, in the table's input order. */
    private static List<String> inputCells(TruthTableView table, ExpectedRow expected) {
        List<String> cells = new ArrayList<>();
        Set<String> used = new java.util.HashSet<>();
        for (ColumnView column : table.inputs()) {
            String key = keyOf(column, expected.inputs()).orElseThrow(() -> new AutomationException(
                    AutomationError.INVALID_PARAMETER, "An expected row does not give the input " + column.name(),
                    Map.of("inputs", names(table.inputs()))));
            used.add(key);
            cells.add(cell(expected.inputs().get(key), column));
        }
        for (String key : expected.inputs().keySet()) {
            if (!used.contains(key)) {
                throw new AutomationException(AutomationError.INVALID_PARAMETER, "The table has no input " + key,
                        Map.of("inputs", names(table.inputs())));
            }
        }
        return cells;
    }

    /** The outputs {@code expected} names whose measured cell differs, as name → measured cell. */
    private static Map<String, String> differences(TruthTableView table, ExpectedRow expected, RowView row) {
        Map<String, String> differences = new LinkedHashMap<>();
        for (Map.Entry<String, String> output : expected.outputs().entrySet()) {
            int index = outputIndex(table, output.getKey());
            ColumnView column = table.outputs().get(index);
            String measured = row.outputs().get(index);
            if (!measured.equals(cell(output.getValue(), column))) {
                differences.put(column.name(), measured);
            }
        }
        return differences;
    }

    private static Map<String, String> measuredOutputs(TruthTableView table, RowView row) {
        Map<String, String> measured = new LinkedHashMap<>();
        for (int index = 0; index < table.outputs().size(); index++) {
            measured.put(table.outputs().get(index).name(), row.outputs().get(index));
        }
        return measured;
    }

    private static int outputIndex(TruthTableView table, String key) {
        for (int index = 0; index < table.outputs().size(); index++) {
            ColumnView column = table.outputs().get(index);
            if (column.name().equals(key) || column.elementId().equals(key)) {
                return index;
            }
        }
        throw new AutomationException(AutomationError.INVALID_PARAMETER, "The table has no output " + key,
                Map.of("outputs", names(table.outputs())));
    }

    private static Optional<String> keyOf(ColumnView column, Map<String, String> values) {
        if (values.containsKey(column.name())) {
            return Optional.of(column.name());
        }
        return values.containsKey(column.elementId()) ? Optional.of(column.elementId()) : Optional.empty();
    }

    private static String cell(String text, ColumnView column) {
        try {
            return ValueText.bits(ValueText.parse(text, column.width()));
        } catch (IllegalArgumentException invalid) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, column.name() + ": "
                    + invalid.getMessage(), Map.of("column", column.name(), "width", column.width()));
        }
    }

    private static List<String> names(List<ColumnView> columns) {
        return columns.stream().map(ColumnView::name).toList();
    }

    // --- problems, layout, signals ---

    private CheckResult problems(DocumentId document) {
        ProblemsView view = measurements.problems(document);
        List<ProblemView> errors = view.problems().stream().filter(problem -> !problem.warning()).toList();
        List<ProblemView> warnings = view.problems().stream().filter(ProblemView::warning).toList();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("version", view.version().value());
        details.put("simulationTime", view.simulationTime());
        details.put("upToDate", view.upToDate());
        details.put("problems", errors.stream().limit(LISTED).map(StageChecks::problem).toList());
        details.put("problemCount", errors.size());
        details.put("warnings", warnings.stream().limit(LISTED).map(StageChecks::problem).toList());
        if (!view.upToDate()) {
            return new CheckResult("problems", Status.NOT_FINISHED, "The simulation has not caught up with the "
                    + "circuit yet; check again", details);
        }
        if (!errors.isEmpty()) {
            return new CheckResult("problems", Status.FAILED, errors.size() + " problems in the circuit", details);
        }
        return new CheckResult("problems", Status.PASSED, warnings.isEmpty() ? "No problems"
                : "No problems (" + warnings.size() + " warnings)", details);
    }

    private static Map<String, Object> problem(ProblemView problem) {
        Map<String, Object> listed = new LinkedHashMap<>();
        listed.put("kind", problem.kind());
        listed.put("message", problem.message());
        listed.put("elementIds", problem.elementIds());
        return listed;
    }

    private CheckResult layout(DocumentId document, Check.Layout check) {
        LayoutView view = measurements.layout(document, check.elementIds());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("version", view.version().value());
        details.put("faultCount", view.faults().size());
        details.put("faults", view.faults().stream().limit(LISTED).map(fault -> {
            Map<String, Object> listed = new LinkedHashMap<>();
            listed.put("kind", fault.kind().name());
            listed.put("elementIds", fault.elementIds());
            listed.put("message", fault.message());
            return listed;
        }).toList());
        if (!view.faults().isEmpty()) {
            return new CheckResult(check.kind(), Status.FAILED, view.faults().size() + " layout faults", details);
        }
        return new CheckResult(check.kind(), Status.PASSED, "No overlapping parts or texts and no wire through a "
                + "part's body", details);
    }

    private CheckResult signals(DocumentId document, Check.Signals check) {
        if (check.expected().isEmpty()) {
            return new CheckResult(check.kind(), Status.NOT_FINISHED, "No expected values were given", Map.of());
        }
        SignalsView view = measurements.signals(document,
                check.expected().stream().map(ExpectedSignal::pin).toList());
        List<Map<String, Object>> mismatches = new ArrayList<>();
        int failed = 0;
        for (int index = 0; index < check.expected().size(); index++) {
            ExpectedSignal expected = check.expected().get(index);
            SignalValue measured = view.signals().get(index);
            String wanted;
            try {
                wanted = ValueText.bits(ValueText.parse(expected.value(), measured.width()));
            } catch (IllegalArgumentException invalid) {
                throw new AutomationException(AutomationError.INVALID_PARAMETER, invalid.getMessage(),
                        Map.of("pin", expected.pin().toString(), "width", measured.width()));
            }
            if (measured.conflict() || !measured.bits().equals(wanted)) {
                failed++;
                if (mismatches.size() < LISTED) {
                    Map<String, Object> mismatch = new LinkedHashMap<>();
                    mismatch.put("elementId", expected.pin().elementId());
                    mismatch.put("pinName", expected.pin().pinName());
                    mismatch.put("index", expected.pin().index());
                    mismatch.put("expected", wanted);
                    mismatch.put("measured", measured.bits());
                    mismatch.put("conflict", measured.conflict());
                    mismatches.add(mismatch);
                }
            }
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("version", view.version().value());
        details.put("time", view.time());
        details.put("running", view.running());
        details.put("failed", failed);
        details.put("mismatches", mismatches);
        if (view.stale()) {
            return new CheckResult(check.kind(), Status.NOT_FINISHED, "The circuit changed while the values were "
                    + "read; read again", details);
        }
        if (failed > 0) {
            return new CheckResult(check.kind(), Status.FAILED, failed + " of " + check.expected().size()
                    + " pins differ from what VSim measured", details);
        }
        return new CheckResult(check.kind(), Status.PASSED, "All " + check.expected().size()
                + " pins have the expected values", details);
    }
}
