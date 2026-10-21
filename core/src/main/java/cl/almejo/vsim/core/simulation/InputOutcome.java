package cl.almejo.vsim.core.simulation;

import java.util.Objects;

/**
 * Whether the simulation thread applied one {@link InputAssignment}: {@code reason} (English, for the AI) says why
 * not, or, for an accepted one, that the event budget cut its effects short; empty when it was simply accepted.
 */
public record InputOutcome(String componentId, boolean accepted, String reason) {

    public InputOutcome {
        Objects.requireNonNull(componentId, "componentId");
        Objects.requireNonNull(reason, "reason");
    }

    static InputOutcome accepted(String componentId) {
        return new InputOutcome(componentId, true, "");
    }

    static InputOutcome rejected(String componentId, String reason) {
        return new InputOutcome(componentId, false, reason);
    }
}
