package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.FutureWait;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

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
        return FutureWait.get(result, timeout,
                "The editor did not answer within " + timeout.toSeconds() + " s; read again before retrying");
    }
}
