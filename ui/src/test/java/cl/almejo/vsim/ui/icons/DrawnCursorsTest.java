package cl.almejo.vsim.ui.icons;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

/** The pieces the drawn cursors share: the badge with its rim and sign, and the sign alone. */
class DrawnCursorsTest {

    private static final DrawnCursors.Badge BADGE = new DrawnCursors.Badge(16, 16, 4, Color.BLACK);
    private static final Color FILL = new Color(0xC62828);

    private static BufferedImage badge(DrawnCursors.Sign sign) {
        return DrawnCursors.picture(DrawnCursors.DESIGN_SIZE,
                graphics -> DrawnCursors.paintBadge(graphics, BADGE, FILL, sign));
    }

    @Test
    void aBadgeIsFilledWithAWhiteSignAndARimOfItsOutline() {
        BufferedImage plus = badge(DrawnCursors.Sign.PLUS);
        assertThat(plus.getRGB(16, 16)).isEqualTo(Color.WHITE.getRGB()); // the centre of the sign
        assertThat(plus.getRGB(16, 13)).isEqualTo(Color.WHITE.getRGB()); // the «+»'s upper arm
        assertThat(plus.getRGB(19, 19)).isEqualTo(FILL.getRGB()); // inside the badge, off the sign
        int rim = plus.getRGB(8, 16); // just outside the badge's radius (7): the rim's outer half
        assertThat(rim >>> 24).isGreaterThan(200);
        assertThat(rim & 0xFFFFFF).isZero();
        assertThat(plus.getRGB(2, 2) >>> 24).isZero(); // outside stays transparent
    }

    @Test
    void aMinusBadgeHasNoUpperArm() {
        BufferedImage minus = badge(DrawnCursors.Sign.MINUS);
        assertThat(minus.getRGB(16, 16)).isEqualTo(Color.WHITE.getRGB());
        assertThat(minus.getRGB(13, 16)).isEqualTo(Color.WHITE.getRGB()); // the arm across
        assertThat(minus.getRGB(16, 13)).isEqualTo(FILL.getRGB());
    }

    @Test
    void aSignAloneIsInTheCurrentColour() {
        BufferedImage plus = DrawnCursors.picture(DrawnCursors.DESIGN_SIZE, graphics -> {
            graphics.setColor(Color.BLUE);
            DrawnCursors.paintSign(graphics, 16, 16, 5, DrawnCursors.Sign.PLUS);
        });
        assertThat(plus.getRGB(16, 12)).isEqualTo(Color.BLUE.getRGB());
        assertThat(plus.getRGB(12, 16)).isEqualTo(Color.BLUE.getRGB());
        assertThat(plus.getRGB(12, 12) >>> 24).isZero();
    }
}
