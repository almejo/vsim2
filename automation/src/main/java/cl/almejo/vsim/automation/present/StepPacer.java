package cl.almejo.vsim.automation.present;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The clock and the timer «De corrido» paces the steps with (spec §5): never the editor thread. Tests give a fake one
 * they advance by hand, so no test sleeps.
 */
public interface StepPacer {

    /** Now, to measure the time since the last applied step. */
    Instant now();

    /** Completes after {@code delay}, off the editor thread. */
    CompletableFuture<Void> after(Duration delay);

    /**
     * Completes after {@code delay}, off the editor thread, for the watch over a gone client (IA.3), which is not a
     * pause between steps; {@link #after} by default.
     */
    default CompletableFuture<Void> silenceCheckAfter(Duration delay) {
        return after(delay);
    }

    /** The system clock and the JDK's delayed executor (its own daemon thread, then the common pool). */
    static StepPacer system() {
        return of(Clock.systemUTC());
    }

    static StepPacer of(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return new StepPacer() {
            @Override
            public Instant now() {
                return clock.instant();
            }

            @Override
            public CompletableFuture<Void> after(Duration delay) {
                if (delay.isZero() || delay.isNegative()) {
                    return CompletableFuture.completedFuture(null);
                }
                return CompletableFuture.runAsync(() -> {
                }, CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS));
            }
        };
    }
}
