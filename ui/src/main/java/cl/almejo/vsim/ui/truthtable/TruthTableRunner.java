package cl.almejo.vsim.ui.truthtable;

import cl.almejo.vsim.core.truthtable.Preparation;
import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.core.truthtable.TruthTableEvaluator;

import javax.swing.SwingUtilities;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Evaluates truth tables away from the EDT, one at a time per view (spec §5.2): {@link #start} cancels the running
 * computation, then evaluates on the worker executor and delivers progress and the result through the EDT executor.
 * Call every method on the EDT. Nothing is delivered for a computation that was cancelled or after {@link #dispose}.
 */
public final class TruthTableRunner {

    /** What a computation reports, always on the EDT. */
    public interface Callbacks {
        /** Percent done; grows to 100. */
        void progress(int percent);

        void finished(TruthTable table);

        /** The computation was stopped by {@link #cancel} or replaced by a newer {@link #start}. */
        void cancelled();

        /** The evaluation threw (the circuit could not be simulated). */
        void failed(RuntimeException exception);
    }

    private static final class Job {
        final Callbacks callbacks;
        volatile boolean cancelled;

        Job(Callbacks callbacks) {
            this.callbacks = callbacks;
        }
    }

    private final Executor worker;
    private final Executor edt;
    private final Runnable shutdown;
    private Job current;
    private boolean disposed;

    /** Tests pass direct or manual executors; {@code edt} must run its tasks on the EDT in the application. */
    public TruthTableRunner(Executor worker, Executor edt) {
        this(worker, edt, () -> {
        });
    }

    private TruthTableRunner(Executor worker, Executor edt, Runnable shutdown) {
        this.worker = worker;
        this.edt = edt;
        this.shutdown = shutdown;
    }

    /** A runner with its own daemon thread, delivering with {@link SwingUtilities#invokeLater}. */
    public static TruthTableRunner create() {
        ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "vsim-truth-table");
            thread.setDaemon(true);
            return thread;
        });
        return new TruthTableRunner(executor, SwingUtilities::invokeLater, executor::shutdownNow);
    }

    public void start(Preparation.Ready ready, Callbacks callbacks) {
        if (disposed) {
            throw new IllegalStateException("the truth-table runner was disposed");
        }
        cancel();
        Job job = new Job(callbacks);
        current = job;
        worker.execute(() -> evaluate(job, ready));
    }

    private void evaluate(Job job, Preparation.Ready ready) {
        Optional<TruthTable> table;
        try {
            table = TruthTableEvaluator.evaluate(ready,
                    percent -> edt.execute(() -> {
                        if (isCurrent(job)) {
                            job.callbacks.progress(percent);
                        }
                    }),
                    () -> job.cancelled);
        } catch (RuntimeException exception) {
            edt.execute(() -> {
                if (finish(job)) {
                    job.callbacks.failed(exception);
                }
            });
            return;
        }
        edt.execute(() -> {
            if (finish(job)) {
                table.ifPresent(job.callbacks::finished);
            }
        });
    }

    private boolean isCurrent(Job job) {
        return current == job && !job.cancelled;
    }

    private boolean finish(Job job) {
        if (!isCurrent(job)) {
            return false;
        }
        current = null;
        return true;
    }

    /** Stops the running computation, if any, and tells its callbacks. */
    public void cancel() {
        Job job = current;
        if (job != null) {
            job.cancelled = true;
            current = null;
            job.callbacks.cancelled();
        }
    }

    public boolean isRunning() {
        return current != null;
    }

    /** Stops the running computation without telling anyone and releases the worker thread. */
    public void dispose() {
        disposed = true;
        Job job = current;
        if (job != null) {
            job.cancelled = true;
            current = null;
        }
        shutdown.run();
    }
}
