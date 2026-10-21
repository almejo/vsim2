package cl.almejo.vsim.automation;

import cl.almejo.vsim.automation.simulate.PreparedTruthTable;
import cl.almejo.vsim.automation.simulate.SimulationReceipt;
import cl.almejo.vsim.automation.simulate.SimulationRequest;
import cl.almejo.vsim.automation.simulate.TruthTableRequest;

import java.util.concurrent.CompletableFuture;

/**
 * The editor-side services for driving the simulation with budgets and receipts (IA.1 Task 4). Both are called off
 * the editor thread and complete off it; the editor never waits for the simulation. The defaults refuse, for hosts
 * without a simulation (tests).
 */
public interface SimulationHost {

    /**
     * Runs {@code request} on its document's simulation thread and completes with its receipt; a refusal (closed
     * circuit, stale version, unknown element or pin, rejected input) fails the future with an
     * {@link AutomationException}.
     */
    default CompletableFuture<SimulationReceipt> simulate(SimulationRequest request) {
        return CompletableFuture.failedFuture(new AutomationException(AutomationError.NOT_ALLOWED,
                "This editor does not simulate"));
    }

    /** Takes the scope of a truth table as an immutable copy on the editor thread (the rows are evaluated after). */
    default CompletableFuture<PreparedTruthTable> prepareTruthTable(TruthTableRequest request) {
        return CompletableFuture.failedFuture(new AutomationException(AutomationError.NOT_ALLOWED,
                "This editor does not make truth tables"));
    }
}
