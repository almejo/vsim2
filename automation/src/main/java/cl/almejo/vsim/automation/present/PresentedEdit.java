package cl.almejo.vsim.automation.present;

import cl.almejo.vsim.automation.edit.EditReceipt;

import java.util.Objects;
import java.util.Optional;

/**
 * An edit call's answer with how «Presentar» took it (spec §5.1): {@code status} is empty when the call was not paced
 * (working mode, validate only, no step, only layout guides), {@link StepStatus#APPLIED} when it is in the circuit, and
 * {@link StepStatus#PROPOSED} when it waits for «Siguiente» as a ghost: then {@code receipt.applied()} is false and its
 * aliases already name the ids the step will have.
 */
public record PresentedEdit(EditReceipt receipt, Optional<StepStatus> status) {

    public PresentedEdit {
        Objects.requireNonNull(receipt, "receipt");
        Objects.requireNonNull(status, "status");
    }

    /** True when the step waits for «Siguiente» (call {@code await_step}). */
    public boolean proposed() {
        return status.filter(StepStatus.PROPOSED::equals).isPresent();
    }
}
