package cl.almejo.vsim.ui.problems;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BlinkTest {

    private final AtomicInteger changes = new AtomicInteger();

    @Test
    void blinksForItsTogglesThenEndsUnlit() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Blink blink = new Blink(changes::incrementAndGet);
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
            Blink blink = new Blink(changes::incrementAndGet);
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
}
