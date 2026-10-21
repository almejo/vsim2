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
import cl.almejo.vsim.automation.check.StageCheck.Status;
import cl.almejo.vsim.automation.read.ProblemView;
import cl.almejo.vsim.automation.read.ProblemsView;
import cl.almejo.vsim.automation.simulate.SignalValue;
import cl.almejo.vsim.automation.simulate.SignalsView;
import cl.almejo.vsim.automation.simulate.TruthTableView;
import cl.almejo.vsim.automation.simulate.TruthTableView.ColumnView;
import cl.almejo.vsim.automation.simulate.TruthTableView.RowView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StageChecksTest {

    private static final DocumentId DOCUMENT = new DocumentId("circuit-1");
    private static final DocumentVersion VERSION = new DocumentVersion(4);
    private static final List<ColumnView> AND_INPUTS = List.of(new ColumnView("A", "c1", 1),
            new ColumnView("B", "c2", 1));
    private static final List<ColumnView> AND_OUTPUTS = List.of(new ColumnView("Y", "c4", 1));

    private final FakeMeasurements measurements = new FakeMeasurements();
    private final StageChecks checks = new StageChecks(measurements);

    @Test
    void aTableThatMatchesEveryExpectedRowPasses() {
        measurements.table = andTable(false, 4, false);

        CheckResult result = runOne(truthTable(andRows()));

        assertThat(result.status()).isEqualTo(Status.PASSED);
        assertThat(result.details()).containsEntry("failedRows", 0).containsEntry("evaluatedRows", 4);
    }

    @Test
    void aRowThatDiffersFailsAndSaysWhatVsimMeasured() {
        measurements.table = andTable(false, 4, false);
        List<ExpectedRow> wrong = new ArrayList<>(andRows());
        wrong.set(3, row("1", "1", "0"));

        CheckResult result = runOne(truthTable(wrong));

        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(result.details()).containsEntry("failedRows", 1);
        assertThat(result.details().get("mismatches").toString()).contains("measured={Y=1}");
    }

    @Test
    void aTableCutByTheBudgetNeverPassesEvenWhenTheRowsItHasMatch() {
        measurements.table = andTable(true, 2, false);

        CheckResult result = runOne(truthTable(andRows()));

        assertThat(result.status()).isEqualTo(Status.NOT_FINISHED);
        assertThat(result.details()).containsEntry("rowsNotEvaluated", 2);
        assertThat(result.summary()).as("says how to go on").contains("100000 events", "after 2 rows",
                "larger eventBudget (at most 1000000)", "signals");
    }

    @Test
    void aTableCutByTheRowLimitSaysWhichLimitToRaise() {
        measurements.table = new TruthTableView(DOCUMENT, VERSION, AND_INPUTS, AND_OUTPUTS, andTable(false, 2, false)
                .rows(), 4, true, false, 20, 100_000, false);

        CheckResult result = runOne(truthTable(andRows()));

        assertThat(result.status()).isEqualTo(Status.NOT_FINISHED);
        assertThat(result.summary()).contains("row limit", "maximumRows");
    }

    @Test
    void aRowThatNeverSettledFails() {
        measurements.table = andTable(false, 4, true);

        assertThat(runOne(truthTable(andRows())).status()).isEqualTo(Status.FAILED);
    }

    @Test
    void aTableVsimRefusesToMakeIsNotFinished() {
        measurements.refusal = new AutomationException(AutomationError.NOT_ALLOWED, "sequential",
                Map.of("reason", "SEQUENTIAL"));

        CheckResult result = runOne(truthTable(andRows()));

        assertThat(result.status()).isEqualTo(Status.NOT_FINISHED);
        assertThat(result.details()).containsEntry("error", "NOT_ALLOWED");
    }

    @Test
    void anExpectedRowWithAnUnknownColumnOrNoRowsIsNotFinished() {
        measurements.table = andTable(false, 4, false);

        assertThat(runOne(truthTable(List.of(new ExpectedRow(Map.of("A", "0", "C", "1"), Map.of("Y", "0")))))
                .status()).isEqualTo(Status.NOT_FINISHED);
        assertThat(runOne(truthTable(List.of())).status()).isEqualTo(Status.NOT_FINISHED);
    }

    @Test
    void problemsPassOnlyWhenTheSimulationIsUpToDateAndHasNone() {
        measurements.problems = new ProblemsView(DOCUMENT, VERSION, "12", true, List.of());
        assertThat(runOne(new Check.Problems()).status()).isEqualTo(Status.PASSED);

        measurements.problems = new ProblemsView(DOCUMENT, VERSION, "12", false, List.of());
        assertThat(runOne(new Check.Problems()).status()).isEqualTo(Status.NOT_FINISHED);

        measurements.problems = new ProblemsView(DOCUMENT, VERSION, "12", true, List.of(new ProblemView(
                "SHORT_CIRCUIT", false, "n1", "two drivers", List.of("c1", "c2"), List.of(), List.of())));
        assertThat(runOne(new Check.Problems()).status()).isEqualTo(Status.FAILED);
    }

    @Test
    void layoutFaultsFailTheLayoutCheck() {
        measurements.faults = List.of(new LayoutCheck.Fault(LayoutCheck.FaultKind.PART_OVERLAP, List.of("c1", "c2"),
                "c1 overlaps c2"));
        assertThat(runOne(new Check.Layout(Set.of())).status()).isEqualTo(Status.FAILED);

        measurements.faults = List.of();
        assertThat(runOne(new Check.Layout(Set.of())).status()).isEqualTo(Status.PASSED);
    }

    @Test
    void signalsAreComparedBitByBitAndAStaleReadIsNotFinished() {
        PinReference carry = new PinReference("c7", "Cout", 0);
        measurements.signals = List.of(new SignalValue(carry, 1, "1", "1", "0", false));
        assertThat(runOne(new Check.Signals(List.of(new ExpectedSignal(carry, "1")))).status())
                .isEqualTo(Status.PASSED);
        assertThat(runOne(new Check.Signals(List.of(new ExpectedSignal(carry, "0b0")))).status())
                .isEqualTo(Status.FAILED);

        measurements.staleSignals = true;
        assertThat(runOne(new Check.Signals(List.of(new ExpectedSignal(carry, "1")))).status())
                .isEqualTo(Status.NOT_FINISHED);
    }

    @Test
    void theStagePassesOnlyWhenEveryCheckPassed() {
        measurements.table = andTable(false, 4, false);
        measurements.problems = new ProblemsView(DOCUMENT, VERSION, "0", false, List.of());

        StageCheck.Result result = checks.run(new StageCheck.Request(DOCUMENT, Optional.of("task-1"),
                Optional.of("Latch"), List.of(truthTable(andRows()), new Check.Problems())));

        assertThat(result.checks()).extracting(CheckResult::status)
                .containsExactly(Status.PASSED, Status.NOT_FINISHED);
        assertThat(result.passed()).isFalse();
        assertThat(result.taskId()).contains("task-1");
        assertThat(result.version()).isEqualTo(VERSION);
    }

    @Test
    void aClosedCircuitFailsTheWholeCall() {
        measurements.closed = true;

        assertThatThrownBy(() -> checks.run(new StageCheck.Request(DOCUMENT, Optional.empty(), Optional.empty(),
                List.of(new Check.Layout(Set.of())))))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.DOCUMENT_CLOSED));
    }

    private CheckResult runOne(Check check) {
        return checks.run(new StageCheck.Request(DOCUMENT, Optional.empty(), Optional.empty(), List.of(check)))
                .checks().getFirst();
    }

    private static Check.TruthTable truthTable(List<ExpectedRow> rows) {
        return new Check.TruthTable(Set.of(), rows, 0, 0);
    }

    private static ExpectedRow row(String a, String b, String y) {
        return new ExpectedRow(Map.of("A", a, "B", b), Map.of("Y", y));
    }

    private static List<ExpectedRow> andRows() {
        return List.of(row("0", "0", "0"), row("0", "1", "0"), row("1", "0", "0"), row("0b1", "1", "1"));
    }

    /** An AND gate's table with its first {@code rows} rows; the last row unsettled when asked. */
    private static TruthTableView andTable(boolean budgetExceeded, int rows, boolean lastUnsettled) {
        List<RowView> all = List.of(new RowView(List.of("0", "0"), List.of("0"), true),
                new RowView(List.of("0", "1"), List.of("0"), true),
                new RowView(List.of("1", "0"), List.of("0"), true),
                new RowView(List.of("1", "1"), List.of("1"), !lastUnsettled));
        return new TruthTableView(DOCUMENT, VERSION, AND_INPUTS, AND_OUTPUTS, all.subList(0, rows), 4, false,
                budgetExceeded, 40, 100_000, false);
    }

    private static final class FakeMeasurements implements StageChecks.Measurements {
        TruthTableView table;
        AutomationException refusal;
        ProblemsView problems = new ProblemsView(DOCUMENT, VERSION, "0", true, List.of());
        List<LayoutCheck.Fault> faults = List.of();
        List<SignalValue> signals = List.of();
        boolean staleSignals;
        boolean closed;

        @Override
        public ProblemsView problems(DocumentId document) {
            if (closed) {
                throw new AutomationException(AutomationError.DOCUMENT_CLOSED, "closed");
            }
            return problems;
        }

        @Override
        public StageCheck.LayoutView layout(DocumentId document, Set<String> elementIds) {
            return new StageCheck.LayoutView(document, VERSION, faults);
        }

        @Override
        public TruthTableView truthTable(DocumentId document, Set<String> elementIds, int maximumRows,
                                         int eventBudget) {
            if (refusal != null) {
                throw refusal;
            }
            return table;
        }

        @Override
        public SignalsView signals(DocumentId document, List<PinReference> pins) {
            return new SignalsView(document, VERSION, "10", false, staleSignals, signals);
        }
    }
}
