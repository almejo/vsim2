package cl.almejo.vsim.ui.karnaugh;

import javax.swing.SwingUtilities;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
        private static final ExecutorService INSTANCE = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "vsim-table-circuits");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** The application's: a background thread, results on the EDT. */
    public static Background application() {
        return new Background(command -> SharedWorker.INSTANCE.execute(command), SwingUtilities::invokeLater);
    }

    /** Everything at once on the calling thread (tests). */
    public static Background inline() {
        return new Background(Runnable::run, Runnable::run);
    }

    /** Runs {@code work} on the worker; {@code done} or {@code failed} then runs on the EDT. */
    public <T> void run(Callable<T> work, Consumer<T> done, Consumer<Exception> failed) {
        worker.execute(() -> {
            T result;
            try {
                result = work.call();
            } catch (Exception exception) {
                edt.execute(() -> failed.accept(exception));
                return;
            }
            edt.execute(() -> done.accept(result));
        });
    }
}
