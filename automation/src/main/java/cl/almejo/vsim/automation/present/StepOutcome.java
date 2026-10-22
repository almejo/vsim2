package cl.almejo.vsim.automation.present;

import cl.almejo.vsim.automation.edit.EditReceipt;

import java.util.Objects;
import java.util.Optional;

/**
 * What {@code await_step} answers (spec §5.1): the step was applied (with its receipt), it still waits for
 * «Siguiente», or it was dropped because the circuit changed ({@code STALE}) or the user stopped it ({@code STOPPED}).
 */
public record StepOutcome(StepStatus status, Optional<EditReceipt> receipt) {

    public StepOutcome {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(receipt, "receipt");
    }

    public static StepOutcome applied(EditReceipt receipt) {
        return new StepOutcome(StepStatus.APPLIED, Optional.of(receipt));
    }

    public static StepOutcome of(StepStatus status) {
        return new StepOutcome(status, Optional.empty());
    }
}
