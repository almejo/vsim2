package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.ui.icons.DrawnCursors;
import cl.almejo.vsim.ui.icons.SignedCursors;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.Map;

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
    private static final Color RING = new Color(0x1C1F24);
    private static final Color OUTLINE = Color.WHITE;
    private static final Color LENS = new Color(255, 255, 255, 215);
    /** The arm of the lens's «+» and «−», in pixels of a 32-pixel cursor. */
    private static final double SIGN_ARM = 4.5;

    private static final SignedCursors CURSORS = new SignedCursors(
            Map.of(DrawnCursors.Sign.PLUS, "vsim-zoom-in", DrawnCursors.Sign.MINUS, "vsim-zoom-out"),
            ZoomCursors::paint, HOTSPOT, Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

    private ZoomCursors() {
    }

    /** The magnifier with a «+» (EDT only, like every cursor change). */
    public static Cursor zoomIn() {
        return CURSORS.cursor(DrawnCursors.Sign.PLUS);
    }

    /** The magnifier with a «−». */
    public static Cursor zoomOut() {
        return CURSORS.cursor(DrawnCursors.Sign.MINUS);
    }

    /** The cursor's picture, {@code size} pixels square: the lens top left, the handle towards the bottom right. */
    static BufferedImage image(int size, DrawnCursors.Sign sign) {
        return CURSORS.image(size, sign);
    }

    private static void paint(Graphics2D graphics, DrawnCursors.Sign sign) {
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
        DrawnCursors.paintSign(graphics, centre, centre, SIGN_ARM, sign);
    }
}
