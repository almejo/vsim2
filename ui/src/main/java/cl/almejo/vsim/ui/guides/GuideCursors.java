package cl.almejo.vsim.ui.guides;

import cl.almejo.vsim.core.document.Guide.Orientation;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.HeadlessException;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/**
 * The guide cursor over a ruler (canvas-edges spec §1): a thin line between two small arrows, across the direction the
 * new guide will move (a horizontal line with arrows up and down over the top ruler). Drawn once, the first time it is
 * asked for, with a dark line and a light outline that read on light and dark canvases; where the platform has no
 * custom cursors (headless, a 0×0 best size) it is the crosshair.
 */
final class GuideCursors {

    private static final int DESIGN_SIZE = 32;
    private static final int CENTER = DESIGN_SIZE / 2;
    private static final Color LINE = new Color(0x1C1F24);
    private static final Color OUTLINE = Color.WHITE;
    private static final Map<Orientation, Cursor> CURSORS = new EnumMap<>(Orientation.class);

    private GuideCursors() {
    }

    /** The cursor that pulls a guide of {@code orientation} (EDT only, like every cursor change). */
    static Cursor forOrientation(Orientation orientation) {
        return CURSORS.computeIfAbsent(orientation, GuideCursors::create);
    }

    private static Cursor create(Orientation orientation) {
        try {
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            Dimension best = toolkit.getBestCursorSize(DESIGN_SIZE, DESIGN_SIZE);
            if (best == null || best.width <= 0 || best.height <= 0) {
                return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
            }
            int size = Math.min(best.width, best.height);
            int hotspot = size / 2;
            return toolkit.createCustomCursor(image(size, orientation), new Point(hotspot, hotspot),
                    orientation == Orientation.HORIZONTAL ? "vsim-horizontal-guide" : "vsim-vertical-guide");
        } catch (HeadlessException | IndexOutOfBoundsException exception) {
            return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
        }
    }

    /** The cursor's picture, {@code size} pixels square, centred on the hotspot. */
    static BufferedImage image(int size, Orientation orientation) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.scale(size / (double) DESIGN_SIZE, size / (double) DESIGN_SIZE);
            if (orientation == Orientation.VERTICAL) {
                graphics.rotate(Math.PI / 2, CENTER, CENTER);
            }
            Line2D line = new Line2D.Double(CENTER - 10, CENTER, CENTER + 10, CENTER);
            Path2D.Double arrows = new Path2D.Double();
            arrows.moveTo(CENTER - 4, CENTER - 5);
            arrows.lineTo(CENTER, CENTER - 10);
            arrows.lineTo(CENTER + 4, CENTER - 5);
            arrows.closePath();
            arrows.moveTo(CENTER - 4, CENTER + 5);
            arrows.lineTo(CENTER, CENTER + 10);
            arrows.lineTo(CENTER + 4, CENTER + 5);
            arrows.closePath();
            graphics.setColor(OUTLINE);
            graphics.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.draw(line);
            graphics.draw(arrows);
            graphics.setColor(LINE);
            graphics.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.draw(line);
            graphics.fill(arrows);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
