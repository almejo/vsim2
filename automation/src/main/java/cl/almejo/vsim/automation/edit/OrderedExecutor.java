package cl.almejo.vsim.automation.edit;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Runs tasks one at a time, in the order they came, on a shared pool: the task events reach the listeners in order
 * (started before ended) and never on the caller's own stack, so «Detener» pressed on the editor thread does not call
 * the listeners there. No thread of its own: nothing to shut down.
 */
public final class OrderedExecutor implements Executor {

    private final Executor pool;
    private final Queue<Runnable> waiting = new ArrayDeque<>();
    private boolean running;

    public OrderedExecutor() {
        this(ForkJoinPool.commonPool());
    }

    OrderedExecutor(Executor pool) {
        this.pool = Objects.requireNonNull(pool, "pool");
    }

    @Override
    public void execute(Runnable task) {
        Objects.requireNonNull(task, "task");
        synchronized (waiting) {
            waiting.add(task);
            if (running) {
                return;
            }
            running = true;
        }
        pool.execute(this::drain);
    }

    private void drain() {
        while (true) {
            Runnable next;
            synchronized (waiting) {
                next = waiting.poll();
                if (next == null) {
                    running = false;
                    return;
                }
            }
            try {
                next.run();
            } catch (RuntimeException failure) {
                // One listener's failure must not silence the events after it; the pool reports it.
                Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), failure);
            }
        }
    }
}
