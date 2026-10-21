package cl.almejo.vsim.automation.simulate;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.PinReference;
import cl.almejo.vsim.automation.SimulationHost;
import cl.almejo.vsim.core.bundled.BundledExamples;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.simulation.InputOutcome;
import cl.almejo.vsim.core.simulation.RequestReport;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.truthtable.Preparation;
import cl.almejo.vsim.core.truthtable.TruthTableScope;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulationOperationsTest {

    private static final DocumentId DOCUMENT = new DocumentId("circuit-1");
    private static final DocumentVersion VERSION = new DocumentVersion(4);

    /** Answers every request with what {@code answer} gives and remembers the requests. */
    private static final class FakeHost implements SimulationHost {
        final List<SimulationRequest> requests = new ArrayList<>();
        Function<SimulationRequest, CompletableFuture<SimulationReceipt>> answer =
                request -> CompletableFuture.completedFuture(receipt(RequestReport.Status.COMPLETED, List.of()));
        PreparedTruthTable prepared;

        @Override
        public CompletableFuture<SimulationReceipt> simulate(SimulationRequest request) {
            requests.add(request);
            return answer.apply(request);
        }

        @Override
        public CompletableFuture<PreparedTruthTable> prepareTruthTable(TruthTableRequest request) {
            return CompletableFuture.completedFuture(prepared);
        }
    }

    private final FakeHost host = new FakeHost();
    private final SimulationOperations operations = new SimulationOperations(host);

    private static SimulationReceipt receipt(RequestReport.Status status, List<InputValue> asked) {
        SimulationSnapshot snapshot = new SimulationSnapshot(1, 9, 42, false, Map.of("n", Value.ONE), Map.of(),
                List.of(), Optional.empty());
        RequestReport report = new RequestReport(status, snapshot, OptionalLong.of(45), 40, 17,
                asked.stream().map(value -> new InputOutcome(value.elementId(), true, "")).toList());
        return SimulationReceipt.of(DOCUMENT, VERSION, VERSION, 9, 1_000, report, asked, List.of());
    }

    private static void assertError(Runnable call, AutomationError error) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(AutomationException.class,
                exception -> assertThat(exception.error()).isEqualTo(error));
    }

    @Test
    void aBudgetOfZeroIsTheDefaultAndNoBudgetGoesAboveTheMaximum() {
        operations.step(DOCUMENT, StepKind.NEXT_EVENT, 1, 0);
        operations.step(DOCUMENT, StepKind.NEXT_EVENT, 1, 500);

        assertThat(host.requests).extracting(request -> ((SimulationRequest.Step) request).eventBudget())
                .containsExactly(SimulationOperations.DEFAULT_EVENT_BUDGET, 500);
        assertError(() -> operations.step(DOCUMENT, StepKind.NEXT_EVENT, 1, 1_000_001),
                AutomationError.INVALID_PARAMETER);
        assertError(() -> operations.step(DOCUMENT, StepKind.NEXT_EVENT, 1, -1), AutomationError.INVALID_PARAMETER);
        assertThat(host.requests).as("refused before reaching the editor").hasSize(2);
    }

    @Test
    void theAmountOfAStepIsBounded() {
        assertError(() -> operations.step(DOCUMENT, StepKind.DURATION, 0, 0), AutomationError.INVALID_PARAMETER);
        assertError(() -> operations.step(DOCUMENT, StepKind.DURATION, SimulationOperations.MAXIMUM_DURATION + 1, 0),
                AutomationError.INVALID_PARAMETER);
        assertError(() -> operations.step(DOCUMENT, StepKind.NEXT_CLOCK_EDGE, 10_001, 0),
                AutomationError.INVALID_PARAMETER);
        assertThat(host.requests).isEmpty();
    }

    @Test
    void aStepWhileRunningIsBusyAndAnOverflowIsInvalid() {
        host.answer = request -> CompletableFuture.completedFuture(
                receipt(RequestReport.Status.REFUSED_WHILE_RUNNING, List.of()));
        assertError(() -> operations.step(DOCUMENT, StepKind.NEXT_EVENT, 1, 0), AutomationError.SIMULATION_BUSY);

        host.answer = request -> CompletableFuture.completedFuture(
                receipt(RequestReport.Status.REFUSED_TIME_OVERFLOW, List.of()));
        assertError(() -> operations.step(DOCUMENT, StepKind.DURATION, 5, 0), AutomationError.INVALID_PARAMETER);
    }

    @Test
    void anExhaustedBudgetIsAReceiptWithTheTimeReached() {
        host.answer = request -> CompletableFuture.completedFuture(
                receipt(RequestReport.Status.BUDGET_EXCEEDED, List.of()));

        SimulationReceipt receipt = operations.step(DOCUMENT, StepKind.DURATION, 1_000, 0);

        assertThat(receipt.budgetExceeded()).isTrue();
        assertThat(receipt.time()).isEqualTo("42");
        assertThat(receipt.fromTime()).isEqualTo("40");
        assertThat(receipt.nextEventTime()).contains("45");
        assertThat(receipt.eventsProcessed()).isEqualTo(17);
    }

    @Test
    void aRetriedSetInputsWithTheSameIdAndValuesSetsNothingTwice() {
        List<InputValue> values = List.of(new InputValue("a", "1"), new InputValue("b", "0"));
        host.answer = request -> CompletableFuture.completedFuture(receipt(RequestReport.Status.COMPLETED,
                ((SimulationRequest.SetInputs) request).values()));

        SimulationReceipt first = operations.setInputs(DOCUMENT, VERSION, "op-1", values);
        SimulationReceipt again = operations.setInputs(DOCUMENT, VERSION, "op-1", values);

        assertThat(again).isEqualTo(first);
        assertThat(host.requests).hasSize(1);
        assertThat(first.inputs()).extracting(InputResult::elementId).containsExactly("a", "b");
        assertError(() -> operations.setInputs(DOCUMENT, VERSION, "op-1", List.of(new InputValue("a", "0"))),
                AutomationError.OPERATION_CONFLICT);
        assertThat(host.requests).hasSize(1);
    }

    @Test
    void aRefusedSetInputsMayBeRetriedWithTheSameId() {
        List<InputValue> values = List.of(new InputValue("a", "1"));
        host.answer = request -> CompletableFuture.failedFuture(new AutomationException(
                AutomationError.STALE_DOCUMENT, "stale", Map.of("currentVersion", 5)));
        assertError(() -> operations.setInputs(DOCUMENT, VERSION, "op-2", values), AutomationError.STALE_DOCUMENT);

        host.answer = request -> CompletableFuture.completedFuture(receipt(RequestReport.Status.COMPLETED, values));
        SimulationReceipt receipt = operations.setInputs(DOCUMENT, VERSION, "op-2", values);

        assertThat(receipt.inputs()).hasSize(1);
        assertThat(host.requests).hasSize(2);
    }

    @Test
    void setInputsAndReadSignalsAreBounded() {
        assertError(() -> operations.setInputs(DOCUMENT, VERSION, null, List.of()), AutomationError.INVALID_PARAMETER);
        assertError(() -> operations.readSignals(DOCUMENT, List.of()), AutomationError.INVALID_PARAMETER);
        assertThat(host.requests).isEmpty();
    }

    @Test
    void anEditorThatDoesNotAnswerGivesAnUnknownOutcome() {
        host.answer = request -> new CompletableFuture<>();
        SimulationOperations impatient = new SimulationOperations(host, Duration.ofMillis(20));

        assertError(() -> impatient.pause(DOCUMENT), AutomationError.OUTCOME_UNKNOWN);
    }

    @Test
    void aReceiptOfACircuitThatChangedMeanwhileIsLabelledWithItsVersion() {
        SimulationSnapshot snapshot = new SimulationSnapshot(1, 8, 10, true, Map.of(), Map.of(), List.of(),
                Optional.empty());
        RequestReport report = new RequestReport(RequestReport.Status.COMPLETED, snapshot, OptionalLong.empty(), 10,
                0, List.of());

        SimulationReceipt receipt = SimulationReceipt.of(DOCUMENT, VERSION, new DocumentVersion(6), 9, 1, report,
                List.of(), List.of(new SimulationReceipt.ObservedPin(new PinReference("n1", "out", 0), "n")));

        assertThat(receipt.version()).isEqualTo(VERSION);
        assertThat(receipt.stale()).isTrue();
        assertThat(receipt.ranOnThatCircuit()).as("the snapshot is of token 8, the request was for 9").isFalse();
        assertThat(receipt.running()).isTrue();
        assertThat(receipt.signals()).extracting(SignalValue::bits).containsExactly("Z");
    }

    @Test
    void theFullAdderTableHasItsEightRows() throws Exception {
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        CircuitDocument adder = BundledExamples.open("full-adder", registry);
        host.prepared = new PreparedTruthTable(VERSION, TruthTableScope.prepare(adder, Set.of(), registry));

        TruthTableView table = operations.truthTable(DOCUMENT, Set.of(), 0, 0);

        assertThat(table.totalRows()).isEqualTo(8);
        assertThat(table.rows()).hasSize(8);
        assertThat(table.truncated()).isFalse();
        assertThat(table.budgetExceeded()).isFalse();
        assertThat(table.version()).isEqualTo(VERSION);
        assertThat(table.inputs()).hasSize(3);
        for (int index = 0; index < 8; index++) {
            int ones = Integer.bitCount(index);
            TruthTableView.RowView row = table.rows().get(index);
            assertThat(row.inputs()).containsExactly(Integer.toString(index >> 2 & 1),
                    Integer.toString(index >> 1 & 1), Integer.toString(index & 1));
            assertThat(row.outputs()).as("row %d: sum and carry, in the table's column order", index)
                    .containsExactlyInAnyOrder(Integer.toString(ones % 2), Integer.toString(ones / 2));
            assertThat(row.settled()).isTrue();
        }
    }

    @Test
    void aSequentialScopeHasNoTable() {
        host.prepared = new PreparedTruthTable(VERSION, new Preparation.Refused(Preparation.Reason.SEQUENTIAL, 1));

        assertThatThrownBy(() -> operations.truthTable(DOCUMENT, Set.of(), 0, 0))
                .isInstanceOfSatisfying(AutomationException.class, exception -> {
                    assertThat(exception.error()).isEqualTo(AutomationError.NOT_ALLOWED);
                    assertThat(exception.recovery()).containsEntry("reason", "SEQUENTIAL");
                });
    }

    @Test
    void theRowsOfATableAreBounded() {
        assertError(() -> operations.truthTable(DOCUMENT, Set.of(), SimulationOperations.MAXIMUM_ROWS + 1, 0),
                AutomationError.INVALID_PARAMETER);
    }
}
