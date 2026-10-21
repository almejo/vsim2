package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Waits, off the editor thread, for work handed to it: at most the editor timeout, after which the AI is told the
 * outcome is unknown (the edit may still apply, so it must read before retrying). What the work threw reaches the
 * caller as it is.
 */
final class EditorWait {

    private final Duration timeout;

    EditorWait(Duration timeout) {
        this.timeout = timeout;
    }

    <T> T await(CompletableFuture<T> result) {
        try {
            return result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new AutomationException(AutomationError.OUTCOME_UNKNOWN,
                    "The editor did not answer within " + timeout.toSeconds() + " s; read again before retrying");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AutomationException(AutomationError.OUTCOME_UNKNOWN, "The request was interrupted");
        } catch (ExecutionException exception) {
            throw unwrap(exception.getCause());
        }
    }

    private static RuntimeException unwrap(Throwable cause) {
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("The editor failed the request", cause);
    }
}
