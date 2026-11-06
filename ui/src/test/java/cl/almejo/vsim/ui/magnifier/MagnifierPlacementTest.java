package cl.almejo.vsim.ui.magnifier;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round C of the hand test: a panel under the pointer takes the canvas's hover, so the canvas hides it at once and
 * the user sees it shrink away. The panel never covers the pointer, at full size and at every step of its growth (as
 * the popup's timer sets its window, never smaller than one pixel).
 */
class MagnifierPlacementTest {

    private static final Rectangle SCREEN = new Rectangle(0, 0, 1920, 1080);
    private static final Dimension PANEL = new Dimension(300, 200);
    private static final int STEPS = 100;

    @Test
    void inTheMiddleOfTheScreenItNeverCoversThePointerWhileItGrows() {
        assertNeverCovers(new Point(500, 400));
    }

    @Test
    void nearTheRightAndBottomEdgesItGoesToTheOtherSideOfThePointer() {
        for (Point pointer : List.of(new Point(1900, 1060), new Point(1900, 400), new Point(500, 1060))) {
            assertNeverCovers(pointer);
        }
    }

    @Test
    void nearTheLeftAndTopEdgesItStillGoesBesideThePointer() {
        for (Point pointer : List.of(new Point(2, 2), new Point(2, 400), new Point(500, 2))) {
            assertNeverCovers(pointer);
        }
    }

    @Test
    void itStaysInsideTheScreen() {
        for (Point pointer : List.of(new Point(1900, 1060), new Point(2, 2), new Point(500, 400))) {
            MagnifierPlacement placement = MagnifierPlacement.beside(pointer, PANEL, Optional.of(SCREEN));

            assertThat(SCREEN.contains(placement.full())).as("%s", pointer).isTrue();
        }
    }

    @Test
    void withoutAScreenItGoesBelowAndRightOfThePointer() {
        MagnifierPlacement placement = MagnifierPlacement.beside(new Point(500, 400), PANEL, Optional.empty());

        assertThat(placement.full()).isEqualTo(new Rectangle(516, 416, 300, 200));
    }

    private static void assertNeverCovers(Point pointer) {
        MagnifierPlacement placement = MagnifierPlacement.beside(pointer, PANEL, Optional.of(SCREEN));
        for (int step = 0; step <= STEPS; step++) {
            double scale = step / (double) STEPS;
            Rectangle bounds = PopupAnimation.boundsAt(placement.anchor(), placement.full(), scale);
            Rectangle window = new Rectangle(bounds.x, bounds.y, Math.max(1, bounds.width),
                    Math.max(1, bounds.height));
            assertThat(window.contains(pointer)).as("pointer %s, scale %s, window %s", pointer, scale, window)
                    .isFalse();
        }
    }
}
