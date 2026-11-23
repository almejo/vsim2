package cl.almejo.vsim.ui.ai.present;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** The fade shared by the subtitle and the step callout of «Presentar». */
class PresentationFadeTest {

    private static final long FADE_NANOS = TimeUnit.MILLISECONDS.toNanos(PresentationFade.FADE_MILLISECONDS);

    private final AtomicLong clock = new AtomicLong(1_000);
    private final AtomicBoolean animate = new AtomicBoolean(true);
    private final AtomicInteger repaints = new AtomicInteger();
    private final AtomicInteger gone = new AtomicInteger();
    private final PresentationFade fade = new PresentationFade(animate::get, repaints::incrementAndGet, clock::get,
            gone::incrementAndGet);

    @AfterEach
    void close() {
        fade.dispose();
    }

    @Test
    void aNewOneFadesInAndAReplacementKeepsItsPlace() {
        fade.show();
        assertThat(fade.alpha()).isZero();
        clock.addAndGet(FADE_NANOS / 2);
        assertThat(fade.alpha()).isEqualTo(0.5);

        fade.show();

        assertThat(fade.alpha()).as("replacing a visible one keeps its opacity").isEqualTo(0.5);
        assertThat(repaints.get()).isEqualTo(2);
    }

    @Test
    void aClearedOneFadesOutAndIsThenGone() {
        animate.set(false);
        fade.show();
        animate.set(true);

        fade.clear();
        clock.addAndGet(FADE_NANOS);
        fade.tick();

        assertThat(fade.alpha()).isZero();
        assertThat(gone.get()).isEqualTo(1);
        assertThat(fade.isTimerRunning()).isFalse();
    }

    @Test
    void withoutAnimationsAClearIsImmediateAndADisposedFadeIsMarked() {
        animate.set(false);
        fade.show();
        assertThat(fade.alpha()).isEqualTo(1);

        fade.clear();

        assertThat(gone.get()).isEqualTo(1);
        fade.dispose();
        assertThat(fade.disposed()).isTrue();
    }
}
