package cl.almejo.vsim.ui.icons;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A drawn cursor in its «+» and «−» versions: one painter, one picture per sign, each cursor made once. */
class SignedCursorsTest {

    private static final Cursor FALLBACK = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);

    private final SignedCursors cursors = new SignedCursors(
            Map.of(DrawnCursors.Sign.PLUS, "vsim-test-plus", DrawnCursors.Sign.MINUS, "vsim-test-minus"),
            (graphics, sign) -> {
                graphics.setColor(Color.BLACK);
                DrawnCursors.paintSign(graphics, 16, 16, 6, sign);
            }, 16, FALLBACK);

    @Test
    void eachSignHasItsOwnPictureFromTheOnePainter() {
        BufferedImage plus = cursors.image(32, DrawnCursors.Sign.PLUS);
        BufferedImage minus = cursors.image(32, DrawnCursors.Sign.MINUS);
        assertThat(plus.getWidth()).isEqualTo(32);
        assertThat(plus.getRGB(16, 12) >>> 24).isEqualTo(255); // the «+»'s upper arm
        assertThat(minus.getRGB(16, 12) >>> 24).isZero();
        assertThat(minus.getRGB(12, 16) >>> 24).isEqualTo(255);
    }

    @Test
    void eachCursorIsMadeOnceAndHeadlessIsTheFallback() {
        Cursor plus = cursors.cursor(DrawnCursors.Sign.PLUS);
        assertThat(cursors.cursor(DrawnCursors.Sign.PLUS)).isSameAs(plus);
        if (GraphicsEnvironment.isHeadless()) {
            assertThat(plus).isEqualTo(FALLBACK);
            assertThat(cursors.cursor(DrawnCursors.Sign.MINUS)).isEqualTo(FALLBACK);
        }
    }
}
