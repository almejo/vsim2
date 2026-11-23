package cl.almejo.vsim.core;

import java.util.Optional;

/**
 * Running a list of steps that must all run even when one fails: the listeners of a change, the clean-up of a closing
 * view. The caller decides what to do with the failure.
 */
public final class Steps {

    private Steps() {
    }

    /** Runs every step; the first failure comes back with the later ones suppressed in it, or empty when none failed. */
    public static Optional<RuntimeException> runEach(Iterable<? extends Runnable> steps) {
        RuntimeException failure = null;
        for (Runnable step : steps) {
            try {
                step.run();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        return Optional.ofNullable(failure);
    }
}
