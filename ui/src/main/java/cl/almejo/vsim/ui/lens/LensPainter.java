package cl.almejo.vsim.ui.lens;

import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.PainterSupport;
import cl.almejo.vsim.ui.theme.ThemeColors;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

/**
 * Paints the accessibility lens on a pane (approved mockup v5, 2026-11-11): the pane's own scene enlarged around the
 * pointer and clipped to a circle or a rounded rectangle ({@link LensLook}), a ring and a faint crosshair at the centre in the
 * theme's accent, and the magnification («4×») under the ring's bottom right. Colours are read at every paint.
 */
final class LensPainter {

    private static final float RING_WIDTH = 2.5f;
    /** How far the ring's stroke reaches outside the circle, rounded up, plus a pixel of antialiasing. */
    private static final int RING_MARGIN = 3;
    private static final int CROSSHAIR_ARM = 7;
    /** The crosshair is faint: it marks the centre without hiding what is there. */
    private static final int CROSSHAIR_ALPHA = 140;
    private static final float LABEL_FONT_SIZE = 11f;
    /** The label's left edge, as a fraction of the radius right of the centre (the mockup's 0.62). */
    private static final double LABEL_X_FRACTION = 0.62;
    /** The label's baseline below the circle's bottom. */
    private static final int LABEL_BASELINE_BELOW = 15;
    /** Room for the label's text («4×») and its descent, generous for larger fonts. */
    private static final int LABEL_WIDTH = 48;
    private static final int LABEL_HEIGHT = 22;

    private LensPainter() {
    }

    /** The lens at {@code centre} (logical pixels of {@code pane}), showing it {@code factor} times larger. */
    static void paint(Graphics2D screen, CircuitCanvas pane, Point2D centre, double factor, LensLook look) {
        paintMagnifiedScene(screen, pane, centre, factor, look);
        Graphics2D chrome = (Graphics2D) screen.create();
        try {
            chrome.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            chrome.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color accent = ThemeColors.accent();
            paintRing(chrome, centre, accent, look);
            paintCrosshair(chrome, centre, accent);
            paintLabel(chrome, centre, factor, accent, look);
        } finally {
            chrome.dispose();
        }
    }

    /** Everything the lens may paint on, ring and label included: what a move of the lens repaints. */
    static Rectangle area(Point2D centre, LensLook look) {
        int reachX = (int) Math.ceil(look.halfWidth()) + RING_MARGIN;
        int reachY = (int) Math.ceil(look.halfHeight()) + RING_MARGIN;
        Rectangle outline = new Rectangle((int) Math.floor(centre.getX()) - reachX,
                (int) Math.floor(centre.getY()) - reachY, 2 * reachX + 1, 2 * reachY + 1);
        return outline.union(labelArea(centre, look));
    }

    /** Where the magnification label may paint, below the ring's bottom right. */
    static Rectangle labelArea(Point2D centre, LensLook look) {
        int left = (int) Math.floor(centre.getX() + look.halfWidth() * LABEL_X_FRACTION) - 1;
        int top = (int) Math.floor(centre.getY() + look.halfHeight()) + 1;
        return new Rectangle(left, top, LABEL_WIDTH, LABEL_HEIGHT);
    }

    private static void paintMagnifiedScene(Graphics2D screen, CircuitCanvas pane, Point2D centre, double factor,
                                             LensLook look) {
        Graphics2D inside = (Graphics2D) screen.create();
        try {
            inside.clip(look.outline(centre));
            pane.paintSceneMagnified(inside, centre, factor);
        } finally {
            inside.dispose();
        }
    }

    private static void paintRing(Graphics2D chrome, Point2D centre, Color accent, LensLook look) {
        chrome.setColor(accent);
        chrome.setStroke(new BasicStroke(RING_WIDTH));
        chrome.draw(look.outline(centre));
    }

    private static void paintCrosshair(Graphics2D chrome, Point2D centre, Color accent) {
        chrome.setColor(PainterSupport.withAlpha(accent, CROSSHAIR_ALPHA));
        chrome.setStroke(new BasicStroke(1f));
        double x = centre.getX();
        double y = centre.getY();
        chrome.draw(new Line2D.Double(x - CROSSHAIR_ARM, y, x + CROSSHAIR_ARM, y));
        chrome.draw(new Line2D.Double(x, y - CROSSHAIR_ARM, x, y + CROSSHAIR_ARM));
    }

    private static void paintLabel(Graphics2D chrome, Point2D centre, double factor, Color accent,
                                  LensLook look) {
        chrome.setColor(accent);
        chrome.setFont(chrome.getFont().deriveFont(Font.BOLD, LABEL_FONT_SIZE));
        String text = Messages.format("lens.zoomLabel", (int) Math.round(factor));
        float x = (float) (centre.getX() + look.halfWidth() * LABEL_X_FRACTION);
        float baseline = (float) (centre.getY() + look.halfHeight() + LABEL_BASELINE_BELOW);
        chrome.drawString(text, x, baseline);
    }
}
