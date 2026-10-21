package cl.almejo.vsim.automation.simulate;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.PinReference;
import cl.almejo.vsim.automation.SimulationHost;
import cl.almejo.vsim.core.truthtable.BudgetedTruthTable;
import cl.almejo.vsim.core.truthtable.Preparation;
import cl.almejo.vsim.core.truthtable.TruthTableEvaluator;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The simulation part of {@link cl.almejo.vsim.automation.AutomationApi} (IA.1 Task 4, A§7): checks the AI's limits,
 * hands the request to the editor ({@link SimulationHost}) and waits for its receipt here, off the editor thread.
 * <ul>
 *   <li>Every run has an event budget: {@value #DEFAULT_EVENT_BUDGET} events by default (a budget of 0), never more
 *   than {@value #MAXIMUM_EVENT_BUDGET}. Running out is not an error here: the receipt says
 *   {@link SimulationReceipt.Outcome#BUDGET_EXCEEDED} with the time reached (the tools answer BUDGET_EXCEEDED).</li>
 *   <li>{@code set_inputs} is sequential in the order given and idempotent by operation id: the same id with the same
 *   values gives the same receipt again without setting anything; the same id with other values is
 *   OPERATION_CONFLICT.</li>
 *   <li>A step while the simulation runs is SIMULATION_BUSY: the AI pauses first (VSim never pauses or resumes on its
 *   own what the user started).</li>
 *   <li>Truth tables are prepared on the editor thread and evaluated here, on a copy.</li>
 * </ul>
 */
public final class SimulationOperations {

    public static final int DEFAULT_EVENT_BUDGET = 100_000;
    public static final int MAXIMUM_EVENT_BUDGET = 1_000_000;
    /** At most this many «Avanzar» or clock edges in one step. */
    public static final long MAXIMUM_STEP_COUNT = 10_000;
    /** At most this much simulation time in one DURATION step. */
    public static final long MAXIMUM_DURATION = 1_000_000_000_000L;
    public static final int MAXIMUM_INPUTS = 64;
    public static final int MAXIMUM_PINS = 256;
    /** Rows of a truth table when the AI does not say (a maximum of 0). */
    public static final int DEFAULT_MAXIMUM_ROWS = 1_024;
    /** The largest table there is: {@link Preparation#MAXIMUM_INPUT_BITS} input bits. */
    public static final int MAXIMUM_ROWS = 1 << Preparation.MAXIMUM_INPUT_BITS;
    /** Operation ids remembered for retries. */
    private static final int REMEMBERED_OPERATIONS = 256;
    private static final Duration SIMULATION_TIMEOUT = Duration.ofSeconds(30);

    private record OperationKey(DocumentId document, String operationId) {
    }

    private record Operation(SimulationRequest.SetInputs request, CompletableFuture<SimulationReceipt> receipt) {
    }

    private final SimulationHost host;
    private final Duration timeout;
    private final Map<OperationKey, Operation> operations = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<OperationKey, Operation> eldest) {
            return size() > REMEMBERED_OPERATIONS;
        }
    };

    public SimulationOperations(SimulationHost host) {
        this(host, SIMULATION_TIMEOUT);
    }

    SimulationOperations(SimulationHost host, Duration timeout) {
        this.host = Objects.requireNonNull(host, "host");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    public SimulationReceipt pause(DocumentId document) {
        return run(new SimulationRequest.Pause(document));
    }

    public SimulationReceipt resume(DocumentId document) {
        return run(new SimulationRequest.Resume(document));
    }

    public SimulationReceipt restart(DocumentId document) {
        return run(new SimulationRequest.Restart(document, DEFAULT_EVENT_BUDGET));
    }

    /**
     * Sets {@code values} in order if the circuit is still at {@code version} (else STALE_DOCUMENT with the current
     * one); an unknown element, one that is not a top-level input or a value that does not fit is INPUT_REJECTED and
     * sets nothing. {@code operationId} may be null (no retry protection).
     */
    public SimulationReceipt setInputs(DocumentId document, DocumentVersion version, String operationId,
                                       List<InputValue> values) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty() || values.size() > MAXIMUM_INPUTS) {
            throw invalid("Give between 1 and " + MAXIMUM_INPUTS + " input values", Map.of("maximum", MAXIMUM_INPUTS));
        }
        SimulationRequest.SetInputs request = new SimulationRequest.SetInputs(document, version, values,
                DEFAULT_EVENT_BUDGET);
        if (operationId == null || operationId.isBlank()) {
            return run(request);
        }
        OperationKey key = new OperationKey(document, operationId);
        Operation operation;
        boolean first = false;
        synchronized (operations) {
            operation = operations.get(key);
            if (operation == null) {
                operation = new Operation(request, new CompletableFuture<>());
                operations.put(key, operation);
                first = true;
            } else if (!operation.request().equals(request)) {
                throw new AutomationException(AutomationError.OPERATION_CONFLICT, "Operation id " + operationId
                        + " was already used with other values; use a new id", Map.of("operationId", operationId));
            }
        }
        if (first) {
            Operation started = operation;
            host.simulate(request).whenComplete((receipt, failure) -> {
                if (failure != null) {
                    synchronized (operations) {
                        operations.remove(key, started); // a refused call may be retried with the same id
                    }
                    started.receipt().completeExceptionally(failure);
                } else {
                    started.receipt().complete(receipt);
                }
            });
        }
        return checked(await(operation.receipt()));
    }

    /** One step of {@code kind}; {@code eventBudget} 0 means {@value #DEFAULT_EVENT_BUDGET}. Paused only. */
    public SimulationReceipt step(DocumentId document, StepKind kind, long amount, int eventBudget) {
        Objects.requireNonNull(kind, "kind");
        long maximum = kind == StepKind.DURATION ? MAXIMUM_DURATION : MAXIMUM_STEP_COUNT;
        if (amount < 1 || amount > maximum) {
            throw invalid("The amount of a " + kind + " step goes from 1 to " + maximum, Map.of("maximum", maximum));
        }
        return run(new SimulationRequest.Step(document, kind, amount, eventBudget(eventBudget)));
    }

    /** The values on {@code pins} at one instant (unknown element INVALID_REFERENCE, unknown pin INVALID_PIN). */
    public SignalsView readSignals(DocumentId document, List<PinReference> pins) {
        Objects.requireNonNull(pins, "pins");
        if (pins.isEmpty() || pins.size() > MAXIMUM_PINS) {
            throw invalid("Ask for between 1 and " + MAXIMUM_PINS + " pins", Map.of("maximum", MAXIMUM_PINS));
        }
        return SignalsView.of(run(new SimulationRequest.ReadSignals(document, pins)));
    }

    /**
     * The truth table of {@code elementIds} (empty: the whole circuit), evaluated on a copy within
     * {@code eventBudget} events (0: {@value #DEFAULT_EVENT_BUDGET}) and {@code maximumRows} rows (0:
     * {@value #DEFAULT_MAXIMUM_ROWS}). A scope with no table (sequential, no inputs or outputs, more than 16 input
     * bits) is NOT_ALLOWED with the reason.
     */
    public TruthTableView truthTable(DocumentId document, Set<String> elementIds, int maximumRows, int eventBudget) {
        Objects.requireNonNull(elementIds, "elementIds");
        if (maximumRows < 0 || maximumRows > MAXIMUM_ROWS) {
            throw invalid("The rows of a table go from 1 to " + MAXIMUM_ROWS, Map.of("maximum", MAXIMUM_ROWS));
        }
        int rows = maximumRows == 0 ? DEFAULT_MAXIMUM_ROWS : maximumRows;
        int budget = eventBudget(eventBudget);
        PreparedTruthTable prepared = await(host.prepareTruthTable(
                new TruthTableRequest(document, elementIds, rows, budget)));
        if (prepared.preparation() instanceof Preparation.Refused refused) {
            throw new AutomationException(AutomationError.NOT_ALLOWED, refusalMessage(refused),
                    Map.of("reason", refused.reason().name(), "inputBits", refused.inputBits()));
        }
        Preparation.Ready ready = (Preparation.Ready) prepared.preparation();
        BudgetedTruthTable evaluated = TruthTableEvaluator.evaluateWithin(ready, rows, budget);
        return TruthTableView.of(document, prepared.version(), evaluated, rows, budget);
    }

    private static String refusalMessage(Preparation.Refused refused) {
        return switch (refused.reason()) {
            case SEQUENTIAL -> "A truth table is only for combinational circuits: this scope has flip-flops, "
                    + "registers, counters or clocks";
            case NO_INPUTS -> "There is no switch or input pin in scope";
            case NO_OUTPUTS -> "There is no LED or output pin in scope";
            case TOO_MANY_INPUT_BITS -> "The scope has " + refused.inputBits() + " input bits; a table takes at most "
                    + Preparation.MAXIMUM_INPUT_BITS;
        };
    }

    private SimulationReceipt run(SimulationRequest request) {
        return checked(await(host.simulate(request)));
    }

    /** Turns the refusals of the simulation thread into the errors the AI reads. */
    private static SimulationReceipt checked(SimulationReceipt receipt) {
        return switch (receipt.outcome()) {
            case REFUSED_WHILE_RUNNING -> throw new AutomationException(AutomationError.SIMULATION_BUSY,
                    "The simulation is running: pause it first, then step", Map.of("running", true,
                    "time", receipt.time()));
            case REFUSED_TIME_OVERFLOW -> throw invalid("That step would pass the largest simulation time",
                    Map.of("time", receipt.time()));
            default -> receipt;
        };
    }

    private static int eventBudget(int eventBudget) {
        if (eventBudget < 0 || eventBudget > MAXIMUM_EVENT_BUDGET) {
            throw invalid("The event budget goes from 1 to " + MAXIMUM_EVENT_BUDGET + " (0: the default "
                    + DEFAULT_EVENT_BUDGET + ")", Map.of("maximum", MAXIMUM_EVENT_BUDGET,
                    "default", DEFAULT_EVENT_BUDGET));
        }
        return eventBudget == 0 ? DEFAULT_EVENT_BUDGET : eventBudget;
    }

    private static AutomationException invalid(String message, Map<String, Object> recovery) {
        return new AutomationException(AutomationError.INVALID_PARAMETER, message, recovery);
    }

    private <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new AutomationException(AutomationError.OUTCOME_UNKNOWN, "The simulation did not answer within "
                    + timeout.toSeconds() + " s; read the signals before retrying");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AutomationException(AutomationError.OUTCOME_UNKNOWN, "The request was interrupted");
        } catch (ExecutionException exception) {
            throw unwrap(exception.getCause());
        }
    }

    private static RuntimeException unwrap(Throwable cause) {
        Throwable actual = cause instanceof CompletionException && cause.getCause() != null ? cause.getCause() : cause;
        if (actual instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (actual instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("The editor failed the request", actual);
    }
}
