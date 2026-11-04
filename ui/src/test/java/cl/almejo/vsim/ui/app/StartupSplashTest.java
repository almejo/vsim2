package cl.almejo.vsim.ui.app;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StartupSplashTest {

    private static final Instant SHOWN_AT = Instant.parse("2026-11-03T10:00:00Z");

    @Test
    void aQuickStartWaitsTheRestOfTheMinimum() {
        Instant now = SHOWN_AT.plusMillis(1200);

        assertThat(StartupSplash.remaining(SHOWN_AT, now, Duration.ofSeconds(3))).isEqualTo(Duration.ofMillis(1800));
    }

    @Test
    void aSlowStartDoesNotWait() {
        Instant now = SHOWN_AT.plusSeconds(4);

        assertThat(StartupSplash.remaining(SHOWN_AT, now, Duration.ofSeconds(3))).isZero();
    }

    @Test
    void exactlyTheMinimumDoesNotWait() {
        Instant now = SHOWN_AT.plusSeconds(3);

        assertThat(StartupSplash.remaining(SHOWN_AT, now, Duration.ofSeconds(3))).isZero();
    }

    @Test
    void headlessRunsHaveNoSplash() {
        assertThat(StartupSplash.current()).isEmpty();
    }

    @Test
    void theMinimumIsThreeSeconds() {
        assertThat(StartupSplash.MINIMUM_TIME).isEqualTo(Duration.ofSeconds(3));
    }
}
