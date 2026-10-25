package cl.almejo.vsim.ui.appearance;

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
 * The cursors of «Agregar nodo» and «Quitar nodo»: a crosshair with a «+» or a «−» badge, so the hand knows what a
 * click does before it clicks (the main editor's zoom cursors work the same way). Drawn once, the first time they are
 * asked for; where the platform has no custom cursors (headless, a 0×0 best size) they are the plain crosshair. Fixed
 * colours, not the scheme's: a dark cross with a light outline reads on light and dark canvases alike.
 */
final class ShapeCursors {

    /** The crosshair's centre, in pixels of a 32-pixel cursor (scaled with the cursor size); the cursor's hotspot. */
    static final int HOTSPOT = 10;
    private static final int DESIGN_SIZE = 32;
    private static final int ARM = 7;
    private static final Color INK = new Color(0x1C1F24);
    private static final Color OUTLINE = Color.WHITE;
    private static final Color BADGE_ADD = new Color(0x3C8F4F);
    private static final Color BADGE_REMOVE = new Color(0xC23B42);

    private static Cursor addNode;
    private static Cursor removeNode;

    private ShapeCursors() {
    }

    /** The crosshair with a «+» (EDT only, like every cursor change). */
    static Cursor addNode() {
        if (addNode == null) {
            addNode = create(false);
        }
        return addNode;
    }

    /** The crosshair with a «−». */
    static Cursor removeNode() {
        if (removeNode == null) {
            removeNode = create(true);
        }
        return removeNode;
    }

    private static Cursor create(boolean removes) {
        try {
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            Dimension best = toolkit.getBestCursorSize(DESIGN_SIZE, DESIGN_SIZE);
            if (best == null || best.width <= 0 || best.height <= 0) {
                return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
            }
            int size = Math.min(best.width, best.height);
            int hotspot = Math.round(HOTSPOT * size / (float) DESIGN_SIZE);
            return toolkit.createCustomCursor(image(size, removes), new Point(hotspot, hotspot),
                    removes ? "vsim-shape-node-remove" : "vsim-shape-node-add");
        } catch (HeadlessException | IndexOutOfBoundsException exception) {
            return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
        }
    }

    /** The cursor's picture, {@code size} pixels square: the cross top left, the badge towards the bottom right. */
    static BufferedImage image(int size, boolean removes) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            graphics.scale(size / (double) DESIGN_SIZE, size / (double) DESIGN_SIZE);
            Line2D.Double horizontal = new Line2D.Double(HOTSPOT - ARM, HOTSPOT, HOTSPOT + ARM, HOTSPOT);
            Line2D.Double vertical = new Line2D.Double(HOTSPOT, HOTSPOT - ARM, HOTSPOT, HOTSPOT + ARM);
            graphics.setColor(OUTLINE);
            graphics.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            graphics.draw(horizontal);
            graphics.draw(vertical);
            graphics.setColor(INK);
            graphics.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            graphics.draw(horizontal);
            graphics.draw(vertical);
            paintBadge(graphics, removes);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void paintBadge(Graphics2D graphics, boolean removes) {
        double centre = 22;
        double radius = 7;
        Ellipse2D.Double badge = new Ellipse2D.Double(centre - radius, centre - radius, 2 * radius, 2 * radius);
        graphics.setColor(OUTLINE);
        graphics.setStroke(new BasicStroke(2f));
        graphics.draw(badge);
        graphics.setColor(removes ? BADGE_REMOVE : BADGE_ADD);
        graphics.fill(badge);
        graphics.setColor(OUTLINE);
        double arm = 4;
        double thickness = 2;
        graphics.fill(new Rectangle2D.Double(centre - arm, centre - thickness / 2, 2 * arm, thickness));
        if (!removes) {
            graphics.fill(new Rectangle2D.Double(centre - thickness / 2, centre - arm, thickness, 2 * arm));
        }
    }
}
