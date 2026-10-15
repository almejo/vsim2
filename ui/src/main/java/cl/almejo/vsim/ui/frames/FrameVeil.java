package cl.almejo.vsim.ui.frames;

import cl.almejo.vsim.ui.paint.ColorScheme;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;

/**
 * The translucent veil over everything outside a frame while it is resized or moved (spec §3), like a screenshot
 * crop. Painted only by the gestures, on the canvas, so never exported; its colour is read from the scheme each time.
 */
final class FrameVeil {

    /** Covers the whole document when the graphics has no clip (an off-screen image in tests). */
    private static final double UNCLIPPED = 1_000_000;

    private FrameVeil() {
    }

    /** {@code inside} is the rectangle left clear, in document pixels. */
    static void paint(Graphics2D graphics, Rectangle2D inside, ColorScheme scheme) {
        Rectangle clip = graphics.getClipBounds();
        Rectangle2D visible = clip != null ? clip
                : new Rectangle2D.Double(-UNCLIPPED, -UNCLIPPED, 2 * UNCLIPPED, 2 * UNCLIPPED);
        Area veiled = new Area(visible);
        veiled.subtract(new Area(inside));
        graphics.setColor(scheme.frameVeil());
        graphics.fill(veiled);
    }
}
