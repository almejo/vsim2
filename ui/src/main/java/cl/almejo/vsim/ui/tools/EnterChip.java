package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Optional;

/**
 * The «Entrar ↘» chip (spec §4.4): with the select tool idle, a user-component instance under the pointer shows a small
 * chip at its top-right corner; a click on it enters the component. The chip keeps a constant size on screen and
 * overlaps the corner a little, so the pointer can move from the instance onto it without the chip going away.
 */
final class EnterChip {

    /** Sizes in screen pixels, divided by the zoom where the chip is drawn in document pixels. */
    private static final float FONT_SIZE = 11f;
    private static final double PADDING = 5;
    private static final double HEIGHT = 16;
    private static final double OVERLAP = 6;
    private static final double ARC = 8;
    private static final FontRenderContext MEASURE = new FontRenderContext(null, true, true);

    /** The instance whose chip shows; empty when none does. */
    private Optional<Component> hovered = Optional.empty();

    /** Follows the pointer; true when the chip appeared, moved to another instance or went (a repaint is due). */
    boolean hover(ToolContext context, CanvasMouseEvent event) {
        Optional<Component> next = instanceAt(context, event.gridPosition())
                // the pointer went from the instance onto its chip, which sticks out of it, over ground that is not the
                // drawn shape (a custom shape need not fill its extent) or even the extent
                .or(() -> hovered.filter(instance -> insideReach(context, instance, event.gridPosition())));
        boolean changed = !next.map(Component::id).equals(hovered.map(Component::id));
        hovered = next;
        return changed;
    }

    /** The pointer left the canvas, or a gesture started: no chip; true when one was shown. */
    boolean clear() {
        boolean shown = hovered.isPresent();
        hovered = Optional.empty();
        return shown;
    }

    /** A primary press on the chip enters its instance; true when it did (the press is used up). */
    boolean pressed(ToolContext context, CanvasMouseEvent event) {
        Optional<Component> instance = current(context)
                .filter(shown -> event.button() == CanvasMouseEvent.PRIMARY)
                .filter(shown -> contains(context, shown, event.gridPosition()));
        if (instance.isEmpty()) {
            return false;
        }
        clear();
        context.repaint();
        context.enterComponent(instance.get());
        return true;
    }

    /** True when the chip is shown and {@code gridPosition} is on it: a press there enters, so the pointer is a hand. */
    boolean isUnder(ToolContext context, Point2D gridPosition) {
        return current(context).filter(shown -> contains(context, shown, gridPosition)).isPresent();
    }

    /** The chip now, in document pixels; empty when none is shown. */
    Optional<Rectangle2D> bounds(ToolContext context) {
        return current(context).map(instance -> chipOf(context, instance));
    }

    void paint(Graphics2D graphics, ToolContext context) {
        Optional<Component> shown = current(context);
        if (shown.isEmpty()) {
            return;
        }
        double zoom = context.viewport().zoom();
        Rectangle2D chip = chipOf(context, shown.get());
        ColorScheme scheme = context.scheme();
        Graphics2D local = (Graphics2D) graphics.create();
        try {
            local.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            local.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            local.setColor(scheme.selection());
            local.fill(new RoundRectangle2D.Double(chip.getX(), chip.getY(), chip.getWidth(), chip.getHeight(),
                    ARC / zoom, ARC / zoom));
            Font font = chipFont().deriveFont((float) (FONT_SIZE / zoom));
            local.setFont(font);
            local.setColor(scheme.background());
            LineMetrics metrics = font.getLineMetrics(text(), local.getFontRenderContext());
            double baseline = chip.getCenterY() + (metrics.getAscent() - metrics.getDescent()) / 2;
            local.drawString(text(), (float) (chip.getX() + PADDING / zoom), (float) baseline);
        } finally {
            local.dispose();
        }
    }

    /** The hovered instance, while it is still in the document and still enterable. */
    private Optional<Component> current(ToolContext context) {
        return hovered.flatMap(instance -> context.openDocument().document().component(instance.id()))
                .filter(component -> isEnterable(context, component));
    }

    private static Optional<Component> instanceAt(ToolContext context, Point2D gridPosition) {
        Optional<DocumentElement> hit = context.hitTester().elementAt(context.openDocument().document(), gridPosition);
        return hit.filter(Component.class::isInstance).map(Component.class::cast)
                .filter(component -> isEnterable(context, component));
    }

    private static boolean isEnterable(ToolContext context, Component component) {
        return EnterableInstances.definitionOf(context.openDocument(), component).isPresent();
    }

    private static boolean contains(ToolContext context, Component instance, Point2D gridPosition) {
        return chipOf(context, instance).contains(gridPosition.getX() * GridPoint.PIXELS_PER_UNIT,
                gridPosition.getY() * GridPoint.PIXELS_PER_UNIT);
    }

    /**
     * True when {@code gridPosition} is in the convex hull of the instance's extent and its chip: the chip reaches only a
     * few pixels into the extent's corner, so a pointer heading for it in a straight line from the drawn shape leaves
     * the extent beside the chip; inside the hull every such line stays.
     */
    private static boolean insideReach(ToolContext context, Component instance, Point2D gridPosition) {
        Bounds box = ElementBounds.of(instance, context.openDocument().registry());
        double unit = GridPoint.PIXELS_PER_UNIT;
        Rectangle2D chip = chipOf(context, instance);
        double[][] corners = {
                {box.x() * unit, box.y() * unit}, {box.x() * unit, box.maximumY() * unit},
                {box.maximumX() * unit, box.y() * unit}, {box.maximumX() * unit, box.maximumY() * unit},
                {chip.getMinX(), chip.getMinY()}, {chip.getMinX(), chip.getMaxY()},
                {chip.getMaxX(), chip.getMinY()}, {chip.getMaxX(), chip.getMaxY()}};
        double pointX = gridPosition.getX() * unit;
        double pointY = gridPosition.getY() * unit;
        // an edge of the hull has every corner on one side; the point is inside when it is on that side of all of them
        for (int first = 0; first < corners.length; first++) {
            for (int second = 0; second < corners.length; second++) {
                if (first != second && isHullEdge(corners, first, second)) {
                    double side = (corners[second][0] - corners[first][0]) * (pointY - corners[first][1])
                            - (corners[second][1] - corners[first][1]) * (pointX - corners[first][0]);
                    if (side < 0) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** True when every corner is on or to the left of the line from {@code first} to {@code second}, and not all on it. */
    private static boolean isHullEdge(double[][] corners, int first, int second) {
        boolean someOff = false;
        for (double[] corner : corners) {
            double side = (corners[second][0] - corners[first][0]) * (corner[1] - corners[first][1])
                    - (corners[second][1] - corners[first][1]) * (corner[0] - corners[first][0]);
            if (side < 0) {
                return false;
            }
            someOff |= side > 0;
        }
        return someOff;
    }

    /** Over the instance's top-right corner, reaching up and right out of it, at a constant size on screen. */
    private static Rectangle2D chipOf(ToolContext context, Component instance) {
        double zoom = context.viewport().zoom();
        Bounds box = ElementBounds.of(instance, context.openDocument().registry());
        double right = (box.x() + box.width()) * (double) GridPoint.PIXELS_PER_UNIT;
        double top = box.y() * (double) GridPoint.PIXELS_PER_UNIT;
        double width = (chipFont().getStringBounds(text(), MEASURE).getWidth() + 2 * PADDING) / zoom;
        double height = HEIGHT / zoom;
        double overlap = OVERLAP / zoom;
        return new Rectangle2D.Double(right - overlap, top - height + overlap, width, height);
    }

    private static Font chipFont() {
        return new Font(Font.DIALOG, Font.BOLD, Math.round(FONT_SIZE));
    }

    private static String text() {
        return Messages.text("canvas.enterChip");
    }
}
