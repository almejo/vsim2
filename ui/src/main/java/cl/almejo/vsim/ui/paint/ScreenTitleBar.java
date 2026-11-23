package cl.almejo.vsim.ui.paint;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

/**
 * The title bar of a part with a screen (a Terminal, a UART 16550): a band across the top of the part, clipped to its
 * outline, with a divider under it; the part's name at its start and its chip (faint) at its end. Each part says how
 * tall its bar is, what fills it and which chip it shows.
 */
final class ScreenTitleBar {

    static final double PADDING = 6;
    static final float TEXT_SIZE = 8f;
    /** Room kept between the name and the chip. */
    private static final double GAP = 8;
    /** The chip is quieter than the name: the mockup's muted colour. */
    private static final int CHIP_ALPHA = 170;
    private static final BasicStroke DIVIDER = new BasicStroke(1f);

    private ScreenTitleBar() {
    }

    /** The band, {@code height} tall and filled with {@code fill}, inside {@code outline}, and its divider. */
    static void paintBand(Graphics2D graphics, Shape outline, double width, double height, Color fill,
                          ColorScheme scheme) {
        Graphics2D clipped = (Graphics2D) graphics.create();
        try {
            clipped.clip(outline);
            clipped.setColor(fill);
            clipped.fill(new Rectangle2D.Double(0, 0, width, height));
            clipped.setColor(scheme.componentStroke());
            clipped.setStroke(DIVIDER);
            clipped.draw(new Line2D.Double(0, height, width, height));
        } finally {
            clipped.dispose();
        }
    }

    /**
     * Whether the band is upside down: turned that way the part's left is the screen's right, and the texts keep
     * their side of the bar.
     */
    static boolean upsideDown(Graphics2D graphics) {
        return Math.abs(PainterSupport.quarterTurns(graphics)) % 4 == 2;
    }

    /** The alignment of a text at the bar's start. */
    static double start(Graphics2D graphics) {
        return upsideDown(graphics) ? PainterSupport.ALIGN_RIGHT : PainterSupport.ALIGN_LEFT;
    }

    /** The name at the bar's start, cut with «…» to leave room for {@code chip}, faint at the bar's end. */
    static void paintNameAndChip(Graphics2D graphics, double width, double height, String name, String chip,
                                 ColorScheme scheme) {
        double middle = height / 2;
        FontMetrics metrics = graphics.getFontMetrics(PainterSupport.BASE_FONT.deriveFont(TEXT_SIZE));
        double nameRoom = width - 2 * PADDING - metrics.stringWidth(chip) - GAP;
        PainterSupport.uprightText(graphics, PainterSupport.ellipsized(name, metrics, nameRoom), PADDING, middle,
                TEXT_SIZE, scheme.text(), start(graphics));
        PainterSupport.uprightText(graphics, chip, width - PADDING, middle, TEXT_SIZE,
                PainterSupport.withAlpha(scheme.text(), CHIP_ALPHA),
                upsideDown(graphics) ? PainterSupport.ALIGN_LEFT : PainterSupport.ALIGN_RIGHT);
    }
}
