package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.ui.icons.DrawnCursors;
import cl.almejo.vsim.ui.icons.SignedCursors;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * The cursors of «Agregar nodo» and «Quitar nodo»: a crosshair with a «+» or a «−» badge, so the hand knows what a
 * click does before it clicks (the main editor's zoom cursors work the same way). Drawn once, the first time they are
 * asked for; where the platform has no custom cursors (headless, a 0×0 best size) they are the plain crosshair. Fixed
 * colours, not the scheme's: a dark cross with a light outline reads on light and dark canvases alike.
 */
final class ShapeCursors {

    /** The crosshair's centre, in pixels of a 32-pixel cursor (scaled with the cursor size); the cursor's hotspot. */
    static final int HOTSPOT = 10;
    private static final int ARM = 7;
    private static final Color INK = new Color(0x1C1F24);
    private static final Color OUTLINE = Color.WHITE;
    private static final Color BADGE_ADD = new Color(0x3C8F4F);
    private static final Color BADGE_REMOVE = new Color(0xC23B42);
    /** The badge's centre (on both axes) and its sign's arm, in pixels of a 32-pixel cursor. */
    private static final double BADGE_CENTRE = 22;
    private static final double BADGE_ARM = 4;
    private static final DrawnCursors.Badge BADGE = new DrawnCursors.Badge(BADGE_CENTRE, BADGE_CENTRE, BADGE_ARM,
            OUTLINE);

    private static final SignedCursors CURSORS = new SignedCursors(
            Map.of(DrawnCursors.Sign.PLUS, "vsim-shape-node-add", DrawnCursors.Sign.MINUS, "vsim-shape-node-remove"),
            ShapeCursors::paint, HOTSPOT, Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

    private ShapeCursors() {
    }

    /** The crosshair with a «+» (EDT only, like every cursor change). */
    static Cursor addNode() {
        return CURSORS.cursor(DrawnCursors.Sign.PLUS);
    }

    /** The crosshair with a «−». */
    static Cursor removeNode() {
        return CURSORS.cursor(DrawnCursors.Sign.MINUS);
    }

    /** The cursor's picture, {@code size} pixels square: the cross top left, the badge towards the bottom right. */
    static BufferedImage image(int size, DrawnCursors.Sign sign) {
        return CURSORS.image(size, sign);
    }

    private static void paint(Graphics2D graphics, DrawnCursors.Sign sign) {
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
        DrawnCursors.paintBadge(graphics, BADGE, sign == DrawnCursors.Sign.MINUS ? BADGE_REMOVE : BADGE_ADD, sign);
    }
}
