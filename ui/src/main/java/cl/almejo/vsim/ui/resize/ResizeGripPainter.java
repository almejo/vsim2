package cl.almejo.vsim.ui.resize;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ComponentDrawing;
import cl.almejo.vsim.ui.paint.KeyboardPainter;
import cl.almejo.vsim.ui.paint.PainterSupport;
import cl.almejo.vsim.ui.paint.TextFonts;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Draws the «↔» (on a corner «⤡») tab of a selected resizable part and, while it is dragged, the tag above it
 * (Teclado mockup v4, {@code paintKeyboard}): the tab in the part's fill with a border and a double arrow in the selection colour, filled
 * with the selection colour while dragged; the tag on the part's fill with a border in the selection colour and a soft
 * shadow, upright whatever the part's rotation. Only the canvas overlay calls it, so exports, printing, the palette
 * and ghosts never show it. Colours are read from the scheme at each paint.
 */
final class ResizeGripPainter {

    private static final double TAB_RADIUS = 3;
    private static final float TAB_BORDER = 1f;
    /** How far the arrow's ends stay inside the tab, and how long its heads are. */
    private static final double ARROW_INSET = 3;
    private static final double ARROW_HEAD = 2;
    /** How far the diagonal «⤡» stays inside the corner tab, across and down. */
    private static final double DIAGONAL_INSET_X = 4;
    private static final double DIAGONAL_INSET_Y = 1.5;
    private static final float TAG_SIZE = 7f;
    private static final double TAG_PADDING = 5;
    private static final double TAG_HEIGHT = 11;
    private static final double TAG_GAP = 4;
    private static final double TAG_RADIUS = 3;
    private static final float TAG_BORDER = 0.8f;
    private static final int SHADOW_ALPHA = 40;
    /** The shadow drawn a few times, each a little wider, under the tag: a blur of about 4 px dropped 1 px. */
    private static final double[] SHADOW_SPREADS = {2, 1, 0};
    private static final double SHADOW_DROP = 1;

    private ResizeGripPainter() {
    }

    /** The tab of {@code component} (as shown: during a drag, at its new size), at {@code tab} in its own pixels. */
    static void paintTab(Graphics2D graphics, Component component, Rectangle2D tab, boolean dragging,
                         ColorScheme scheme) {
        paintTab(graphics, component, tab, false, dragging, scheme);
    }

    /** {@link #paintTab(Graphics2D, Component, Rectangle2D, boolean, ColorScheme)}; a corner's tab shows «⤡». */
    static void paintTab(Graphics2D graphics, Component component, Rectangle2D tab, boolean corner, boolean dragging,
                         ColorScheme scheme) {
        ComponentDrawing.paintPlaced(graphics, component, local -> {
            local.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D shape = new RoundRectangle2D.Double(tab.getX(), tab.getY(), tab.getWidth(),
                    tab.getHeight(), 2 * TAB_RADIUS, 2 * TAB_RADIUS);
            local.setColor(dragging ? scheme.selection() : scheme.componentFill());
            local.fill(shape);
            local.setColor(scheme.selection());
            local.setStroke(new BasicStroke(TAB_BORDER));
            local.draw(shape);
            local.setColor(dragging ? KeyboardPainter.textOn(scheme.selection()) : scheme.selection());
            local.draw(corner ? diagonalArrow(tab) : arrow(tab));
        });
    }

    /** «⤡»: a line from the tab's top left to its bottom right with a head at each end, its legs along the axes. */
    private static Path2D diagonalArrow(Rectangle2D tab) {
        double left = tab.getMinX() + DIAGONAL_INSET_X;
        double top = tab.getMinY() + DIAGONAL_INSET_Y;
        double right = tab.getMaxX() - DIAGONAL_INSET_X;
        double bottom = tab.getMaxY() - DIAGONAL_INSET_Y;
        Path2D arrow = new Path2D.Double();
        arrow.append(new Line2D.Double(left, top, right, bottom), false);
        arrow.moveTo(left + ARROW_HEAD, top);
        arrow.lineTo(left, top);
        arrow.lineTo(left, top + ARROW_HEAD);
        arrow.moveTo(right - ARROW_HEAD, bottom);
        arrow.lineTo(right, bottom);
        arrow.lineTo(right, bottom - ARROW_HEAD);
        return arrow;
    }

    /** A line along the tab's long side with a head at each end. */
    private static Path2D arrow(Rectangle2D tab) {
        Path2D arrow = new Path2D.Double();
        if (tab.getWidth() >= tab.getHeight()) {
            double middle = tab.getCenterY();
            double left = tab.getMinX() + ARROW_INSET;
            double right = tab.getMaxX() - ARROW_INSET;
            arrow.append(new Line2D.Double(left, middle, right, middle), false);
            arrow.moveTo(left + ARROW_HEAD, middle - ARROW_HEAD);
            arrow.lineTo(left, middle);
            arrow.lineTo(left + ARROW_HEAD, middle + ARROW_HEAD);
            arrow.moveTo(right - ARROW_HEAD, middle - ARROW_HEAD);
            arrow.lineTo(right, middle);
            arrow.lineTo(right - ARROW_HEAD, middle + ARROW_HEAD);
        } else {
            double middle = tab.getCenterX();
            double top = tab.getMinY() + ARROW_INSET;
            double bottom = tab.getMaxY() - ARROW_INSET;
            arrow.append(new Line2D.Double(middle, top, middle, bottom), false);
            arrow.moveTo(middle - ARROW_HEAD, top + ARROW_HEAD);
            arrow.lineTo(middle, top);
            arrow.lineTo(middle + ARROW_HEAD, top + ARROW_HEAD);
            arrow.moveTo(middle - ARROW_HEAD, bottom - ARROW_HEAD);
            arrow.lineTo(middle, bottom);
            arrow.lineTo(middle + ARROW_HEAD, bottom - ARROW_HEAD);
        }
        return arrow;
    }

    /**
     * The tag {@code text}, centred above {@code tabTop} (the top centre of the tab, in document pixels) and upright,
     * with a border in the selection colour and a shadow, unlike the part's own labels.
     */
    static void paintTag(Graphics2D graphics, String text, Point2D tabTop, ColorScheme scheme) {
        Graphics2D tag = (Graphics2D) graphics.create();
        try {
            tag.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            tag.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Font font = TextFonts.tagFont().deriveFont(Font.BOLD, TAG_SIZE);
            tag.setFont(font);
            FontMetrics metrics = tag.getFontMetrics();
            double width = metrics.stringWidth(text) + 2 * TAG_PADDING;
            double left = tabTop.getX() - width / 2;
            double top = tabTop.getY() - TAG_GAP - TAG_HEIGHT;
            for (double spread : SHADOW_SPREADS) {
                tag.setColor(PainterSupport.withAlpha(scheme.shadow(), SHADOW_ALPHA));
                tag.fill(new RoundRectangle2D.Double(left - spread, top - spread + SHADOW_DROP, width + 2 * spread,
                        TAG_HEIGHT + 2 * spread, 2 * (TAG_RADIUS + spread), 2 * (TAG_RADIUS + spread)));
            }
            RoundRectangle2D box = new RoundRectangle2D.Double(left, top, width, TAG_HEIGHT, 2 * TAG_RADIUS,
                    2 * TAG_RADIUS);
            tag.setColor(scheme.componentFill());
            tag.fill(box);
            tag.setColor(scheme.selection());
            tag.setStroke(new BasicStroke(TAG_BORDER));
            tag.draw(box);
            tag.setColor(scheme.text());
            double baseline = top + (TAG_HEIGHT + metrics.getAscent() - metrics.getDescent()) / 2;
            tag.drawString(text, (float) (left + TAG_PADDING), (float) baseline);
        } finally {
            tag.dispose();
        }
    }
}
