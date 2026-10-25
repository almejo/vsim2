package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.appearance.SymbolDrawing;
import cl.almejo.vsim.core.component.appearance.SymbolElement;
import cl.almejo.vsim.core.component.appearance.SymbolNode;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolText;
import cl.almejo.vsim.core.component.appearance.TextAlignment;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Shared ordered symbol rendering for the circuit, shape editor, palette and vector exports. */
public final class SymbolPainter {
    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final double COORDINATE_SCALE = UNIT / SymbolPoint.EIGHTHS;
    private static final Map<SymbolPath, Shape> PATHS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final float STROKE_WIDTH = 1.6f;
    private static final float PIN_NAME_SIZE = 6f;
    private static final float MINIMUM_NAME_SIZE = 4f;
    private static final double NAME_INSET = 4;

    private SymbolPainter() { }

    public static void paint(Graphics2D graphics, ComponentType type, SymbolDrawing drawing, ColorScheme scheme,
                             String componentName) {
        Graphics2D symbol = (Graphics2D) graphics.create();
        try {
            symbol.setStroke(new BasicStroke(STROKE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (SymbolElement element : drawing.elements()) {
                if (element instanceof SymbolPath path) {
                    Shape shape = insetByHalfTheStroke(path(path), drawing);
                    if (path.filled()) {
                        symbol.setColor(scheme.componentFill());
                        symbol.fill(shape);
                    }
                    symbol.setColor(scheme.componentStroke());
                    symbol.draw(shape);
                } else if (element instanceof SymbolText text) {
                    paintText(symbol, text, componentName, drawing, scheme);
                }
            }
            for (PinSpec pin : type.pins()) {
                paintPin(symbol, pin, type.pins(), drawing, scheme);
            }
            PainterSupport.pinDots(symbol, type, scheme);
        } finally {
            symbol.dispose();
        }
    }

    /**
     * The path squeezed into the box by half the stroke width, so a figure on the box edge is drawn inside it and its
     * stroke never spills past the type bounds (the selection outline and exports crop at those bounds).
     */
    private static Shape insetByHalfTheStroke(Shape shape, SymbolDrawing drawing) {
        double inset = STROKE_WIDTH / 2;
        double width = drawing.size().width() * UNIT;
        double height = drawing.size().height() * UNIT;
        AffineTransform transform = new AffineTransform();
        transform.translate(inset, inset);
        transform.scale((width - 2 * inset) / width, (height - 2 * inset) / height);
        return transform.createTransformedShape(shape);
    }

    /** The cached path in local document pixels; callers must not mutate it. */
    public static Shape path(SymbolPath symbol) {
        return PATHS.computeIfAbsent(symbol, SymbolPainter::buildPath);
    }

    private static Shape buildPath(SymbolPath symbol) {
        Path2D.Double shape = new Path2D.Double();
        SymbolPoint first = symbol.nodes().getFirst().point();
        shape.moveTo(first.x() * COORDINATE_SCALE, first.y() * COORDINATE_SCALE);
        int segments = symbol.nodes().size() - (symbol.closed() ? 0 : 1);
        for (int index = 0; index < segments; index++) {
            SymbolNode node = symbol.nodes().get(index);
            SymbolPoint end = symbol.nodes().get((index + 1) % symbol.nodes().size()).point();
            if (node.control().isPresent()) {
                SymbolPoint control = node.control().get();
                shape.quadTo(control.x() * COORDINATE_SCALE, control.y() * COORDINATE_SCALE,
                        end.x() * COORDINATE_SCALE, end.y() * COORDINATE_SCALE);
            } else {
                shape.lineTo(end.x() * COORDINATE_SCALE, end.y() * COORDINATE_SCALE);
            }
        }
        if (symbol.closed()) { shape.closePath(); }
        return shape;
    }

    private static void paintText(Graphics2D graphics, SymbolText text, String name, SymbolDrawing drawing,
                                  ColorScheme scheme) {
        String shown = text.text().orElse(name);
        float size = switch (text.size()) { case SMALL -> 6f; case MEDIUM -> 9f; case LARGE -> 13f; };
        double anchorX = text.anchor().x() * COORDINATE_SCALE;
        double anchorY = text.anchor().y() * COORDINATE_SCALE;
        boolean sideways = PainterSupport.turnedSideways(graphics);
        double available = (sideways ? drawing.size().height() : drawing.size().width()) * UNIT - 2;
        Font font = text.text().isEmpty() ? TextFonts.labelFont().deriveFont(Font.BOLD) : TextFonts.labelFont();
        FontMetrics metrics = graphics.getFontMetrics(font.deriveFont(size));
        if (metrics.stringWidth(shown) > available) {
            size = (float) Math.max(3, size * available / Math.max(1, metrics.stringWidth(shown)));
        }
        upright(graphics, shown, font, anchorX, anchorY, size,
                text.alignment() == TextAlignment.CENTER ? 0.5 : 0, scheme,
                new Rectangle2D.Double(0, 0, drawing.size().width() * UNIT, drawing.size().height() * UNIT));
    }

    private static void paintPin(Graphics2D graphics, PinSpec pin, List<PinSpec> pins, SymbolDrawing drawing,
                                 ColorScheme scheme) {
        double x = pin.position().x() * UNIT;
        double y = pin.position().y() * UNIT;
        double width = drawing.size().width() * UNIT;
        double height = drawing.size().height() * UNIT;
        double horizontal = Math.min(x, width - x);
        double vertical = Math.min(y, height - y);
        double directionX = horizontal <= vertical ? (x <= width / 2 ? 1 : -1) : 0;
        double directionY = horizontal <= vertical ? 0 : (y <= height / 2 ? 1 : -1);
        double length = stubLength(drawing, x, y, directionX, directionY);
        graphics.setColor(scheme.componentStroke());
        graphics.draw(new Line2D.Double(x, y, x + directionX * length, y + directionY * length));
        if (drawing.showPinNames()) {
            paintPinName(graphics, pin, pins, drawing, scheme, length);
        }
    }

    /**
     * The pin's name inside the box next to its stub, upright in every rotation and fitted to its slot as the default
     * box does: the stretch between the midpoints to the neighbouring pins on the same edge, across the box. A name
     * that is still too long is cut at the slot, never run into the next pin's name.
     */
    private static void paintPinName(Graphics2D graphics, PinSpec pin, List<PinSpec> pins, SymbolDrawing drawing,
                                     ColorScheme scheme, double stubLength) {
        double x = pin.position().x() * UNIT;
        double y = pin.position().y() * UNIT;
        double width = drawing.size().width() * UNIT;
        double height = drawing.size().height() * UNIT;
        boolean onSide = Math.min(x, width - x) <= Math.min(y, height - y);
        double directionX = onSide ? (x <= width / 2 ? 1 : -1) : 0;
        double directionY = onSide ? 0 : (y <= height / 2 ? 1 : -1);
        boolean sideways = PainterSupport.turnedSideways(graphics);
        double gap = gapToNeighbours(pin, pins, onSide, onSide ? height : width);
        boolean runsAlongTheSpacing = onSide == sideways;
        double across = runsAlongTheSpacing ? gap - 2 : (onSide ? width : height) - 2 * NAME_INSET - stubLength;
        String text = SubcircuitPainter.pinText(pin);
        float size = fittedSize(graphics, text, across);
        double inset = Math.max(5, stubLength + 3);
        double alignment = sideways ? 0.5 : directionX > 0 ? 0 : directionX < 0 ? 1 : 0.5;
        Rectangle2D slot = onSide
                ? new Rectangle2D.Double(1, y - gap / 2, width - 2, gap)
                : new Rectangle2D.Double(x - gap / 2, 1, gap, height - 2);
        upright(graphics, text, TextFonts.labelFont(), x + directionX * inset, y + directionY * inset, size, alignment,
                scheme, slot.createIntersection(new Rectangle2D.Double(1, 1, width - 2, height - 2)));
    }

    /** The distance to the nearest other pin on the same edge (the edge's length when there is none). */
    private static double gapToNeighbours(PinSpec pin, List<PinSpec> pins, boolean onSide, double edgeLength) {
        double gap = edgeLength;
        for (PinSpec other : pins) {
            boolean sameEdge = onSide ? other.position().x() == pin.position().x()
                    : other.position().y() == pin.position().y();
            double distance = Math.abs(onSide ? other.position().y() - pin.position().y()
                    : other.position().x() - pin.position().x()) * UNIT;
            if (other != pin && sameEdge && distance > 0) {
                gap = Math.min(gap, distance);
            }
        }
        return gap;
    }

    private static float fittedSize(Graphics2D graphics, String text, double across) {
        double textWidth = graphics.getFontMetrics(TextFonts.labelFont().deriveFont(PIN_NAME_SIZE)).stringWidth(text);
        return textWidth <= across ? PIN_NAME_SIZE
                : (float) Math.max(MINIMUM_NAME_SIZE, PIN_NAME_SIZE * across / textWidth);
    }

    private static double stubLength(SymbolDrawing drawing, double x, double y, double directionX, double directionY) {
        List<Shape> outlines = drawing.paths().stream().map(path -> new BasicStroke(STROKE_WIDTH + 0.5f)
                .createStrokedShape(insetByHalfTheStroke(path(path), drawing))).toList();
        for (double distance = 0; distance <= 2 * UNIT; distance += 0.5) {
            Point2D point = new Point2D.Double(x + directionX * distance, y + directionY * distance);
            for (Shape outline : outlines) {
                if (outline.contains(point)) { return distance; }
            }
        }
        return 0;
    }

    private static void upright(Graphics2D graphics, String text, Font font, double x, double y, float size,
                                double alignment, ColorScheme scheme, Shape clip) {
        Graphics2D upright = (Graphics2D) graphics.create();
        try {
            upright.clip(clip);
            PainterSupport.uprightText(upright, text, x, y, size, scheme.text(), alignment, font);
        } finally { upright.dispose(); }
    }
}
