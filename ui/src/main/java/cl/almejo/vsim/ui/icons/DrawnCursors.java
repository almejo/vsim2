package cl.almejo.vsim.ui.icons;

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
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

/**
 * The cursors VSim draws itself (the zoom magnifier, the selection arrow with a badge, the shape editor's crosshair,
 * the guide arrows): designed on a 32-pixel square and scaled to the platform's best cursor size. Fixed colours, not
 * the scheme's, chosen by each cursor so it reads on light and dark canvases alike. Where the platform has no custom
 * cursors (headless, a 0×0 best size) a cursor is a predefined fallback.
 */
public final class DrawnCursors {

    /** The side of the square the cursors are designed on, in pixels. */
    public static final int DESIGN_SIZE = 32;
    /** The thickness of a badge's or a lens's «+» and «−» arms, in design pixels. */
    private static final double SIGN_THICKNESS = 2;
    private static final double BADGE_RADIUS = 7;

    private DrawnCursors() {
    }

    /**
     * The cursor {@code name} at the platform's best size, its picture from {@code picture} (given the size) and its
     * hotspot from {@code hotspot} (given the size), or {@code fallback} where the platform has no custom cursors.
     */
    public static Cursor create(String name, IntFunction<BufferedImage> picture, IntUnaryOperator hotspot,
                                Cursor fallback) {
        try {
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            Dimension best = toolkit.getBestCursorSize(DESIGN_SIZE, DESIGN_SIZE);
            if (best == null || best.width <= 0 || best.height <= 0) {
                return fallback;
            }
            int size = Math.min(best.width, best.height);
            int hotspotAtSize = hotspot.applyAsInt(size);
            return toolkit.createCustomCursor(picture.apply(size), new Point(hotspotAtSize, hotspotAtSize), name);
        } catch (HeadlessException | IndexOutOfBoundsException exception) {
            return fallback;
        }
    }

    /** The hotspot at {@code designPixels} of the design square, scaled to the cursor's size and rounded. */
    public static IntUnaryOperator scaledHotspot(int designPixels) {
        return size -> Math.round(designPixels * size / (float) DESIGN_SIZE);
    }

    /**
     * A transparent picture {@code size} pixels square, antialiased, with pure strokes (no normalization), scaled so
     * {@code painter} draws in design pixels.
     */
    public static BufferedImage picture(int size, Consumer<Graphics2D> painter) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            graphics.scale(size / (double) DESIGN_SIZE, size / (double) DESIGN_SIZE);
            painter.accept(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /**
     * A round badge centred at ({@code centreX}, {@code centreY}): {@code fill} with an {@code outline} rim, and a white
     * «−», or «+» unless {@code minus}, with arms of {@code armLength}.
     */
    public static void paintBadge(Graphics2D graphics, double centreX, double centreY, Color outline, Color fill,
                                  double armLength, boolean minus) {
        Ellipse2D.Double badge = new Ellipse2D.Double(centreX - BADGE_RADIUS, centreY - BADGE_RADIUS,
                2 * BADGE_RADIUS, 2 * BADGE_RADIUS);
        graphics.setColor(outline);
        graphics.setStroke(new BasicStroke(2f));
        graphics.draw(badge);
        graphics.setColor(fill);
        graphics.fill(badge);
        graphics.setColor(Color.WHITE);
        paintSign(graphics, centreX, centreY, armLength, minus);
    }

    /** A «−», or a «+» unless {@code minus}, centred at ({@code centreX}, {@code centreY}), in the current colour. */
    public static void paintSign(Graphics2D graphics, double centreX, double centreY, double armLength,
                                 boolean minus) {
        graphics.fill(new Rectangle2D.Double(centreX - armLength, centreY - SIGN_THICKNESS / 2, 2 * armLength,
                SIGN_THICKNESS));
        if (!minus) {
            graphics.fill(new Rectangle2D.Double(centreX - SIGN_THICKNESS / 2, centreY - armLength, SIGN_THICKNESS,
                    2 * armLength));
        }
    }
}
