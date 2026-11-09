package cl.almejo.vsim.ui.problems;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BlinkTest {

    private final AtomicInteger changes = new AtomicInteger();

    @Test
    void blinksForItsTogglesThenEndsUnlit() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Blink blink = new Blink(changes::incrementAndGet, () -> true);
            assertThat(blink.isBlinking()).isFalse();
            blink.start();
            assertThat(blink.isLit()).isTrue();
            for (int step = 1; step < Blink.TOGGLES; step++) {
                blink.tick();
                assertThat(blink.isLit()).isEqualTo(step % 2 == 0);
                assertThat(blink.isBlinking()).isTrue();
            }
            blink.tick();
            assertThat(blink.isBlinking()).isFalse();
            assertThat(blink.isLit()).isFalse();
            blink.tick(); // over: nothing changes
            assertThat(changes.get()).isEqualTo(1 + Blink.TOGGLES);
        });
    }

    @Test
    void stopEndsAtOnceAndDisposeDoesSoQuietly() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Blink blink = new Blink(changes::incrementAndGet, () -> true);
            blink.start();
            blink.stop();
            assertThat(blink.isBlinking()).isFalse();
            assertThat(blink.isLit()).isFalse();
            assertThat(changes.get()).isEqualTo(2);
            blink.start();
            blink.dispose();
            assertThat(blink.isLit()).isFalse();
            assertThat(changes.get()).isEqualTo(3);
        });
    }

    @Test
    void withAnimationsOffItStaysLitForTheSameTimeThenGoesOutAtOnce() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Blink blink = new Blink(changes::incrementAndGet, () -> false);
            blink.start();
            for (int step = 1; step < Blink.TOGGLES; step++) {
                blink.tick();
                assertThat(blink.isLit()).isTrue();
                assertThat(blink.isBlinking()).isTrue();
            }
            blink.tick();
            assertThat(blink.isBlinking()).isFalse();
            assertThat(blink.isLit()).isFalse();
            // lit at the start and out at the end: nothing repainted in between, there was nothing to change
            assertThat(changes.get()).isEqualTo(2);
        });
    }

    @Test
    void turningAnimationsOffWhileItBlinksKeepsItLitFromTheNextTick() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicBoolean animations = new AtomicBoolean(true);
            Blink blink = new Blink(changes::incrementAndGet, animations::get);
            blink.start();
            blink.tick();
            assertThat(blink.isLit()).isFalse();

            animations.set(false);
            blink.tick();

            assertThat(blink.isLit()).isTrue();
            blink.tick();
            assertThat(blink.isLit()).isTrue();
            blink.dispose();
        });
    }
}
