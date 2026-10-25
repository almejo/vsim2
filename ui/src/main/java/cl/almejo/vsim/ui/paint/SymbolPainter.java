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
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    /** The space between the box edge and a pin name drawn outside it. */
    private static final double OUTSIDE_GAP = 2;
    private static final float[] NAME_SIZE_STEPS = {13f, 9f, 6f, 5f};
    private static final double LINE_HEIGHT = 1.2;
    private static final String ELLIPSIS = "\u2026";

    private SymbolPainter() { }

    public static void paint(Graphics2D graphics, ComponentType type, SymbolDrawing drawing, ColorScheme scheme,
                             String componentName) {
        paint(graphics, type, drawing, scheme, componentName, STROKE_WIDTH, 1);
    }

    /**
     * Paints with a given stroke width and pin marker scale (both in local pixels): a zoomed view passes
     * {@code 1.6 / zoom} and {@code 1 / zoom} to keep strokes and pin dots at a constant size on screen.
     */
    public static void paint(Graphics2D graphics, ComponentType type, SymbolDrawing drawing, ColorScheme scheme,
                             String componentName, float strokeWidth, double pinScale) {
        Graphics2D symbol = (Graphics2D) graphics.create();
        try {
            symbol.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (SymbolElement element : drawing.elements()) {
                if (element instanceof SymbolPath path) {
                    Shape shape = insetByHalfTheStroke(path(path), drawing, strokeWidth);
                    if (path.filled()) {
                        symbol.setColor(scheme.componentFill());
                        symbol.fill(shape);
                    }
                    symbol.setColor(scheme.componentStroke());
                    symbol.draw(shape);
                } else if (element instanceof SymbolText text) {
                    paintText(symbol, text, componentName, type, drawing, scheme, strokeWidth);
                }
            }
            for (PinSpec pin : type.pins()) {
                paintPin(symbol, pin, type.pins(), drawing, scheme, strokeWidth);
            }
            PainterSupport.pinDots(symbol, type, scheme, pinScale);
        } finally {
            symbol.dispose();
        }
    }

    /**
     * The path squeezed into the box by half the stroke width, so a figure on the box edge is drawn inside it and its
     * stroke never spills past the type bounds (the selection outline and exports crop at those bounds).
     */
    private static Shape insetByHalfTheStroke(Shape shape, SymbolDrawing drawing, float strokeWidth) {
        double inset = strokeWidth / 2;
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
        if (symbol.closed()) {
            shape.closePath();
        }
        return shape;
    }

    /** The part of a text, from its left end, that sits on the anchor. */
    private static double anchorFraction(TextAlignment alignment) {
        return switch (alignment) {
            case LEFT -> PainterSupport.ALIGN_LEFT;
            case CENTER -> PainterSupport.ALIGN_CENTER;
            case RIGHT -> PainterSupport.ALIGN_RIGHT;
        };
    }

    private static void paintText(Graphics2D graphics, SymbolText text, String name, ComponentType type,
                                  SymbolDrawing drawing, ColorScheme scheme, float strokeWidth) {
        if (text.text().isEmpty()) {
            paintName(graphics, text, name, type, drawing, scheme, strokeWidth);
            return;
        }
        String shown = text.text().get();
        float size = switch (text.size()) {
            case SMALL -> 6f;
            case MEDIUM -> 9f;
            case LARGE -> 13f;
        };
        double anchorX = text.anchor().x() * COORDINATE_SCALE;
        double anchorY = text.anchor().y() * COORDINATE_SCALE;
        boolean sideways = PainterSupport.turnedSideways(graphics);
        double available = (sideways ? drawing.size().height() : drawing.size().width()) * UNIT - 2;
        Font font = TextFonts.labelFont().deriveFont(Font.BOLD);
        FontMetrics metrics = graphics.getFontMetrics(font.deriveFont(size));
        if (metrics.stringWidth(shown) > available) {
            size = (float) Math.max(3, size * available / Math.max(1, metrics.stringWidth(shown)));
        }
        upright(graphics, shown, font, anchorX, anchorY, size,
                anchorFraction(text.alignment()), scheme,
                new Rectangle2D.Double(0, 0, drawing.size().width() * UNIT, drawing.size().height() * UNIT));
    }

    /**
     * The component's name, as large as the free area of the shape allows: inside the closed figure it sits in, clear
     * of every pin name. It shrinks through the size steps and then loses letters (ending in an ellipsis) rather than
     * run over an outline or a pin name; when not even that fits, nothing is drawn.
     */
    private static void paintName(Graphics2D graphics, SymbolText text, String name, ComponentType type,
                                  SymbolDrawing drawing, ColorScheme scheme, float strokeWidth) {
        double anchorX = text.anchor().x() * COORDINATE_SCALE;
        double anchorY = text.anchor().y() * COORDINATE_SCALE;
        boolean sideways = PainterSupport.turnedSideways(graphics);
        double alignment = sideways ? 0.5 : anchorFraction(text.alignment());
        Font font = TextFonts.labelFont().deriveFont(Font.BOLD);
        double width = drawing.size().width() * UNIT;
        double height = drawing.size().height() * UNIT;
        Rectangle2D box = new Rectangle2D.Double(1, 1, width - 2, height - 2);
        List<Area> bodies = new ArrayList<>();
        for (SymbolPath path : drawing.paths()) {
            Shape shape = insetByHalfTheStroke(path(path), drawing, strokeWidth);
            if (path.closed() && shape.contains(anchorX, anchorY)) {
                bodies.add(new Area(shape));
            }
        }
        List<Rectangle2D> obstacles = new ArrayList<>();
        if (drawing.showPinNames()) {
            for (PinSpec pin : type.pins()) {
                obstacles.add(pinNameArea(graphics, pin, type.pins(), drawing, strokeWidth));
            }
        }
        float nominal = switch (text.size()) {
            case SMALL -> 6f;
            case MEDIUM -> 9f;
            case LARGE -> 13f;
        };
        float smallest = nominal;
        for (float step : NAME_SIZE_STEPS) {
            if (step > nominal) {
                continue;
            }
            smallest = step;
            Rectangle2D area = textArea(graphics, name, font, step, anchorX, anchorY, alignment, sideways);
            if (isFree(area, box, bodies, obstacles)) {
                upright(graphics, name, font, anchorX, anchorY, step, alignment, scheme, box);
                return;
            }
        }
        for (int length = name.length() - 1; length > 0; length--) {
            String elided = name.substring(0, length).stripTrailing() + ELLIPSIS;
            Rectangle2D area = textArea(graphics, elided, font, smallest, anchorX, anchorY, alignment, sideways);
            if (isFree(area, box, bodies, obstacles)) {
                upright(graphics, elided, font, anchorX, anchorY, smallest, alignment, scheme, box);
                return;
            }
        }
    }

    private static boolean isFree(Rectangle2D area, Rectangle2D box, List<Area> bodies, List<Rectangle2D> obstacles) {
        Rectangle2D margin = new Rectangle2D.Double(area.getX() - 1, area.getY() - 1, area.getWidth() + 2,
                area.getHeight() + 2);
        if (!box.contains(area)) {
            return false;
        }
        for (Area body : bodies) {
            if (!body.contains(margin)) {
                return false;
            }
        }
        for (Rectangle2D obstacle : obstacles) {
            if (obstacle.intersects(margin)) {
                return false;
            }
        }
        return true;
    }

    /** The local rectangle a text takes, as {@link PainterSupport#uprightText} will place it. */
    private static Rectangle2D textArea(Graphics2D graphics, String text, Font font, float size, double anchorX,
                                        double anchorY, double alignment, boolean sideways) {
        double width = graphics.getFontMetrics(font.deriveFont(size)).getStringBounds(text, graphics).getWidth();
        double half = size * LINE_HEIGHT / 2;
        return sideways
                ? new Rectangle2D.Double(anchorX - half, anchorY - width / 2, 2 * half, width)
                : new Rectangle2D.Double(anchorX - width * alignment, anchorY - half, width, 2 * half);
    }

    private static void paintPin(Graphics2D graphics, PinSpec pin, List<PinSpec> pins, SymbolDrawing drawing,
                                 ColorScheme scheme, float strokeWidth) {
        double x = pin.position().x() * UNIT;
        double y = pin.position().y() * UNIT;
        double width = drawing.size().width() * UNIT;
        double height = drawing.size().height() * UNIT;
        double horizontal = Math.min(x, width - x);
        double vertical = Math.min(y, height - y);
        double directionX = horizontal <= vertical ? (x <= width / 2 ? 1 : -1) : 0;
        double directionY = horizontal <= vertical ? 0 : (y <= height / 2 ? 1 : -1);
        double length = stubLength(drawing, x, y, directionX, directionY, strokeWidth);
        graphics.setColor(scheme.componentStroke());
        graphics.draw(new Line2D.Double(x, y, x + directionX * length, y + directionY * length));
        if (drawing.showPinNames()) {
            visiblePinName(graphics, pin, pins, drawing, length, strokeWidth).ifPresent(name ->
                    upright(graphics, name.text(), TextFonts.labelFont(), name.anchorX(), name.anchorY(), name.size(),
                            name.alignment(), scheme, name.slot()));
        }
    }

    /**
     * The name to draw for a pin: a side pin's name always (its slot keeps it off the neighbours). A top or bottom
     * pin's name goes only where it overlaps neither the outline nor a side pin's name, shrinking to the smallest
     * size first; when it cannot, it is not drawn (the pin table and the pin's tooltip still name it).
     */
    private static Optional<PinName> visiblePinName(Graphics2D graphics, PinSpec pin, List<PinSpec> pins,
                                                    SymbolDrawing drawing, double stubLength, float strokeWidth) {
        if (isOnSide(pin, drawing)) {
            return Optional.of(pinName(graphics, pin, pins, drawing, stubLength, PIN_NAME_SIZE));
        }
        List<Area> obstacles = new ArrayList<>();
        for (SymbolPath path : drawing.paths()) {
            obstacles.add(new Area(new BasicStroke(strokeWidth + 1f)
                    .createStrokedShape(insetByHalfTheStroke(path(path), drawing, strokeWidth))));
        }
        boolean sideways = PainterSupport.turnedSideways(graphics);
        List<Rectangle2D> sideNames = new ArrayList<>();
        for (PinSpec other : pins) {
            if (other != pin && isOnSide(other, drawing)) {
                sideNames.add(pinNameArea(graphics, other, pins, drawing, strokeWidth));
            }
        }
        for (float limit = PIN_NAME_SIZE; limit >= MINIMUM_NAME_SIZE; limit -= 1) {
            PinName name = pinName(graphics, pin, pins, drawing, stubLength, limit);
            Rectangle2D area = textArea(graphics, name.text(), TextFonts.labelFont(), name.size(), name.anchorX(),
                    name.anchorY(), name.alignment(), sideways).createIntersection(name.slot());
            boolean clear = obstacles.stream().noneMatch(outline -> outline.intersects(area))
                    && sideNames.stream().noneMatch(area::intersects);
            if (clear) {
                return Optional.of(name);
            }
        }
        return Optional.of(outsideName(graphics, pin, pins, drawing));
    }

    /**
     * A top or bottom pin's name that has no clear room inside the box: drawn outside, past the box edge next to its
     * pin, at a size that fits between the neighbouring pins on that edge. It is never hidden.
     */
    private static PinName outsideName(Graphics2D graphics, PinSpec pin, List<PinSpec> pins, SymbolDrawing drawing) {
        double x = pin.position().x() * UNIT;
        double y = pin.position().y() * UNIT;
        double height = drawing.size().height() * UNIT;
        double outward = y <= height / 2 ? -1 : 1;
        boolean sideways = PainterSupport.turnedSideways(graphics);
        double gap = gapToNeighbours(pin, pins, false, drawing.size().width() * UNIT);
        String text = SubcircuitPainter.pinText(pin);
        float size = Math.min(PIN_NAME_SIZE, fittedSize(graphics, text, sideways ? PIN_NAME_SIZE * 4 : gap - 2));
        FontMetrics metrics = graphics.getFontMetrics(TextFonts.labelFont().deriveFont(size));
        // Upright on screen, so when the box is turned a quarter the text's width runs along the box's local y axis.
        double extent = sideways ? metrics.stringWidth(text) / 2.0 : metrics.getAscent() / 2.0;
        double anchorY = y + outward * (OUTSIDE_GAP + extent);
        double margin = OUTSIDE_GAP + 2 * extent + 4;
        Rectangle2D slot = new Rectangle2D.Double(x - Math.max(gap, UNIT) / 2, -margin,
                Math.max(gap, UNIT), height + 2 * margin);
        return new PinName(text, size, x, anchorY, 0.5, slot);
    }

    private static boolean isOnSide(PinSpec pin, SymbolDrawing drawing) {
        double x = pin.position().x() * UNIT;
        double y = pin.position().y() * UNIT;
        return Math.min(x, drawing.size().width() * UNIT - x) <= Math.min(y, drawing.size().height() * UNIT - y);
    }

    /** A pin's name as laid out inside the box: its text, size, anchor, alignment and the slot it is clipped to. */
    private record PinName(String text, float size, double anchorX, double anchorY, double alignment,
                           Rectangle2D slot) { }

    /**
     * The pin's name inside the box next to its stub, upright in every rotation and fitted to its slot as the default
     * box does: the stretch between the midpoints to the neighbouring pins on the same edge, across the box. A name
     * that is still too long is cut at the slot, never run into the next pin's name.
     */
    private static PinName pinName(Graphics2D graphics, PinSpec pin, List<PinSpec> pins, SymbolDrawing drawing,
                                   double stubLength, float largestSize) {
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
        float size = Math.min(largestSize, fittedSize(graphics, text, across));
        double inset = Math.max(5, stubLength + 3);
        double alignment = sideways ? 0.5 : directionX > 0 ? 0 : directionX < 0 ? 1 : 0.5;
        Rectangle2D slot = onSide
                ? new Rectangle2D.Double(1, y - gap / 2, width - 2, gap)
                : new Rectangle2D.Double(x - gap / 2, 1, gap, height - 2);
        return new PinName(text, size, x + directionX * inset, y + directionY * inset, alignment,
                slot.createIntersection(new Rectangle2D.Double(1, 1, width - 2, height - 2)));
    }

    /** Where a pin's name ink can be: its text rectangle cut to its slot. */
    private static Rectangle2D pinNameArea(Graphics2D graphics, PinSpec pin, List<PinSpec> pins, SymbolDrawing drawing,
                                           float strokeWidth) {
        double x = pin.position().x() * UNIT;
        double y = pin.position().y() * UNIT;
        double width = drawing.size().width() * UNIT;
        double height = drawing.size().height() * UNIT;
        boolean onSide = Math.min(x, width - x) <= Math.min(y, height - y);
        double directionX = onSide ? (x <= width / 2 ? 1 : -1) : 0;
        double directionY = onSide ? 0 : (y <= height / 2 ? 1 : -1);
        PinName name = pinName(graphics, pin, pins, drawing,
                stubLength(drawing, x, y, directionX, directionY, strokeWidth), PIN_NAME_SIZE);
        Rectangle2D text = textArea(graphics, name.text(), TextFonts.labelFont(), name.size(), name.anchorX(),
                name.anchorY(), name.alignment(), PainterSupport.turnedSideways(graphics));
        return text.createIntersection(name.slot());
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

    private static double stubLength(SymbolDrawing drawing, double x, double y, double directionX, double directionY,
                                     float strokeWidth) {
        List<Shape> outlines = drawing.paths().stream().map(path -> new BasicStroke(strokeWidth + 0.5f)
                .createStrokedShape(insetByHalfTheStroke(path(path), drawing, strokeWidth))).toList();
        for (double distance = 0; distance <= 2 * UNIT; distance += 0.5) {
            Point2D point = new Point2D.Double(x + directionX * distance, y + directionY * distance);
            for (Shape outline : outlines) {
                if (outline.contains(point)) {
                    return distance;
                }
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
        } finally {
            upright.dispose();
        }
    }
}
