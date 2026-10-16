package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.paint.ComponentDrawing;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.util.Optional;

/**
 * Where label text goes, in document coordinates (8 px per grid unit at zoom 1). Painting, hit testing and
 * circuit bounds all use these numbers. Works without a screen.
 */
public final class LabelLayout {

    public static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
    /** Space between a component's body and its label, in document pixels. */
    public static final double GAP = 3;

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final FontRenderContext RENDER_CONTEXT = new FontRenderContext(null, true, true);

    private LabelLayout() {
    }

    /** One line of text whose box starts at {@code left}/{@code top}; draw it at the baseline. */
    public record PlacedText(String text, double left, double top, double width, double height, double ascent) {

        public Rectangle2D bounds() {
            return new Rectangle2D.Double(left, top, width, height);
        }

        public float baselineX() {
            return (float) left;
        }

        public float baselineY() {
            return (float) (top + ascent);
        }
    }

    /** A free text label: its position is the top-left corner; at least one grid unit wide. */
    public static PlacedText freeText(TextLabel label) {
        PlacedText measured = measure(label.text(), label.position().x() * UNIT, label.position().y() * UNIT);
        return new PlacedText(measured.text(), measured.left(), measured.top(), Math.max(UNIT, measured.width()),
                measured.height(), measured.ascent());
    }

    /** A component's label: above the body for R0/R180, left of it for R90/R270; empty for a blank label. */
    public static Optional<PlacedText> componentLabel(ComponentType type, Component component) {
        if (component.label().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(componentLabelPlace(type, component, component.label()));
    }

    /**
     * Where {@code text} would be drawn as the label of {@code component}: what {@link #componentLabel} gives for a
     * label with that text, also for an empty one (a zero-width box where the label would start).
     */
    public static PlacedText componentLabelPlace(ComponentType type, Component component, String text) {
        Rectangle2D body = ComponentDrawing.documentBounds(type, component);
        PlacedText measured = measure(text, 0, 0);
        boolean sideways = component.rotation() == Rotation.R90 || component.rotation() == Rotation.R270;
        double left = sideways ? body.getMinX() - GAP - measured.width()
                : body.getCenterX() - measured.width() / 2;
        double top = sideways ? body.getCenterY() - measured.height() / 2
                : body.getMinY() - GAP - measured.height();
        return new PlacedText(measured.text(), left, top, measured.width(), measured.height(), measured.ascent());
    }

    /** Bounds of a free text label in grid units. */
    public static Rectangle2D gridBounds(TextLabel label) {
        Rectangle2D bounds = freeText(label).bounds();
        return new Rectangle2D.Double(bounds.getX() / UNIT, bounds.getY() / UNIT,
                bounds.getWidth() / UNIT, bounds.getHeight() / UNIT);
    }

    private static PlacedText measure(String text, double left, double top) {
        LineMetrics metrics = FONT.getLineMetrics(text.isEmpty() ? " " : text, RENDER_CONTEXT);
        double width = text.isEmpty() ? 0 : FONT.getStringBounds(text, RENDER_CONTEXT).getWidth();
        return new PlacedText(text, left, top, width, metrics.getAscent() + metrics.getDescent(), metrics.getAscent());
    }
}
