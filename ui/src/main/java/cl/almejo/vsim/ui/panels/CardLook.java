package cl.almejo.vsim.ui.panels;

import javax.swing.UIManager;
import java.awt.AlphaComposite;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.function.Consumer;

/**
 * What the floating cards share (I6): one corner (unscaled; scale it with {@code UIScale}), one close icon, their fonts
 * and their fade.
 */
public final class CardLook {

    /** The arc of the rounded corner of a card, in pixels at 100 %. */
    public static final int CORNER = 10;
    /** The icon of a card's close button (the find bar's cross, recoloured by FlatLaf in dark themes). */
    public static final String CLOSE_ICON = "find-close";

    private CardLook() {
    }

    /** A card's text: the theme's label font (a 13 pt sans serif without a theme), read now. */
    public static Font textFont() {
        Font font = UIManager.getFont("Label.font");
        return font == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 13) : font;
    }

    /** A card's title: its text in bold, {@code growth} points larger. */
    public static Font titleFont(float growth) {
        Font font = textFont();
        return font.deriveFont(Font.BOLD, font.getSize2D() + growth);
    }

    /** A card's small print: its text {@code shrink} points smaller, never under the floor ({@link FontFloor}). */
    public static Font smallFont(float shrink) {
        return FontFloor.smaller(textFont(), shrink);
    }

    /** Paints with {@code paint} on a copy of {@code graphics}, {@code opacity} opaque: a card fading in or out. */
    public static void paintFaded(Graphics graphics, float opacity, Consumer<Graphics> paint) {
        Graphics2D painter = (Graphics2D) graphics.create();
        try {
            painter.setComposite(AlphaComposite.SrcOver.derive(opacity));
            paint.accept(painter);
        } finally {
            painter.dispose();
        }
    }
}
