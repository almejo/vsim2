package cl.almejo.vsim.ui.paint;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AppearanceTest {

    @Test
    void listenersHearOnlyRealChanges() {
        Appearance appearance = new Appearance(ColorScheme.LIGHT);
        AtomicInteger changes = new AtomicInteger();
        Runnable listener = changes::incrementAndGet;
        appearance.addListener(listener);
        appearance.setScheme(ColorScheme.LIGHT);
        assertThat(changes).hasValue(0);
        ColorScheme magenta = AppearanceTestSupport.withBackground(ColorScheme.LIGHT, Color.MAGENTA);
        appearance.setScheme(magenta);
        assertThat(appearance.scheme()).isSameAs(magenta);
        assertThat(changes).hasValue(1);
        assertThat(appearance.listenerCount()).isEqualTo(1);
        appearance.removeListener(listener);
        assertThat(appearance.listenerCount()).isZero();
    }
}
