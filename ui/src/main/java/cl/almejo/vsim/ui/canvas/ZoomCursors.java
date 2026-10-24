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
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * The zoom tool's magnifier with a «+» (a click zooms in) or a «−» (with Alt or Shift: zooms out), zoom spec §4.3.
 * Drawn once, the first time they are asked for, like {@link SelectionCursors}; where the platform has no custom
 * cursors (headless, a 0×0 best size) they are the plain crosshair. Fixed colours, not the scheme's: a dark ring and
 * handle with a light outline around a light lens read on light and dark canvases alike. The hotspot is the lens's
 * centre, so a click zooms around the point under it.
 */
public final class ZoomCursors {

    /** The lens's centre, in pixels of a 32-pixel cursor (scaled with the cursor size); the cursor's hotspot. */
    static final int HOTSPOT = 12;
    /** The radius of the lens's ring, in pixels of a 32-pixel cursor. */
    static final int LENS_RADIUS = 8;
    private static final int DESIGN_SIZE = 32;
    private static final Color RING = new Color(0x1C1F24);
    private static final Color OUTLINE = Color.WHITE;
    private static final Color LENS = new Color(255, 255, 255, 215);

    private static Cursor zoomIn;
    private static Cursor zoomOut;

    private ZoomCursors() {
    }

    /** The magnifier with a «+» (EDT only, like every cursor change). */
    public static Cursor zoomIn() {
        if (zoomIn == null) {
            zoomIn = create(false);
        }
        return zoomIn;
    }

    /** The magnifier with a «−». */
    public static Cursor zoomOut() {
        if (zoomOut == null) {
            zoomOut = create(true);
        }
        return zoomOut;
    }

    private static Cursor create(boolean zoomsOut) {
        try {
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            Dimension best = toolkit.getBestCursorSize(DESIGN_SIZE, DESIGN_SIZE);
            if (best == null || best.width <= 0 || best.height <= 0) {
                return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
            }
            int size = Math.min(best.width, best.height);
            int hotspot = Math.round(HOTSPOT * size / (float) DESIGN_SIZE);
            return toolkit.createCustomCursor(image(size, zoomsOut), new Point(hotspot, hotspot),
                    zoomsOut ? "vsim-zoom-out" : "vsim-zoom-in");
        } catch (HeadlessException | IndexOutOfBoundsException exception) {
            return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
        }
    }

    /** The cursor's picture, {@code size} pixels square: the lens top left, the handle towards the bottom right. */
    static BufferedImage image(int size, boolean zoomsOut) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            graphics.scale(size / (double) DESIGN_SIZE, size / (double) DESIGN_SIZE);
            double centre = HOTSPOT;
            double radius = LENS_RADIUS;
            Ellipse2D.Double lens = new Ellipse2D.Double(centre - radius, centre - radius, 2 * radius, 2 * radius);
            double handleStart = centre + radius * Math.sqrt(0.5);
            Line2D.Double handle = new Line2D.Double(handleStart, handleStart, 28.5, 28.5);

            graphics.setColor(OUTLINE);
            graphics.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.draw(handle);
            graphics.setStroke(new BasicStroke(4.5f));
            graphics.draw(lens);
            graphics.setColor(LENS);
            graphics.fill(lens);
            graphics.setColor(RING);
            graphics.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.draw(handle);
            graphics.setStroke(new BasicStroke(2.5f));
            graphics.draw(lens);

            double armLength = 4.5;
            double armThickness = 2;
            graphics.fill(new Rectangle2D.Double(centre - armLength, centre - armThickness / 2, 2 * armLength,
                    armThickness));
            if (!zoomsOut) {
                graphics.fill(new Rectangle2D.Double(centre - armThickness / 2, centre - armLength, armThickness,
                        2 * armLength));
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
