package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.ui.app.DaemonThreads;

import javax.swing.SwingUtilities;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Runs the slow part of building a circuit from a table (minimizing, generating, checking its truth table: about
 * 3 s for an 8×8 import) on {@code worker} and hands the result, or the failure, back on {@code edt}. Tests use
 * {@link #inline()}, which runs everything at once on the caller's thread.
 *
 * @param worker where the work runs
 * @param edt    where its result is delivered
 */
public record Background(Executor worker, Executor edt) {

    /** One daemon thread for the whole application, made the first time it is needed. */
    private static final class SharedWorker {
        private static final ExecutorService INSTANCE = DaemonThreads.single("vsim-table-circuits");
    }

    /** The application's: a background thread, results on the EDT. */
    public static Background application() {
        return new Background(Background::executeOnSharedWorker, SwingUtilities::invokeLater);
    }

    /** Not a method reference to the instance: the worker is made the first time it is needed, not before. */
    private static void executeOnSharedWorker(Runnable command) {
        SharedWorker.INSTANCE.execute(command);
    }

    /** Everything at once on the calling thread (tests). */
    public static Background inline() {
        return new Background(Runnable::run, Runnable::run);
    }

    /**
     * Runs {@code work} on the worker; {@code done} or {@code failed} then runs on the EDT. Anything {@code work}
     * throws, an {@link Error} too (out of memory, a stack overflow), reaches {@code failed}, so the caller always
     * hears back and never stays busy.
     */
    public <T> void run(Callable<T> work, Consumer<T> done, Consumer<Throwable> failed) {
        worker.execute(() -> runWork(work, done, failed));
    }

    /** On the worker: runs {@code work}, then reports its result or its failure on the EDT. */
    private <T> void runWork(Callable<T> work, Consumer<T> done, Consumer<Throwable> failed) {
        T result;
        try {
            result = work.call();
        } catch (Throwable failure) {
            edt.execute(() -> failed.accept(failure));
            return;
        }
        edt.execute(() -> done.accept(result));
    }
}
