package cl.almejo.vsim.ui.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** «Claude está pensando…» (IA.3 Task 8): after 20 s without hearing from the AI, gone at the next call. */
class ThinkingWatchTest {

    private static final long SECOND = 1_000_000_000L;

    private final AtomicLong clock = new AtomicLong(5 * SECOND);
    private final AtomicBoolean quiet = new AtomicBoolean(true);
    private final AtomicInteger changes = new AtomicInteger();
    private final ThinkingWatch watch = new ThinkingWatch(clock::get, quiet::get, changes::incrementAndGet);

    @AfterEach
    void close() {
        watch.dispose();
    }

    @Test
    void itShowsAfterTwentySecondsOfSilenceAndGoesAtTheNextCall() {
        watch.start();
        clock.addAndGet(19 * SECOND);
        watch.tick();
        assertThat(watch.showing()).isFalse();

        clock.addAndGet(SECOND);
        watch.tick();
        watch.tick();
        assertThat(watch.showing()).isTrue();
        assertThat(changes.get()).isEqualTo(1);

        watch.heard();
        assertThat(watch.showing()).isFalse();
        assertThat(changes.get()).isEqualTo(2);
        assertThat(ThinkingWatch.LIMIT.toSeconds()).isEqualTo(20);
    }

    @Test
    void neverWhileSomethingWaitsForTheUser() {
        watch.start();
        quiet.set(false); // a step waits for «Siguiente», a pause runs or a question waits
        clock.addAndGet(60 * SECOND);
        watch.tick();
        assertThat(watch.showing()).isFalse();

        quiet.set(true);
        watch.tick();
        assertThat(watch.showing()).isTrue();
        quiet.set(false);
        watch.tick();
        assertThat(watch.showing()).isFalse();
    }

    @Test
    void stoppedItNeitherShowsNorTicks() {
        watch.start();
        assertThat(watch.isTimerRunning()).isTrue();
        clock.addAndGet(30 * SECOND);
        watch.tick();
        assertThat(watch.showing()).isTrue();

        watch.stop();

        assertThat(watch.showing()).isFalse();
        assertThat(watch.isTimerRunning()).isFalse();
        clock.addAndGet(30 * SECOND);
        watch.tick();
        assertThat(watch.showing()).isFalse();
    }
}
