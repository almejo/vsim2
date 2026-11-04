package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.appearance.SymbolDrawing;
import cl.almejo.vsim.core.component.appearance.SymbolElement;
import cl.almejo.vsim.core.component.appearance.SymbolNode;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolText;
import cl.almejo.vsim.core.component.appearance.TextAlignment;
import cl.almejo.vsim.core.component.appearance.TextSize;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.io.logisim.ConvertedAppearance.ConvertedPin;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Anchor;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Curve;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Line;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Oval;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Paint;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Polyline;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Port;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Rectangle;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Shape;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Turns a Logisim circuit's {@code <appear>} (or, without one, Logisim's default box, {@link LogisimDefaultAppearance})
 * into a VSim custom component's look. Logisim's grid is 10 px and VSim's unit 8 px, so one Logisim pixel is 0.8 of
 * an eighth (a {@link SymbolPoint}): the box is the figures' and pins' bounds widened to whole units, every point is
 * scaled and rounded to the eighth, and each {@code circ-port} becomes the pin at its centre rounded to a whole unit,
 * moved to the nearest free point of the box edge when it is not on it (noted). Rectangles, polylines, polygons,
 * lines and Logisim's quadratic curves are exact; ovals and rounded corners become cubic curves. Texts keep their
 * alignment; their size is the nearest of VSim's three; they are always upright and bold, as VSim draws shape texts.
 * The colours go to the nearest of the notes' palette, black being the scheme's own colour. The circuit's label
 * ({@code clabel}) is a text in the middle, as Logisim draws it on any look.
 */
public final class LogisimAppearanceConverter {

    /** Logisim pixels per VSim grid unit. */
    private static final int PIXELS_PER_UNIT = 10;
    /** Eighths of a VSim unit per Logisim pixel. */
    private static final double EIGHTHS_PER_PIXEL = (double) SymbolPoint.EIGHTHS / PIXELS_PER_UNIT;
    /** Where the middle of a line of letters is above its baseline, as a part of the font size. */
    private static final double MIDDLE_ABOVE_BASELINE = 0.35;
    /** A cubic's handle length for a quarter of a circle, as a part of its radius. */
    private static final double QUARTER_CIRCLE = 0.5522847498;
    /** Logisim's anchor when a drawing has none . */
    private static final LogisimPoint DEFAULT_ANCHOR = new LogisimPoint(100, 100);
    private static final String DEFAULT_FACING = "east";
    /** The circuit label is upright when the top of its letters points north. */
    private static final String UPRIGHT_LABEL = "north";
    private static final String NO_PAINT = "none";
    private static final double TIE = 1e-9;
    /** A text moved by more than this, in eighths, to stay in the box is noted. */
    private static final int TEXT_MOVE_NOTED = SymbolPoint.EIGHTHS / 2;
    static final Map<NoteColor, int[]> PALETTE = palette();

    private LogisimAppearanceConverter() {
    }

    /** The look of {@code circuit}, with its {@code Pin}s and its label. */
    public static ConvertedAppearance convert(LogisimCircuit circuit) {
        return convert(circuit.name(), circuit.appearance(), LogisimInterfacePin.of(circuit), circuit.attributes());
    }

    /**
     * The look of the circuit called {@code circuit} (for the notes) drawn as {@code appearance} (empty: Logisim's
     * default box), with interface {@code pins} and the circuit's own attributes ({@code clabel}, {@code clabelup},
     * {@code clabelfont}).
     */
    public static ConvertedAppearance convert(String circuit, LogisimAppearance appearance,
                                              List<LogisimInterfacePin> pins, LogisimAttributes circuitAttributes) {
        List<Shape> shapes = appearance.shapes().isEmpty()
                ? LogisimDefaultAppearance.build(pins).shapes() : appearance.shapes();
        return new Conversion(circuit, shapes, pins, circuitAttributes).run();
    }

    /** One circuit being converted. */
    private static final class Conversion {
        private final String circuit;
        private final List<Shape> shapes;
        private final List<LogisimInterfacePin> pins;
        private final LogisimAttributes circuitAttributes;
        private final List<ImportNote> notes = new ArrayList<>();
        private final double[][] portCentres;
        private int left;
        private int top;
        private int width;
        private int height;

        Conversion(String circuit, List<Shape> shapes, List<LogisimInterfacePin> pins,
                   LogisimAttributes circuitAttributes) {
            this.circuit = circuit;
            this.shapes = shapes;
            this.pins = pins;
            this.circuitAttributes = circuitAttributes;
            this.portCentres = new double[pins.size()][];
        }

        ConvertedAppearance run() {
            matchPorts();
            Optional<Anchor> anchor = shapes.stream().filter(Anchor.class::isInstance).map(Anchor.class::cast)
                    .findFirst();
            double anchorX = anchor.map(found -> found.x() + found.width() / 2.0).orElse((double) DEFAULT_ANCHOR.x());
            double anchorY = anchor.map(found -> found.y() + found.height() / 2.0)
                    .orElse((double) DEFAULT_ANCHOR.y());
            String facing = anchor.map(Anchor::facing).filter(Conversion::isFacing).orElse(DEFAULT_FACING);
            box(anchorX, anchorY);
            List<ConvertedPin> placed = placePins();
            SymbolDrawing drawing = new SymbolDrawing(new GridSize(width, height), drawing(), false);
            GridPoint anchorPoint = new GridPoint((int) Math.round(anchorX / PIXELS_PER_UNIT) - left,
                    (int) Math.round(anchorY / PIXELS_PER_UNIT) - top);
            return new ConvertedAppearance(new GridSize(width, height), drawing, placed, anchorPoint,
                    facing.toLowerCase(Locale.ROOT), notes);
        }

        private static boolean isFacing(String text) {
            try {
                LogisimFacing.parse(text);
                return true;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

        /** Each port's centre, by the pin it names; a port naming no pin is noted and dropped. */
        private void matchPorts() {
            Map<LogisimPoint, Integer> byLocation = new HashMap<>();
            for (int index = 0; index < pins.size(); index++) {
                byLocation.putIfAbsent(pins.get(index).location(), index);
            }
            for (Shape shape : shapes) {
                if (shape instanceof Port port) {
                    Integer index = byLocation.get(port.pin());
                    if (index == null) {
                        notes.add(ImportNote.at(ImportNote.Kind.INVALID, circuit, port.pin(),
                                LogisimNotes.PORT_WITHOUT_PIN, port.pin().x() + "," + port.pin().y()));
                    } else if (portCentres[index] == null) {
                        portCentres[index] = new double[] {port.x() + port.width() / 2.0,
                                port.y() + port.height() / 2.0};
                    }
                }
            }
        }

        /** The box: the bounds of the figures and the ports in whole units; big enough for every pin on its edge. */
        private void box(double anchorX, double anchorY) {
            Bounds bounds = new Bounds();
            for (Shape shape : shapes) {
                switch (shape) {
                    case Rectangle rectangle -> bounds.add(rectangle.x(), rectangle.y())
                            .add(rectangle.x() + rectangle.width(), rectangle.y() + rectangle.height());
                    case Oval oval -> bounds.add(oval.x(), oval.y())
                            .add(oval.x() + oval.width(), oval.y() + oval.height());
                    case Line line -> bounds.add(line.from()).add(line.to());
                    case Polyline polyline -> polyline.points().forEach(bounds::add);
                    case Curve curve -> bounds.add(curve.from()).add(curve.to()).add(curve.control());
                    case Text text -> {
                        // texts may stick out of a VSim box: they never widen it
                    }
                    case Port port -> {
                        // the ports' centres are added below, matched to a pin or not
                    }
                    case Anchor ignored -> {
                        // the anchor may lie outside the box
                    }
                }
            }
            for (double[] centre : portCentres) {
                if (centre != null) {
                    bounds.add(centre[0], centre[1]);
                }
            }
            if (bounds.isEmpty()) {
                bounds.add(anchorX, anchorY);
            }
            left = (int) Math.floor(bounds.minimumX / PIXELS_PER_UNIT + TIE);
            top = (int) Math.floor(bounds.minimumY / PIXELS_PER_UNIT + TIE);
            width = Math.max(1, (int) Math.ceil(bounds.maximumX / PIXELS_PER_UNIT - TIE) - left);
            height = Math.max(1, (int) Math.ceil(bounds.maximumY / PIXELS_PER_UNIT - TIE) - top);
            while (2 * (width + height) < pins.size()) {
                height++;
            }
        }

        /**
         * Every pin on the box edge: first the ports already on it, then the others at the nearest free point (noted),
         * then the pins without a port, inputs on the left and outputs on the right, top to bottom (noted).
         */
        private List<ConvertedPin> placePins() {
            List<String> names = LogisimPinNames.unique(pins);
            GridPoint[] positions = new GridPoint[pins.size()];
            Set<GridPoint> taken = new HashSet<>();
            for (int index = 0; index < pins.size(); index++) {
                if (portCentres[index] != null) {
                    GridPoint rounded = new GridPoint((int) Math.round(scaledX(index)),
                            (int) Math.round(scaledY(index)));
                    if (onEdge(rounded) && taken.add(rounded)) {
                        positions[index] = rounded;
                    }
                }
            }
            for (int index = 0; index < pins.size(); index++) {
                if (portCentres[index] != null && positions[index] == null) {
                    positions[index] = nearestFree(scaledX(index), scaledY(index), taken);
                    taken.add(positions[index]);
                    notes.add(ImportNote.at(ImportNote.Kind.APPROXIMATED, circuit, pins.get(index).location(),
                            LogisimNotes.PORT_MOVED, names.get(index)));
                }
            }
            for (int index = 0; index < pins.size(); index++) {
                if (positions[index] == null) {
                    // just inside the top corner of the pin's side: the nearest free point is the first one down it
                    positions[index] = nearestFree(pins.get(index).output() ? width : 0, 0.5, taken);
                    taken.add(positions[index]);
                    notes.add(ImportNote.at(ImportNote.Kind.APPROXIMATED, circuit, pins.get(index).location(),
                            LogisimNotes.PIN_WITHOUT_PORT, names.get(index)));
                }
            }
            List<ConvertedPin> placed = new ArrayList<>();
            for (int index = 0; index < pins.size(); index++) {
                LogisimInterfacePin pin = pins.get(index);
                placed.add(new ConvertedPin(pin, new PinSpec(names.get(index), positions[index],
                        pin.output() ? PinDirection.OUTPUT : PinDirection.INPUT, pin.width())));
            }
            return placed;
        }

        private double scaledX(int index) {
            return portCentres[index][0] / PIXELS_PER_UNIT - left;
        }

        private double scaledY(int index) {
            return portCentres[index][1] / PIXELS_PER_UNIT - top;
        }

        private boolean onEdge(GridPoint point) {
            boolean inside = point.x() >= 0 && point.x() <= width && point.y() >= 0 && point.y() <= height;
            return inside && (point.x() == 0 || point.x() == width || point.y() == 0 || point.y() == height);
        }

        private boolean corner(GridPoint point) {
            return (point.x() == 0 || point.x() == width) && (point.y() == 0 || point.y() == height);
        }

        /**
         * The free edge point nearest to ({@code x}, {@code y}), corners last; between equally near points the one on
         * the side the point lies towards from the box's middle, then the topmost, leftmost.
         */
        private GridPoint nearestFree(double x, double y, Set<GridPoint> taken) {
            double towardsX = (x - width / 2.0) / width;
            double towardsY = (y - height / 2.0) / height;
            Comparator<GridPoint> order = Comparator
                    .comparing(this::corner)
                    .thenComparingDouble((GridPoint point) -> Math.round(
                            Math.hypot(point.x() - x, point.y() - y) / TIE) * TIE)
                    .thenComparingDouble(point -> -(outwardX(point) * towardsX + outwardY(point) * towardsY))
                    .thenComparingInt(GridPoint::y)
                    .thenComparingInt(GridPoint::x);
            return edgePoints().stream().filter(point -> !taken.contains(point)).min(order).orElseThrow();
        }

        private int outwardX(GridPoint point) {
            return point.x() == 0 ? -1 : point.x() == width ? 1 : 0;
        }

        private int outwardY(GridPoint point) {
            return point.y() == 0 ? -1 : point.y() == height ? 1 : 0;
        }

        private List<GridPoint> edgePoints() {
            List<GridPoint> points = new ArrayList<>();
            for (int x = 0; x <= width; x++) {
                points.add(new GridPoint(x, 0));
                points.add(new GridPoint(x, height));
            }
            for (int y = 1; y < height; y++) {
                points.add(new GridPoint(0, y));
                points.add(new GridPoint(width, y));
            }
            return points;
        }

        /** The figures and texts in Logisim's order (back to front), as many as VSim's drawings take. */
        private List<SymbolElement> drawing() {
            List<SymbolElement> elements = new ArrayList<>();
            Set<String> fonts = new LinkedHashSet<>();
            int paths = 0;
            int texts = 0;
            int dropped = 0;
            for (Shape shape : shapes) {
                Optional<SymbolElement> element;
                if (shape instanceof Text text) {
                    fontProblem(text.font()).ifPresent(fonts::add);
                    element = text(text).map(SymbolElement.class::cast);
                } else {
                    element = path(shape).map(SymbolElement.class::cast);
                }
                if (element.isEmpty()) {
                    continue;
                }
                boolean isText = element.get() instanceof SymbolText;
                if (isText ? texts >= SymbolDrawing.MAXIMUM_TEXTS : paths >= SymbolDrawing.MAXIMUM_PATHS) {
                    dropped++;
                    continue;
                }
                if (isText) {
                    texts++;
                } else {
                    paths++;
                }
                elements.add(element.get());
            }
            Optional<SymbolText> label = label();
            if (label.isPresent()) {
                if (texts < SymbolDrawing.MAXIMUM_TEXTS) {
                    elements.add(label.get());
                } else {
                    dropped++;
                }
            }
            fonts.forEach(font -> notes.add(ImportNote.about(ImportNote.Kind.APPROXIMATED, circuit,
                    LogisimNotes.TEXT_FONT, font)));
            if (dropped > 0) {
                notes.add(ImportNote.about(ImportNote.Kind.UNSUPPORTED, circuit, LogisimNotes.DRAWING_TOO_BIG,
                        Integer.toString(dropped)));
            }
            return elements;
        }

        private Optional<SymbolPath> path(Shape shape) {
            return switch (shape) {
                case Rectangle rectangle -> Optional.of(rectangle.cornerRadius() > 0
                        ? roundedRectangle(rectangle) : polygon(List.of(
                        new LogisimPoint(rectangle.x(), rectangle.y()),
                        new LogisimPoint(rectangle.x() + rectangle.width(), rectangle.y()),
                        new LogisimPoint(rectangle.x() + rectangle.width(), rectangle.y() + rectangle.height()),
                        new LogisimPoint(rectangle.x(), rectangle.y() + rectangle.height())), true,
                        rectangle.paint()));
                case Oval oval -> Optional.of(oval(oval));
                case Line line -> Optional.of(polygon(List.of(line.from(), line.to()), false, line.paint()));
                case Polyline polyline -> polyline(polyline);
                case Curve curve -> Optional.of(styled(List.of(
                        SymbolNode.quadratic(point(curve.from().x(), curve.from().y()),
                                point(curve.control().x(), curve.control().y())),
                        SymbolNode.straight(point(curve.to().x(), curve.to().y()))), false, curve.paint()));
                case Text ignored -> Optional.empty();
                case Port ignored -> Optional.empty();
                case Anchor ignored -> Optional.empty();
            };
        }

        private Optional<SymbolPath> polyline(Polyline polyline) {
            List<LogisimPoint> points = polyline.points();
            if (points.size() < 2) {
                return Optional.empty();
            }
            if (points.size() > SymbolPath.MAXIMUM_NODES) {
                notes.add(ImportNote.about(ImportNote.Kind.UNSUPPORTED, circuit, LogisimNotes.DRAWING_TOO_BIG,
                        Integer.toString(points.size() - SymbolPath.MAXIMUM_NODES)));
                points = points.subList(0, SymbolPath.MAXIMUM_NODES);
            }
            return Optional.of(polygon(points, polyline.closed() && points.size() >= 3, polyline.paint()));
        }

        private SymbolPath polygon(List<LogisimPoint> points, boolean closed, Paint paint) {
            return styled(points.stream().map(point -> SymbolNode.straight(point(point.x(), point.y()))).toList(),
                    closed, paint);
        }

        /** An ellipse as four cubic quarters, from its rightmost point clockwise (on screen). */
        private SymbolPath oval(Oval oval) {
            double centreX = oval.x() + oval.width() / 2.0;
            double centreY = oval.y() + oval.height() / 2.0;
            double radiusX = oval.width() / 2.0;
            double radiusY = oval.height() / 2.0;
            double handleX = radiusX * QUARTER_CIRCLE;
            double handleY = radiusY * QUARTER_CIRCLE;
            List<SymbolNode> nodes = List.of(
                    SymbolNode.cubic(point(centreX + radiusX, centreY), point(centreX + radiusX, centreY + handleY),
                            point(centreX + handleX, centreY + radiusY)),
                    SymbolNode.cubic(point(centreX, centreY + radiusY), point(centreX - handleX, centreY + radiusY),
                            point(centreX - radiusX, centreY + handleY)),
                    SymbolNode.cubic(point(centreX - radiusX, centreY), point(centreX - radiusX, centreY - handleY),
                            point(centreX - handleX, centreY - radiusY)),
                    SymbolNode.cubic(point(centreX, centreY - radiusY), point(centreX + handleX, centreY - radiusY),
                            point(centreX + radiusX, centreY - handleY)));
            return styled(nodes, true, oval.paint());
        }

        /** A rectangle with quarter-circle corners: four straight sides joined by four cubic corners. */
        private SymbolPath roundedRectangle(Rectangle rectangle) {
            double radius = Math.min(rectangle.cornerRadius(),
                    Math.min(rectangle.width(), rectangle.height()) / 2.0);
            double handle = radius * (1 - QUARTER_CIRCLE);
            double leftX = rectangle.x();
            double topY = rectangle.y();
            double rightX = leftX + rectangle.width();
            double bottomY = topY + rectangle.height();
            List<SymbolNode> nodes = List.of(
                    SymbolNode.straight(point(leftX + radius, topY)),
                    SymbolNode.cubic(point(rightX - radius, topY), point(rightX - handle, topY),
                            point(rightX, topY + handle)),
                    SymbolNode.straight(point(rightX, topY + radius)),
                    SymbolNode.cubic(point(rightX, bottomY - radius), point(rightX, bottomY - handle),
                            point(rightX - handle, bottomY)),
                    SymbolNode.straight(point(rightX - radius, bottomY)),
                    SymbolNode.cubic(point(leftX + radius, bottomY), point(leftX + handle, bottomY),
                            point(leftX, bottomY - handle)),
                    SymbolNode.straight(point(leftX, bottomY - radius)),
                    SymbolNode.cubic(point(leftX, topY + radius), point(leftX, topY + handle),
                            point(leftX + handle, topY)));
            return styled(nodes, true, rectangle.paint());
        }

        /**
         * A path painted like Logisim's: filled when it is closed and has a fill; the outline in the stroke's colour
         * (the fill's when Logisim draws no outline). Stroke widths are VSim's own.
         */
        private SymbolPath styled(List<SymbolNode> nodes, boolean closed, Paint paint) {
            boolean filled = closed && !paint.fill().equalsIgnoreCase(NO_PAINT);
            NoteColor fill = filled ? fillColor(paint.fill()) : NoteColor.NONE;
            String outline = paint.stroke().equalsIgnoreCase(NO_PAINT) && filled ? paint.fill() : paint.stroke();
            return new SymbolPath(nodes, closed, filled, fill, lineColor(outline));
        }

        private Optional<SymbolText> text(Text text) {
            if (text.text().isBlank()) {
                return Optional.empty();
            }
            TextAlignment alignment = switch (text.anchor()) {
                case START -> TextAlignment.LEFT;
                case MIDDLE -> TextAlignment.CENTER;
                case END -> TextAlignment.RIGHT;
            };
            double middleY = text.y() - MIDDLE_ABOVE_BASELINE * text.font().size();
            SymbolPoint wanted = unclamped(text.x(), middleY);
            SymbolPoint anchor = clamp(wanted);
            if (Math.abs(anchor.x() - wanted.x()) > TEXT_MOVE_NOTED
                    || Math.abs(anchor.y() - wanted.y()) > TEXT_MOVE_NOTED) {
                notes.add(ImportNote.about(ImportNote.Kind.APPROXIMATED, circuit, LogisimNotes.TEXT_MOVED,
                        text.text()));
            }
            return Optional.of(new SymbolText(anchor, Optional.of(text.text()), size(text.font()), alignment,
                    letterColor(text.color()), NoteColor.NONE));
        }

        /** The circuit's {@code clabel} in the middle of the box; noted when Logisim draws it sideways. */
        private Optional<SymbolText> label() {
            String label = unescape(circuitAttributes.textOr("clabel", ""));
            if (label.isBlank()) {
                return Optional.empty();
            }
            if (!circuitAttributes.textOr("clabelup", DEFAULT_FACING).strip().equalsIgnoreCase(UPRIGHT_LABEL)) {
                notes.add(ImportNote.about(ImportNote.Kind.APPROXIMATED, circuit, LogisimNotes.LABEL_TURNED, label));
            }
            LogisimFont font;
            try {
                font = LogisimFont.parse(circuitAttributes.textOr("clabelfont", ""));
            } catch (IllegalArgumentException exception) {
                font = LogisimFont.DEFAULT;
            }
            SymbolPoint middle = new SymbolPoint(width * SymbolPoint.EIGHTHS / 2, height * SymbolPoint.EIGHTHS / 2);
            return Optional.of(new SymbolText(middle, Optional.of(label), size(font), TextAlignment.CENTER));
        }

        /** Logisim's label escapes: {@code \n} a new line, {@code \\} a backslash. */
        private static String unescape(String label) {
            StringBuilder text = new StringBuilder();
            for (int index = 0; index < label.length(); index++) {
                char character = label.charAt(index);
                if (character == '\\' && index + 1 < label.length()) {
                    char next = label.charAt(index + 1);
                    if (next == 'n' || next == '\\') {
                        text.append(next == 'n' ? '\n' : '\\');
                        index++;
                        continue;
                    }
                }
                text.append(character == '\r' ? ' ' : character);
            }
            return text.toString();
        }

        /** The nearest of VSim's three lettering sizes to the font's size at VSim's scale. */
        private static TextSize size(LogisimFont font) {
            double scaled = font.size() * EIGHTHS_PER_PIXEL;
            TextSize nearest = TextSize.MEDIUM;
            for (TextSize candidate : TextSize.values()) {
                if (Math.abs(points(candidate) - scaled) < Math.abs(points(nearest) - scaled)) {
                    nearest = candidate;
                }
            }
            return nearest;
        }

        /** VSim's lettering sizes in pixels at one unit = 8 px ({@code SymbolPainter.sizeOf}). */
        private static double points(TextSize size) {
            return switch (size) {
                case SMALL -> 6;
                case MEDIUM -> 9;
                case LARGE -> 14;
            };
        }

        /** What VSim cannot draw of a text's font: another family than a sans-serif, or italics. */
        private static Optional<String> fontProblem(LogisimFont font) {
            String family = font.family().toLowerCase(Locale.ROOT);
            boolean sans = family.contains("sans") || family.equals("dialog") || family.equals("arial")
                    || family.equals("helvetica");
            if (sans && !font.italic()) {
                return Optional.empty();
            }
            return Optional.of(font.family() + (font.italic() ? " italic" : ""));
        }

        private SymbolPoint point(double pixelX, double pixelY) {
            return clamp(unclamped(pixelX, pixelY));
        }

        private SymbolPoint unclamped(double pixelX, double pixelY) {
            return new SymbolPoint((int) Math.round((pixelX - left * PIXELS_PER_UNIT) * EIGHTHS_PER_PIXEL),
                    (int) Math.round((pixelY - top * PIXELS_PER_UNIT) * EIGHTHS_PER_PIXEL));
        }

        private SymbolPoint clamp(SymbolPoint point) {
            return new SymbolPoint(Math.clamp(point.x(), 0, width * SymbolPoint.EIGHTHS),
                    Math.clamp(point.y(), 0, height * SymbolPoint.EIGHTHS));
        }

        /** The figures' and ports' extent in Logisim pixels. */
        private static final class Bounds {
            private double minimumX = Double.POSITIVE_INFINITY;
            private double minimumY = Double.POSITIVE_INFINITY;
            private double maximumX = Double.NEGATIVE_INFINITY;
            private double maximumY = Double.NEGATIVE_INFINITY;

            Bounds add(double x, double y) {
                minimumX = Math.min(minimumX, x);
                minimumY = Math.min(minimumY, y);
                maximumX = Math.max(maximumX, x);
                maximumY = Math.max(maximumY, y);
                return this;
            }

            Bounds add(LogisimPoint point) {
                return add(point.x(), point.y());
            }

            boolean isEmpty() {
                return minimumX > maximumX;
            }
        }
    }

    /** Black is the scheme's own line colour; any other the nearest of the palette. */
    private static NoteColor lineColor(String color) {
        return nearest(color, false).filter(found -> found != NoteColor.BLACK).orElse(NoteColor.NONE);
    }

    /** White is the scheme's own fill, the component's background; never red (red means broken). */
    private static NoteColor fillColor(String color) {
        return nearest(color, true).filter(found -> found != NoteColor.WHITE).orElse(NoteColor.NONE);
    }

    /** Black letters are the scheme's own text colour. */
    private static NoteColor letterColor(String color) {
        return lineColor(color);
    }

    private static Optional<NoteColor> nearest(String color, boolean fill) {
        Optional<int[]> rgb = rgb(color);
        if (rgb.isEmpty()) {
            return Optional.empty();
        }
        NoteColor nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        for (Map.Entry<NoteColor, int[]> entry : PALETTE.entrySet()) {
            if (fill && !SymbolPath.FILL_CHOICES.contains(entry.getKey())) {
                continue;
            }
            long distance = 0;
            for (int channel = 0; channel < 3; channel++) {
                long difference = rgb.get()[channel] - entry.getValue()[channel];
                distance += difference * difference;
            }
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = entry.getKey();
            }
        }
        return Optional.ofNullable(nearest);
    }

    /** {@code #rrggbb} (or {@code #rgb}) as red, green, blue; empty for {@code none} or anything else. */
    private static Optional<int[]> rgb(String color) {
        String hex = color.strip();
        if (!hex.startsWith("#")) {
            return Optional.empty();
        }
        hex = hex.substring(1);
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        }
        if (hex.length() != 6) {
            return Optional.empty();
        }
        try {
            int value = Integer.parseInt(hex, 16);
            return Optional.of(new int[] {(value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff});
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    /** A typical shade of each palette colour, only to find the nearest one (the schemes choose the real ones). */
    private static Map<NoteColor, int[]> palette() {
        Map<NoteColor, int[]> palette = new EnumMap<>(NoteColor.class);
        palette.put(NoteColor.BLACK, new int[] {0, 0, 0});
        palette.put(NoteColor.WHITE, new int[] {255, 255, 255});
        palette.put(NoteColor.GRAY, new int[] {128, 128, 128});
        palette.put(NoteColor.RED, new int[] {220, 40, 40});
        palette.put(NoteColor.GREEN, new int[] {40, 160, 60});
        palette.put(NoteColor.BLUE, new int[] {40, 90, 210});
        palette.put(NoteColor.YELLOW, new int[] {235, 200, 30});
        palette.put(NoteColor.PURPLE, new int[] {131, 71, 173});
        return palette;
    }
}
