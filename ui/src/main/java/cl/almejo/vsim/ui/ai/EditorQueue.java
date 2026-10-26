package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The AI's work waiting for the EDT (review M1). Once the AI is turned off, every job still queued is refused at once
 * — the server's threads waiting for them return, so stopping the server never waits for the EDT — and the jobs skip
 * their work when they finally run: no file is written, no window opens, no edit lands after «Apagar». Any thread.
 */
final class EditorQueue {

    private final Set<CompletableFuture<?>> waiting = ConcurrentHashMap.newKeySet();
    private volatile boolean refusing;

    /** Registers {@code result}, the future of a job about to be queued; refused at once while the AI is off. */
    <T> CompletableFuture<T> track(CompletableFuture<T> result) {
        if (refusing) {
            result.completeExceptionally(turnedOff());
            return result;
        }
        waiting.add(result);
        result.whenComplete((value, failure) -> waiting.remove(result));
        if (refusing) {
            result.completeExceptionally(turnedOff());
        }
        return result;
    }

    /** True when the job of {@code result} must not run: it was refused (or answered) before its turn came. */
    static boolean skips(CompletableFuture<?> result) {
        return result.isDone();
    }

    /**
     * Queues {@code work} on {@code executor}: tracked (refused at once while the AI is off), skipped if it was
     * refused before its turn, and its failure completes the future exceptionally.
     */
    <T> CompletableFuture<T> submit(Executor executor, Supplier<T> work) {
        return submitAsync(executor, result -> result.complete(work.get()));
    }

    /** Like {@link #submit}, for a {@code body} that completes the future itself, possibly later. */
    <T> CompletableFuture<T> submitAsync(Executor executor, Consumer<CompletableFuture<T>> body) {
        CompletableFuture<T> result = track(new CompletableFuture<>());
        executor.execute(job(result, body));
        return result;
    }

    /** The job of {@code result}: skips when already done; a failure of {@code body} completes it exceptionally. */
    static <T> Runnable job(CompletableFuture<T> result, Consumer<CompletableFuture<T>> body) {
        return () -> {
            if (skips(result)) {
                return;
            }
            try {
                body.accept(result);
            } catch (RuntimeException | Error failure) {
                result.completeExceptionally(failure);
            }
        };
    }

    /** The AI was turned off: refuses every job still waiting and the next ones. */
    void refuse() {
        refusing = true;
        for (CompletableFuture<?> result : Set.copyOf(waiting)) {
            result.completeExceptionally(turnedOff());
        }
    }

    /** The AI was turned on again: new jobs run. */
    void accept() {
        refusing = false;
    }

    private static AutomationException turnedOff() {
        return new AutomationException(AutomationError.NOT_ALLOWED, "The user turned the AI off in VSim (or VSim "
                + "is closing); nothing was done");
    }
}
