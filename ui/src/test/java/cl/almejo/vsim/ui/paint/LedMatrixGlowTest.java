package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** «Brillo que se apaga»: what fades is how lit each LED looks, from frame to frame, with a fake clock. */
class LedMatrixGlowTest {

    private final AtomicLong nanos = new AtomicLong();
    private final LedMatrixGlow glow = new LedMatrixGlow(nanos::get);

    private static VisibleState.LedMatrixState rows(int... values) {
        return new VisibleState.LedMatrixState(java.util.Arrays.stream(values).mapToObj(value -> Value.of(2, value))
                .toList());
    }

    @Test
    void aLitLedIsFullyLitAndAnOffOneOffTheFirstTimeItIsSeen() {
        double[][] levels = glow.levels("m", rows(0b10, 0b01));
        assertThat(levels[0]).containsExactly(1, 0);
        assertThat(levels[1]).containsExactly(0, 1);
    }

    @Test
    void anLedThatTurnsOffFadesWithWallTimeFrameByFrameUntilItIsOff() {
        glow.levels("m", rows(0b10));
        nanos.addAndGet(LedMatrixGlow.FADE_NANOS / 4);
        assertThat(glow.levels("m", rows(0b00))[0][0]).isCloseTo(0.75, within(0.001));
        double level = 1;
        for (int frame = 1; frame <= 10; frame++) {
            nanos.addAndGet(LedMatrixGlow.FADE_NANOS / 10);
            level = glow.levels("m", rows(0b00))[0][0];
        }
        assertThat(level).isZero();
    }

    /** Decided in the «Filas fáciles» fix round: a matrix that stops changing still fades to fully off. */
    @Test
    void aFadingLedGoesOnFadingWhileTheSimulationIsPausedUntilItIsOff() {
        glow.levels("m", rows(0b10));
        nanos.addAndGet(LedMatrixGlow.FADE_NANOS / 4);
        double first = glow.levels("m", rows(0b00))[0][0];
        nanos.addAndGet(LedMatrixGlow.FADE_NANOS / 10);
        double second = glow.levels("m", rows(0b00))[0][0];
        for (int frame = 0; frame < 10; frame++) {
            nanos.addAndGet(LedMatrixGlow.FADE_NANOS / 10);
            glow.levels("m", rows(0b00));
        }

        assertThat(second).isLessThan(first);
        assertThat(glow.levels("m", rows(0b00))[0][0]).isZero();
    }

    /** Review M3: a step after a long still spell does not swallow the fade of the LEDs it turned off. */
    @Test
    void anLedAStepTurnsOffAfterALongStillSpellStartsNearlyLitAndFadesFromThere() {
        glow.levels("m", rows(0b10));
        nanos.addAndGet(10 * LedMatrixGlow.FADE_NANOS);

        double afterTheStep = glow.levels("m", rows(0b00))[0][0];

        assertThat(afterTheStep).isGreaterThan(0.5).isLessThan(1);
    }

    @Test
    void fadingSaysWhetherAnyLedIsBetweenLitAndOff() {
        assertThat(LedMatrixGlow.fading(new double[][] {{1, 0}, {0, 1}})).isFalse();
        assertThat(LedMatrixGlow.fading(new double[][] {{1, 0}, {0.4, 1}})).isTrue();
    }

    @Test
    void anLedThatTurnsOnAgainIsFullyLitAtOnce() {
        glow.levels("m", rows(0b10));
        nanos.addAndGet(LedMatrixGlow.FADE_NANOS / 2);
        glow.levels("m", rows(0b00));
        assertThat(glow.levels("m", rows(0b10))[0][0]).isEqualTo(1);
    }

    @Test
    void anUnknownBitHasNoLevelAndEachMatrixHasItsOwnMemory() {
        VisibleState.LedMatrixState unknown = new VisibleState.LedMatrixState(List.of(Value.highImpedance(2)));
        assertThat(glow.levels("m", unknown)[0]).containsExactly(0, 0);
        glow.levels("other", rows(0b11));
        nanos.addAndGet(LedMatrixGlow.FADE_NANOS / 2);
        assertThat(glow.levels("m", rows(0b00))[0]).containsExactly(0, 0);
    }

    @Test
    void aMatrixThatChangedSizeStartsOver() {
        glow.levels("m", rows(0b11));
        nanos.addAndGet(LedMatrixGlow.FADE_NANOS / 2);
        double[][] levels = glow.levels("m", rows(0b00, 0b00));
        assertThat(levels).hasNumberOfRows(2);
        assertThat(levels[0]).containsExactly(0, 0);
    }
}
