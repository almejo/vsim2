package cl.almejo.vsim.ui.magnifier;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.Rectangle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The «Mirar adentro» panel grows from the piece and shrinks back to it (about 200 ms, ease-out), on a fake clock. */
class PopupAnimationTest {

    private static final long MILLISECOND = 1_000_000L;

    private long now;
    private final PopupAnimation animation = new PopupAnimation(() -> now, () -> true);

    @Test
    void growingStartsAsAPointAndEndsAtFullSizeAfterAboutTwoHundredMilliseconds() {
        animation.grow();

        assertThat(animation.scale()).isZero();
        assertThat(animation.isFinished()).isFalse();
        now = 200 * MILLISECOND;
        assertThat(animation.scale()).isEqualTo(1.0);
        assertThat(animation.isFinished()).isTrue();
    }

    @Test
    void theGrowthEasesOutSoTheHalfwayTimeIsPastHalfTheSize() {
        animation.grow();
        now = 100 * MILLISECOND;

        assertThat(animation.scale()).isCloseTo(0.875, within(0.001));
        assertThat(animation.isFinished()).isFalse();
    }

    @Test
    void shrinkingGoesBackToThePointAndThenIsFinished() {
        animation.grow();
        now = 500 * MILLISECOND;
        animation.shrink();

        assertThat(animation.scale()).isEqualTo(1.0);
        now += 100 * MILLISECOND;
        assertThat(animation.scale()).isCloseTo(0.125, within(0.001));
        now += 100 * MILLISECOND;
        assertThat(animation.scale()).isZero();
        assertThat(animation.isFinished()).isTrue();
        assertThat(animation.isShrinking()).isTrue();
    }

    @Test
    void shrinkingMidGrowthStartsFromTheCurrentSize() {
        animation.grow();
        now = 100 * MILLISECOND;
        double reached = animation.scale();
        animation.shrink();

        assertThat(animation.scale()).isCloseTo(reached, within(0.001));
        now += 50 * MILLISECOND;
        assertThat(animation.scale()).isLessThan(reached);
    }

    @Test
    void theBoundsGoFromThePieceToTheFullRectangle() {
        Rectangle full = new Rectangle(100, 200, 80, 40);
        Point piece = new Point(0, 0);

        assertThat(PopupAnimation.boundsAt(piece, full, 0.0)).isEqualTo(new Rectangle(0, 0, 0, 0));
        assertThat(PopupAnimation.boundsAt(piece, full, 1.0)).isEqualTo(full);
        Rectangle half = PopupAnimation.boundsAt(piece, full, 0.5);
        assertThat(half.width).isEqualTo(40);
        assertThat(half.height).isEqualTo(20);
        assertThat(half.getCenterX()).isCloseTo(70, within(1.0));
        assertThat(half.getCenterY()).isCloseTo(110, within(1.0));
    }

    @Test
    void withAnimationsOffItIsFullAtOnceAndGoesAtOnce() {
        PopupAnimation still = new PopupAnimation(() -> now, () -> false);

        still.grow();
        assertThat(still.scale()).isEqualTo(1.0);
        assertThat(still.isFinished()).isTrue();

        still.shrink();
        assertThat(still.isShrinking()).isTrue();
        assertThat(still.scale()).isZero();
        assertThat(still.isFinished()).isTrue();
    }

    @Test
    void turningAnimationsOffWhileGrowingFinishesItAtTheNextAsk() {
        java.util.concurrent.atomic.AtomicBoolean animations = new java.util.concurrent.atomic.AtomicBoolean(true);
        PopupAnimation live = new PopupAnimation(() -> now, animations::get);
        live.grow();
        now += 50 * 1_000_000L;
        assertThat(live.isFinished()).isFalse();

        animations.set(false);

        assertThat(live.isFinished()).isTrue();
        assertThat(live.scale()).isEqualTo(1.0);
    }
}
