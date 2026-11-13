package cl.almejo.vsim.ui.netcard;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NetHoverTest {

    private static final long DELAY = 750;

    private long now = 10_000;
    private final NetHover<String> hover = new NetHover<>(() -> now, () -> DELAY);

    private void advance(long milliseconds) {
        now += milliseconds;
        hover.tick();
    }

    @Test
    void hoveringAWireLightsItsNetAtOnceAndShowsTheCardAfterTheDelay() {
        hover.overWire("A");

        assertThat(hover.current()).contains("A");
        assertThat(hover.cardShown()).isFalse();
        advance(DELAY - 1);
        assertThat(hover.cardShown()).isFalse();
        advance(1);
        assertThat(hover.cardShown()).isTrue();
        assertThat(hover.opacity()).isEqualTo(1f);
        assertThat(hover.needsTicks()).isFalse();
    }

    @Test
    void leavingTheWireFadesForOneSecondThenHides() {
        hover.overWire("A");
        advance(DELAY);

        hover.offWire();
        advance(999);

        assertThat(hover.current()).contains("A");
        assertThat(hover.opacity()).isBetween(0f, 0.01f);
        advance(1);
        assertThat(hover.current()).isEmpty();
        assertThat(hover.cardShown()).isFalse();
    }

    @Test
    void comingBackToTheNetStopsTheFade() {
        hover.overWire("A");
        advance(DELAY);
        hover.offWire();
        advance(500);

        hover.overWire("A");
        advance(2_000);

        assertThat(hover.current()).contains("A");
        assertThat(hover.opacity()).isEqualTo(1f);
    }

    @Test
    void crossingAnotherWireOnTheWayKeepsTheFirstUnlessThePointerRestsThere() {
        hover.overWire("A");
        advance(DELAY);
        hover.offWire();
        advance(100);

        hover.overWire("B");
        advance(DELAY - 1);
        assertThat(hover.current()).contains("A");
        hover.offWire();
        advance(100);
        assertThat(hover.current()).contains("A");
    }

    @Test
    void restingOnAnotherWireTakesOverWithItsCard() {
        hover.overWire("A");
        advance(DELAY);
        hover.offWire();

        hover.overWire("B");
        advance(DELAY);

        assertThat(hover.current()).contains("B");
        assertThat(hover.cardShown()).isTrue();
        assertThat(hover.opacity()).isEqualTo(1f);
    }

    @Test
    void beforeTheCardShowsAnotherWireTakesOverAtOnce() {
        hover.overWire("A");
        advance(100);

        hover.overWire("B");

        assertThat(hover.current()).contains("B");
    }

    @Test
    void thePointerOverTheCardStopsTheFadeAndLeavingItStartsItAgain() {
        hover.overWire("A");
        advance(DELAY);
        hover.offWire();
        advance(900);

        hover.enterCard();
        advance(5_000);
        assertThat(hover.current()).contains("A");
        assertThat(hover.opacity()).isEqualTo(1f);

        hover.leaveCard();
        advance(1_000);
        assertThat(hover.current()).isEmpty();
    }

    @Test
    void aPinnedCardStaysAfterTheNetIsLeftAndIgnoresOtherWiresUntilClosed() {
        hover.overWire("A");
        advance(DELAY);
        hover.enterCard();
        hover.pin();
        hover.leaveCard();
        hover.overWire("B");
        advance(2_000);

        assertThat(hover.current()).contains("A");
        assertThat(hover.pinned()).isTrue();
        hover.close();
        assertThat(hover.current()).isEmpty();
        assertThat(hover.pinned()).isFalse();
    }
}
