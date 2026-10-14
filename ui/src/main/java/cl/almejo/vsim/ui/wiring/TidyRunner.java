package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.wiring.TidyOptions;
import cl.almejo.vsim.core.wiring.TidyPlan;
import cl.almejo.vsim.core.wiring.WireTidy;

import javax.swing.SwingUtilities;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Computes tidy plans away from the EDT. {@link #start} takes a snapshot the worker may read freely (the caller
 * copies it on the EDT) and delivers progress and the result through the EDT executor; a cancelled job, or any job
 * after {@link #dispose}, delivers nothing. Call every method on the EDT.
 */
public final class TidyRunner {

    /** The planning itself; {@link WireTidy#plan} in the application, a blocking fake in tests. */
    @FunctionalInterface
    public interface Planner {
        Optional<TidyPlan> plan(CircuitDocument snapshot, ComponentTypeRegistry registry, Set<ElementId> scope,
                                TidyOptions options, BooleanSupplier cancelled, IntConsumer progress);
    }

    /** What a job reports, always on the EDT. */
    public interface Callbacks {
        /** The number of the round that just started. */
        void progress(int round);

        void finished(TidyPlan plan);

        void failed(Throwable throwable);
    }

    /** A running computation. */
    public final class Job {
        private final Callbacks callbacks;
        private volatile boolean cancelled;
        private boolean done;

        private Job(Callbacks callbacks) {
            this.callbacks = callbacks;
        }

        /** Stops the computation; nothing more is delivered. */
        public void cancel() {
            cancelled = true;
        }

        public boolean isRunning() {
            return !cancelled && !done;
        }
    }

    private final Executor worker;
    private final Executor edt;
    private final Planner planner;
    private final Runnable shutdown;
    private final java.util.List<Job> jobs = new java.util.ArrayList<>();
    private boolean disposed;

    /** Tests pass manual executors; {@code edt} must run its tasks on the EDT in the application. */
    public TidyRunner(Executor worker, Executor edt, Planner planner) {
        this(worker, edt, planner, () -> {
        });
    }

    private TidyRunner(Executor worker, Executor edt, Planner planner, Runnable shutdown) {
        this.worker = worker;
        this.edt = edt;
        this.planner = planner;
        this.shutdown = shutdown;
    }

    /** A runner with daemon threads, delivering with {@link SwingUtilities#invokeLater}. */
    public static TidyRunner create() {
        ExecutorService executor = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "vsim-tidy");
            thread.setDaemon(true);
            return thread;
        });
        return new TidyRunner(executor, SwingUtilities::invokeLater, WireTidy::plan, executor::shutdownNow);
    }

    public Job start(CircuitDocument snapshot, ComponentTypeRegistry registry, Set<ElementId> scope,
                     TidyOptions options, Callbacks callbacks) {
        if (disposed) {
            throw new IllegalStateException("the tidy runner was disposed");
        }
        Job job = new Job(callbacks);
        jobs.add(job);
        worker.execute(() -> compute(job, snapshot, registry, scope, options));
        return job;
    }

    private void compute(Job job, CircuitDocument snapshot, ComponentTypeRegistry registry, Set<ElementId> scope,
                         TidyOptions options) {
        Optional<TidyPlan> plan;
        try {
            plan = planner.plan(snapshot, registry, scope, options, () -> job.cancelled,
                    round -> edt.execute(() -> {
                        if (job.isRunning()) {
                            job.callbacks.progress(round);
                        }
                    }));
        } catch (Throwable throwable) {
            edt.execute(() -> {
                if (finish(job)) {
                    job.callbacks.failed(throwable);
                }
            });
            if (throwable instanceof Error error) {
                throw error;
            }
            return;
        }
        edt.execute(() -> {
            if (finish(job)) {
                plan.ifPresent(job.callbacks::finished);
            }
        });
    }

    private boolean finish(Job job) {
        boolean deliver = job.isRunning();
        job.done = true;
        jobs.remove(job);
        return deliver;
    }

    /** Stops every running job without telling anyone and releases the threads. */
    public void dispose() {
        disposed = true;
        jobs.forEach(Job::cancel);
        jobs.clear();
        shutdown.run();
    }
}
