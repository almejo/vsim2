package cl.almejo.vsim.ui.canvas;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.HeadlessException;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * M6.8: the arrow with a «+» (add to the selection) or «−» (take out of it) badge. The cursors are drawn once, the
 * first time they are asked for; where the platform has no custom cursors (headless, a 0×0 best size) they are the
 * plain arrow. The colours are fixed, not the scheme's: a dark arrow with a light outline and a badge in a coloured
 * circle with a light rim read on light and dark canvases alike.
 */
public final class SelectionCursors {

    /** The arrow's tip, in pixels of a 32-pixel cursor (scaled with the cursor size); the cursor's hotspot. */
    static final int HOTSPOT = 1;
    private static final int DESIGN_SIZE = 32;
    private static final Color ARROW = new Color(0x1C1F24);
    private static final Color OUTLINE = Color.WHITE;
    private static final Color ADD_BADGE = new Color(0x2E7D32);
    private static final Color REMOVE_BADGE = new Color(0xC62828);
    private static final Color BADGE_SIGN = Color.WHITE;

    private static Cursor adding;
    private static Cursor removing;

    private SelectionCursors() {
    }

    /** The arrow with a «+» badge (EDT only, like every cursor change). */
    public static Cursor adding() {
        if (adding == null) {
            adding = create(false);
        }
        return adding;
    }

    /** The arrow with a «−» badge. */
    public static Cursor removing() {
        if (removing == null) {
            removing = create(true);
        }
        return removing;
    }

    private static Cursor create(boolean removes) {
        try {
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            Dimension best = toolkit.getBestCursorSize(DESIGN_SIZE, DESIGN_SIZE);
            if (best == null || best.width <= 0 || best.height <= 0) {
                return Cursor.getDefaultCursor();
            }
            int size = Math.min(best.width, best.height);
            int hotspot = Math.round(HOTSPOT * size / (float) DESIGN_SIZE);
            return toolkit.createCustomCursor(image(size, removes), new Point(hotspot, hotspot),
                    removes ? "vsim-remove-from-selection" : "vsim-add-to-selection");
        } catch (HeadlessException | IndexOutOfBoundsException exception) {
            return Cursor.getDefaultCursor();
        }
    }

    /** The cursor's picture, {@code size} pixels square: the arrow from the top-left corner, the badge bottom right. */
    static BufferedImage image(int size, boolean removes) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            graphics.scale(size / (double) DESIGN_SIZE, size / (double) DESIGN_SIZE);
            Path2D.Double arrow = new Path2D.Double();
            arrow.moveTo(HOTSPOT, HOTSPOT);
            arrow.lineTo(HOTSPOT, HOTSPOT + 17);
            arrow.lineTo(HOTSPOT + 4.5, HOTSPOT + 13);
            arrow.lineTo(HOTSPOT + 7.5, HOTSPOT + 19.5);
            arrow.lineTo(HOTSPOT + 10, HOTSPOT + 18.3);
            arrow.lineTo(HOTSPOT + 7, HOTSPOT + 12);
            arrow.lineTo(HOTSPOT + 12.5, HOTSPOT + 12);
            arrow.closePath();
            graphics.setColor(OUTLINE);
            graphics.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.draw(arrow);
            graphics.setColor(ARROW);
            graphics.fill(arrow);

            double centreX = 23.5;
            double centreY = 23.5;
            double radius = 7;
            Ellipse2D.Double badge = new Ellipse2D.Double(centreX - radius, centreY - radius, 2 * radius, 2 * radius);
            graphics.setColor(OUTLINE);
            graphics.setStroke(new BasicStroke(2f));
            graphics.draw(badge);
            graphics.setColor(removes ? REMOVE_BADGE : ADD_BADGE);
            graphics.fill(badge);
            graphics.setColor(BADGE_SIGN);
            double armLength = 4.5;
            double armThickness = 2;
            graphics.fill(new Rectangle2D.Double(centreX - armLength, centreY - armThickness / 2, 2 * armLength,
                    armThickness));
            if (!removes) {
                graphics.fill(new Rectangle2D.Double(centreX - armThickness / 2, centreY - armLength, armThickness,
                        2 * armLength));
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
