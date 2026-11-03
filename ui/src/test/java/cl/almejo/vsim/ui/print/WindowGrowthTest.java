package cl.almejo.vsim.ui.print;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Rectangle;

import static org.assertj.core.api.Assertions.assertThat;

class WindowGrowthTest {

    private static final Rectangle SCREEN = new Rectangle(0, 0, 1000, 800);

    @Test
    void aWindowSmallerThanItsContentGrowsInTheDimensionThatIsShort() {
        assertThat(WindowGrowth.grown(new Rectangle(100, 100, 500, 400), new Dimension(600, 300), SCREEN))
                .isEqualTo(new Rectangle(100, 100, 600, 400));
    }

    @Test
    void aWindowThatFitsIsLeftAloneEvenWhenTheUserMadeItLarger() {
        Rectangle large = new Rectangle(50, 50, 900, 700);
        assertThat(WindowGrowth.grown(large, new Dimension(600, 400), SCREEN)).isEqualTo(large);
    }

    @Test
    void itNeverGrowsBeyondTheUsableScreenAndIsMovedBackOnto() {
        assertThat(WindowGrowth.grown(new Rectangle(700, 600, 200, 100), new Dimension(1500, 900), SCREEN))
                .isEqualTo(new Rectangle(0, 0, 1000, 800));
    }

    @Test
    void aGrowingWindowNearAnEdgeSlidesToStayInside() {
        assertThat(WindowGrowth.grown(new Rectangle(800, 100, 150, 100), new Dimension(400, 100), SCREEN))
                .isEqualTo(new Rectangle(600, 100, 400, 100));
    }
}
