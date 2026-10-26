package cl.almejo.vsim.automation;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Waits, off the editor thread, for a future the editor (or the simulation) completes: at most {@code timeout}, after
 * which the AI is told the outcome is unknown. What the work threw reaches the caller as it is; each caller passes its
 * own texts.
 */
public final class FutureWait {

    private static final String INTERRUPTED = "The request was interrupted";

    private FutureWait() {
    }

    /** The result of {@code future}; on timeout an {@code OUTCOME_UNKNOWN} error saying {@code timeoutMessage}. */
    public static <T> T get(CompletableFuture<T> future, Duration timeout, String timeoutMessage) {
        return get(future, timeout, timeoutMessage, INTERRUPTED);
    }

    /** Like {@link #get(CompletableFuture, Duration, String)} with its own text for an interrupted wait. */
    public static <T> T get(CompletableFuture<T> future, Duration timeout, String timeoutMessage,
                            String interruptedMessage) {
        return await(future, timeout, interruptedMessage, result -> result, () -> {
            throw new AutomationException(AutomationError.OUTCOME_UNKNOWN, timeoutMessage);
        });
    }

    /** The result of {@code future}, or empty when it did not finish within {@code timeout}. */
    public static <T> Optional<T> getIfAnswered(CompletableFuture<T> future, Duration timeout,
                                                String interruptedMessage) {
        return await(future, timeout, interruptedMessage, Optional::of, Optional::empty);
    }

    private static <T, R> R await(CompletableFuture<T> future, Duration timeout, String interruptedMessage,
                                  Function<T, R> onResult, Supplier<R> onTimeout) {
        try {
            return onResult.apply(future.get(timeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException exception) {
            return onTimeout.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AutomationException(AutomationError.OUTCOME_UNKNOWN, interruptedMessage);
        } catch (ExecutionException exception) {
            throw unwrap(exception.getCause());
        }
    }

    /** What the work threw: a runtime exception as it is, an error rethrown, anything else wrapped. */
    public static RuntimeException unwrap(Throwable cause) {
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
