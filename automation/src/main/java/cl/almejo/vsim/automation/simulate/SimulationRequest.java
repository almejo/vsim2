package cl.almejo.vsim.automation.simulate;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.PinReference;

import java.util.List;
import java.util.Objects;

/**
 * One simulation request of the AI, already checked by {@link SimulationOperations} (budgets in range, sizes
 * bounded), for the editor to run ({@link cl.almejo.vsim.automation.SimulationHost#simulate}). Every request runs on
 * the document's simulation thread with an event budget where it runs events, and is answered with one
 * {@link SimulationReceipt}.
 */
public sealed interface SimulationRequest {

    DocumentId document();

    /** Pauses, as «Pausar» does; the receipt is the state once paused. */
    record Pause(DocumentId document) implements SimulationRequest {
        public Pause {
            Objects.requireNonNull(document, "document");
        }
    }

    /** Runs the simulation, as «Iniciar» does (it also leaves «Paso a paso»). */
    record Resume(DocumentId document) implements SimulationRequest {
        public Resume {
            Objects.requireNonNull(document, "document");
        }
    }

    /** «Reiniciar» within {@code eventBudget} events: time 0, switches where they are, running or paused as it was. */
    record Restart(DocumentId document, int eventBudget) implements SimulationRequest {
        public Restart {
            Objects.requireNonNull(document, "document");
        }
    }

    /**
     * Sets top-level inputs one after another in the order given (sequential: each with its zero-delay effects before
     * the next), within {@code eventBudget} events, if the circuit is still at {@code version}.
     */
    record SetInputs(DocumentId document, DocumentVersion version, List<InputValue> values, int eventBudget)
            implements SimulationRequest {
        public SetInputs {
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(version, "version");
            values = List.copyOf(values);
        }
    }

    /** One step of {@code kind} (see {@link StepKind} for {@code amount}) within {@code eventBudget}; paused only. */
    record Step(DocumentId document, StepKind kind, long amount, int eventBudget) implements SimulationRequest {
        public Step {
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(kind, "kind");
        }
    }

    /** Reads the values on {@code pins} once every earlier request ran; nothing runs. */
    record ReadSignals(DocumentId document, List<PinReference> pins) implements SimulationRequest {
        public ReadSignals {
            Objects.requireNonNull(document, "document");
            pins = List.copyOf(pins);
        }
    }
}
