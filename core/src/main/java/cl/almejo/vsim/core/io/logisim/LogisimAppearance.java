package cl.almejo.vsim.core.io.logisim;

import java.util.List;
import java.util.Objects;

/**
 * A circuit's {@code <appear>}: how Logisim draws an instance of it, as the raw shapes in Logisim's pixels (the
 * subcircuit wave turns them into a VSim symbol). Empty when the circuit has the default box.
 */
public record LogisimAppearance(List<Shape> shapes) {

    /** A circuit without its own appearance. */
    public static final LogisimAppearance NONE = new LogisimAppearance(List.of());

    public LogisimAppearance {
        shapes = List.copyOf(shapes);
    }

    /** One element of {@code <appear>}. */
    public sealed interface Shape permits Rectangle, Oval, Line, Polyline, Curve, Text, Port, Anchor {
    }

    /**
     * How a shape is painted: SVG colours as written ({@code #000000}, {@code none} when it has no stroke or no fill)
     * and the stroke width (1 when the file gives none).
     */
    public record Paint(String stroke, String fill, int strokeWidth) {
        public Paint {
            Objects.requireNonNull(stroke, "stroke");
            Objects.requireNonNull(fill, "fill");
        }
    }

    /** {@code <rect>}; {@code cornerRadius} is its {@code rx}, 0 for square corners. */
    public record Rectangle(int x, int y, int width, int height, int cornerRadius, Paint paint) implements Shape {
    }

    /** {@code <ellipse>}, by its bounding box (Logisim rounds the centre and radii to whole pixels). */
    public record Oval(int x, int y, int width, int height, Paint paint) implements Shape {
    }

    /** {@code <line>}. */
    public record Line(LogisimPoint from, LogisimPoint to, Paint paint) implements Shape {
    }

    /** {@code <polyline>}, or {@code <polygon>} when {@code closed}. */
    public record Polyline(boolean closed, List<LogisimPoint> points, Paint paint) implements Shape {
        public Polyline {
            points = List.copyOf(points);
        }
    }

    /** A quadratic curve, {@code <path d="M from Q control to">}, the only path Logisim writes. */
    public record Curve(LogisimPoint from, LogisimPoint to, LogisimPoint control, Paint paint) implements Shape {
    }

    /** Where a text's {@code (x, y)} is on its baseline: SVG's {@code text-anchor}. */
    public enum TextAnchor { START, MIDDLE, END }

    /** {@code <text>}: {@code (x, y)} on the baseline, at {@code anchor}; {@code color} is its fill. */
    public record Text(int x, int y, String text, LogisimFont font, TextAnchor anchor, String color) implements Shape {
    }

    /**
     * {@code <circ-port>}: the circuit's pin at {@code pin} (a point inside the circuit) shows on the symbol in the box
     * {@code (x, y, width, height)}; its connection is the box's centre.
     */
    public record Port(LogisimPoint pin, int x, int y, int width, int height) implements Shape {
    }

    /**
     * {@code <circ-anchor>}: the symbol's point that lands on the instance's location, in the box
     * {@code (x, y, width, height)} (its centre), and which way the symbol faces.
     */
    public record Anchor(int x, int y, int width, int height, String facing) implements Shape {
    }
}
