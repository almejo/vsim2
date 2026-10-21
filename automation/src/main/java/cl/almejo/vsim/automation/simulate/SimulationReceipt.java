package cl.almejo.vsim.automation.simulate;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.PinReference;
import cl.almejo.vsim.core.simulation.InputOutcome;
import cl.almejo.vsim.core.simulation.RequestReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The receipt of one simulation request (A§7): what it ran against and what it reached, all from one immutable report
 * of the simulation thread.
 * <ul>
 *   <li>{@code version} — the circuit version the request ran against; {@code currentVersion} — the version when the
 *   receipt was written. They differ when the user (or another call) changed the circuit meanwhile: the receipt then
 *   describes version {@code version}, not the circuit as it is now ({@link #stale()}).</li>
 *   <li>{@code netlistToken} — the compiled circuit the editor sent the request to; {@code ranOnThatCircuit} is false
 *   when the simulation was on another one (the circuit could not be prepared).</li>
 *   <li>{@code fromTime}, {@code time}, {@code nextEventTime} — simulation times as decimal strings.</li>
 *   <li>{@code eventsProcessed} of at most {@code eventBudget}; {@code outcome} {@link Outcome#BUDGET_EXCEEDED} when
 *   the budget ran out: the simulation stopped at {@code time} and paused, nothing rolled back.</li>
 *   <li>{@code running}, {@code failure} — the simulation's state right after the request.</li>
 *   <li>{@code inputs} — for {@code set_inputs}, each value in order; {@code signals} — for {@code read_signals}.</li>
 * </ul>
 */
public record SimulationReceipt(DocumentId document, DocumentVersion version, DocumentVersion currentVersion,
                                long netlistToken, boolean ranOnThatCircuit, Outcome outcome, String fromTime,
                                String time, Optional<String> nextEventTime, long eventsProcessed, int eventBudget,
                                boolean running, Optional<String> failure, List<InputResult> inputs,
                                List<SignalValue> signals) {

    public enum Outcome {
        COMPLETED,
        /** The event budget ran out: stopped at the time reached and paused. */
        BUDGET_EXCEEDED,
        /** A step while running: nothing ran ({@link SimulationOperations} turns it into SIMULATION_BUSY). */
        REFUSED_WHILE_RUNNING,
        /** A duration past the largest time: nothing ran ({@link SimulationOperations}: INVALID_PARAMETER). */
        REFUSED_TIME_OVERFLOW,
        /** The engine failed: paused, with {@code failure}. */
        FAILED
    }

    /** A pin read by {@code read_signals} and the net (top-level or private) the editor found for it. */
    public record ObservedPin(PinReference pin, String netId) {
        public ObservedPin {
            Objects.requireNonNull(pin, "pin");
            Objects.requireNonNull(netId, "netId");
        }
    }

    public SimulationReceipt {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(currentVersion, "currentVersion");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(fromTime, "fromTime");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(nextEventTime, "nextEventTime");
        Objects.requireNonNull(failure, "failure");
        inputs = List.copyOf(inputs);
        signals = List.copyOf(signals);
    }

    /**
     * The receipt of {@code report}: {@code asked} are the values of a {@code set_inputs} in the order sent (matched
     * one to one with the report's outcomes), {@code observed} the pins of a {@code read_signals}.
     */
    public static SimulationReceipt of(DocumentId document, DocumentVersion version, DocumentVersion currentVersion,
                                       long netlistToken, int eventBudget, RequestReport report,
                                       List<InputValue> asked, List<ObservedPin> observed) {
        List<InputResult> inputs = new ArrayList<>();
        List<InputOutcome> outcomes = report.inputs();
        for (int index = 0; index < asked.size(); index++) {
            InputValue value = asked.get(index);
            inputs.add(index < outcomes.size()
                    ? new InputResult(value.elementId(), value.value(), outcomes.get(index).accepted(),
                    outcomes.get(index).reason())
                    : new InputResult(value.elementId(), value.value(), false, "not set"));
        }
        List<SignalValue> signals = observed.stream()
                .map(pin -> SignalValue.of(pin.pin(), report.snapshot().netValue(pin.netId())))
                .toList();
        return new SimulationReceipt(document, version, currentVersion, netlistToken,
                report.snapshot().token() == netlistToken, outcomeOf(report.status()),
                Long.toString(report.fromTime()), Long.toString(report.snapshot().time()),
                report.nextEventTime().isPresent()
                        ? Optional.of(Long.toString(report.nextEventTime().getAsLong())) : Optional.empty(),
                report.eventsProcessed(), eventBudget, report.snapshot().running(), report.snapshot().failure(),
                inputs, signals);
    }

    private static Outcome outcomeOf(RequestReport.Status status) {
        return switch (status) {
            case COMPLETED -> Outcome.COMPLETED;
            case BUDGET_EXCEEDED -> Outcome.BUDGET_EXCEEDED;
            case REFUSED_WHILE_RUNNING -> Outcome.REFUSED_WHILE_RUNNING;
            case REFUSED_TIME_OVERFLOW -> Outcome.REFUSED_TIME_OVERFLOW;
            case FAILED -> Outcome.FAILED;
        };
    }

    /** True when the event budget ran out (the tools answer it as BUDGET_EXCEEDED, with this receipt). */
    public boolean budgetExceeded() {
        return outcome == Outcome.BUDGET_EXCEEDED;
    }

    /** True when the circuit changed after the request ran: this receipt describes {@link #version()}. */
    public boolean stale() {
        return !version.equals(currentVersion);
    }
}
