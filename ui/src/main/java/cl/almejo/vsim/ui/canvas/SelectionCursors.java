package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.ui.icons.DrawnCursors;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
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
    private static final Color ARROW = new Color(0x1C1F24);
    private static final Color OUTLINE = Color.WHITE;
    private static final Color ADD_BADGE = new Color(0x2E7D32);
    private static final Color REMOVE_BADGE = new Color(0xC62828);
    /** The badge's centre (on both axes) and its sign's arm, in pixels of a 32-pixel cursor. */
    private static final double BADGE_CENTRE = 23.5;
    private static final double BADGE_ARM = 4.5;

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
        return DrawnCursors.create(removes ? "vsim-remove-from-selection" : "vsim-add-to-selection",
                size -> image(size, removes), DrawnCursors.scaledHotspot(HOTSPOT), Cursor.getDefaultCursor());
    }

    /** The cursor's picture, {@code size} pixels square: the arrow from the top-left corner, the badge bottom right. */
    static BufferedImage image(int size, boolean removes) {
        return DrawnCursors.picture(size, graphics -> paint(graphics, removes));
    }

    private static void paint(Graphics2D graphics, boolean removes) {
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
        DrawnCursors.paintBadge(graphics, BADGE_CENTRE, BADGE_CENTRE, OUTLINE, removes ? REMOVE_BADGE : ADD_BADGE,
                BADGE_ARM, removes);
    }
}
