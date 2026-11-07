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
        double[][] levels = glow.levels("m", rows(0b10, 0b01), 0);
        assertThat(levels[0]).containsExactly(1, 0);
        assertThat(levels[1]).containsExactly(0, 1);
    }

    @Test
    void anLedThatTurnsOffFadesWithWallTimeWhileTheSimulationAdvances() {
        glow.levels("m", rows(0b10), 0);
        nanos.addAndGet(LedMatrixGlow.FADE_NANOS / 2);
        assertThat(glow.levels("m", rows(0b00), 10)[0][0]).isCloseTo(0.5, within(0.001));
        nanos.addAndGet(LedMatrixGlow.FADE_NANOS);
        assertThat(glow.levels("m", rows(0b00), 20)[0][0]).isZero();
    }

    @Test
    void anLedThatTurnsOnAgainIsFullyLitAtOnce() {
        glow.levels("m", rows(0b10), 0);
        nanos.addAndGet(LedMatrixGlow.FADE_NANOS / 2);
        glow.levels("m", rows(0b00), 10);
        assertThat(glow.levels("m", rows(0b10), 11)[0][0]).isEqualTo(1);
    }

    @Test
    void whilePausedNothingFadesNoMatterHowMuchWallTimePasses() {
        glow.levels("m", rows(0b10), 0);
        nanos.addAndGet(LedMatrixGlow.FADE_NANOS / 4);
        double paused = glow.levels("m", rows(0b00), 10)[0][0];
        nanos.addAndGet(10 * LedMatrixGlow.FADE_NANOS);
        assertThat(glow.levels("m", rows(0b00), 10)[0][0]).isEqualTo(paused);
    }

    @Test
    void anUnknownBitHasNoLevelAndEachMatrixHasItsOwnMemory() {
        VisibleState.LedMatrixState unknown = new VisibleState.LedMatrixState(List.of(Value.highImpedance(2)));
        assertThat(glow.levels("m", unknown, 0)[0]).containsExactly(0, 0);
        glow.levels("other", rows(0b11), 0);
        nanos.addAndGet(LedMatrixGlow.FADE_NANOS / 2);
        assertThat(glow.levels("m", rows(0b00), 5)[0]).containsExactly(0, 0);
    }

    @Test
    void aMatrixThatChangedSizeStartsOver() {
        glow.levels("m", rows(0b11), 0);
        nanos.addAndGet(LedMatrixGlow.FADE_NANOS / 2);
        double[][] levels = glow.levels("m", rows(0b00, 0b00), 5);
        assertThat(levels).hasNumberOfRows(2);
        assertThat(levels[0]).containsExactly(0, 0);
    }
}
