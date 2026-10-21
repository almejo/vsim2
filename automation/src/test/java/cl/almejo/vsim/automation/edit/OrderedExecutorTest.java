package cl.almejo.vsim.automation.edit;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderedExecutorTest {

    private final Deque<Runnable> pool = new ArrayDeque<>();
    private final OrderedExecutor executor = new OrderedExecutor(pool::add);

    @Test
    void tasksRunLaterInTheOrderTheyCameAndNeverOnTheCallersStack() {
        List<String> ran = new ArrayList<>();

        executor.execute(() -> ran.add("started"));
        executor.execute(() -> ran.add("ended"));

        assertThat(ran).isEmpty();
        assertThat(pool).hasSize(1);
        pool.poll().run();
        assertThat(ran).containsExactly("started", "ended");
    }

    @Test
    void aFailingTaskDoesNotSilenceTheOnesAfterIt() {
        List<String> ran = new ArrayList<>();
        executor.execute(() -> {
            throw new IllegalStateException("a listener failed");
        });
        executor.execute(() -> ran.add("next"));

        Thread.UncaughtExceptionHandler previous = Thread.currentThread().getUncaughtExceptionHandler();
        List<Throwable> reported = new ArrayList<>();
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> reported.add(failure));
        try {
            pool.poll().run();
        } finally {
            Thread.currentThread().setUncaughtExceptionHandler(previous);
        }

        assertThat(ran).containsExactly("next");
        assertThat(reported).singleElement().isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aTaskAfterTheQueueEmptiedIsHandedToThePoolAgain() {
        executor.execute(() -> {
        });
        pool.poll().run();

        executor.execute(() -> {
        });

        assertThat(pool).hasSize(1);
    }
}
