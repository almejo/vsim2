package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.appearance.CurvePoint;
import cl.almejo.vsim.core.component.appearance.SymbolDrawing;
import cl.almejo.vsim.core.component.appearance.SymbolElement;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolText;
import cl.almejo.vsim.core.component.appearance.TextSize;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A custom component's look as Logisim's {@code <appear>}, the inverse of {@link LogisimAppearanceConverter} at the
 * same scale (one VSim unit is Logisim's 10-pixel grid step, an eighth 1.25 px): the drawing's figures as polylines and
 * polygons (curves become short straight pieces, Logisim draws only single quadratic curves), its texts, or VSim's
 * plain box with the name when there is no drawing; one port at each pin of the box, and the anchor at the box's top
 * left corner facing east. So an instance placed at its VSim position (×10) and turned like the VSim one has every end
 * exactly where the VSim pin is.
 */
final class ExportAppearance {

    /** Where the box's top left corner (and the anchor) is in the appearance's own coordinates. */
    static final LogisimPoint ORIGIN = new LogisimPoint(50, 50);
    private static final int PIXELS_PER_UNIT = 10;
    private static final double PIXELS_PER_EIGHTH = (double) PIXELS_PER_UNIT / SymbolPoint.EIGHTHS;
    /** Straight pieces per curved segment of a figure. */
    private static final int CURVE_PIECES = 8;
    /** Logisim's usual outline. */
    private static final int STROKE_WIDTH = 2;
    private static final String BLACK = "#000000";
    private static final String WHITE = "#ffffff";
    private static final String NO_PAINT = "none";
    private static final String FAMILY = "SansSerif";
    /** The import's {@code MIDDLE_ABOVE_BASELINE}: a text's middle is this many font sizes above its baseline. */
    private static final double MIDDLE_ABOVE_BASELINE = 0.35;
    private static final double LINE_SPACING = 1.2;
    /** The name in the middle of the plain box: Logisim's usual 12 points. */
    private static final int NAME_SIZE = 12;
    /** The pin names of the plain box, and how far inside the box they start. */
    private static final int PIN_NAME_SIZE = 10;
    private static final int PIN_NAME_INSET = 3;
    /** Logisim's ports: an input's box is 8 px, an output's 10 px, the anchor's 6 px, all centred on their point. */
    private static final int INPUT_PORT = 8;
    private static final int OUTPUT_PORT = 10;
    private static final int ANCHOR = 6;

    private ExportAppearance() {
    }

    /** The look of {@code definition}; {@code innerPins} says where the Logisim pin of each box pin (by index) is. */
    static LogisimAppearance of(ComponentDefinition definition, Map<Integer, LogisimPoint> innerPins) {
        List<LogisimAppearance.Shape> shapes = new ArrayList<>();
        GridSize size = definition.size();
        boolean pinNames;
        if (definition.appearance().isPresent()) {
            SymbolDrawing drawing = definition.appearance().get();
            for (SymbolElement element : drawing.elements()) {
                switch (element) {
                    case SymbolPath path -> shapes.add(figure(path));
                    case SymbolText text -> shapes.addAll(texts(text, definition.name()));
                }
            }
            pinNames = drawing.showPinNames();
        } else {
            shapes.add(new LogisimAppearance.Rectangle(ORIGIN.x(), ORIGIN.y(), size.width() * PIXELS_PER_UNIT,
                    size.height() * PIXELS_PER_UNIT, 0, new LogisimAppearance.Paint(BLACK, WHITE, STROKE_WIDTH)));
            int middleX = ORIGIN.x() + size.width() * PIXELS_PER_UNIT / 2;
            int middleY = ORIGIN.y() + size.height() * PIXELS_PER_UNIT / 2;
            shapes.add(text(middleX, middleY, definition.name(), NAME_SIZE, LogisimAppearance.TextAnchor.MIDDLE,
                    BLACK));
            pinNames = true;
        }
        if (pinNames) {
            definition.pinout().forEach(pin -> shapes.add(pinName(pin.spec(), size)));
        }
        List<DefinitionPin> pinout = definition.pinout();
        for (int index = 0; index < pinout.size(); index++) {
            LogisimPoint inner = innerPins.get(index);
            if (inner != null) {
                shapes.add(port(pinout.get(index).spec(), inner));
            }
        }
        shapes.add(new LogisimAppearance.Anchor(ORIGIN.x() - ANCHOR / 2, ORIGIN.y() - ANCHOR / 2, ANCHOR, ANCHOR,
                LogisimFacing.EAST.text()));
        return new LogisimAppearance(shapes);
    }

    /** A figure as one polyline or polygon through its nodes, its curved segments cut in straight pieces. */
    private static LogisimAppearance.Shape figure(SymbolPath path) {
        List<LogisimPoint> points = new ArrayList<>();
        for (int segment = 0; segment < path.segmentCount(); segment++) {
            points.add(pixels(path.nodes().get(segment).point()));
            if (path.nodes().get(segment).curved()) {
                for (int piece = 1; piece < CURVE_PIECES; piece++) {
                    CurvePoint inside = path.pointAt(segment, (double) piece / CURVE_PIECES);
                    points.add(pixels(inside.x(), inside.y()));
                }
            }
        }
        if (!path.closed()) {
            points.add(pixels(path.nodes().getLast().point()));
        }
        String fill = path.filled() ? color(path.fill(), WHITE) : NO_PAINT;
        return new LogisimAppearance.Polyline(path.closed(), points,
                new LogisimAppearance.Paint(color(path.line(), BLACK), fill, STROKE_WIDTH));
    }

    /** One Logisim text per line, the block centred on the VSim anchor; a text without its own says the name. */
    private static List<LogisimAppearance.Shape> texts(SymbolText text, String name) {
        List<String> lines = SymbolText.linesOf(text.text().orElse(name));
        int fontSize = fontSize(text.size());
        LogisimAppearance.TextAnchor anchor = switch (text.alignment()) {
            case LEFT -> LogisimAppearance.TextAnchor.START;
            case CENTER -> LogisimAppearance.TextAnchor.MIDDLE;
            case RIGHT -> LogisimAppearance.TextAnchor.END;
        };
        LogisimPoint middle = pixels(text.anchor());
        double lineHeight = fontSize * LINE_SPACING;
        List<LogisimAppearance.Shape> shapes = new ArrayList<>();
        for (int line = 0; line < lines.size(); line++) {
            double lineMiddle = middle.y() + (line - (lines.size() - 1) / 2.0) * lineHeight;
            shapes.add(text(middle.x(), (int) Math.round(lineMiddle), lines.get(line), fontSize, anchor,
                    color(text.color(), BLACK)));
        }
        return shapes;
    }

    /** A text whose middle is at ({@code x}, {@code middleY}): Logisim places it by its baseline. */
    private static LogisimAppearance.Text text(int x, int middleY, String text, int fontSize,
                                               LogisimAppearance.TextAnchor anchor, String color) {
        int baseline = middleY + (int) Math.round(MIDDLE_ABOVE_BASELINE * fontSize);
        return new LogisimAppearance.Text(x, baseline, text, new LogisimFont(FAMILY, true, false, fontSize), anchor,
                color);
    }

    /** The pin's name just inside the box, by the side the pin is on. */
    private static LogisimAppearance.Shape pinName(PinSpec pin, GridSize size) {
        int x = ORIGIN.x() + pin.position().x() * PIXELS_PER_UNIT;
        int y = ORIGIN.y() + pin.position().y() * PIXELS_PER_UNIT;
        if (pin.position().x() == 0) {
            return text(x + PIN_NAME_INSET, y, pin.name(), PIN_NAME_SIZE, LogisimAppearance.TextAnchor.START, BLACK);
        }
        if (pin.position().x() == size.width()) {
            return text(x - PIN_NAME_INSET, y, pin.name(), PIN_NAME_SIZE, LogisimAppearance.TextAnchor.END, BLACK);
        }
        int inward = pin.position().y() == 0 ? PIN_NAME_SIZE : -PIN_NAME_SIZE;
        return text(x, y + inward, pin.name(), PIN_NAME_SIZE, LogisimAppearance.TextAnchor.MIDDLE, BLACK);
    }

    private static LogisimAppearance.Port port(PinSpec pin, LogisimPoint inner) {
        int box = pin.isInput() ? INPUT_PORT : OUTPUT_PORT;
        int x = ORIGIN.x() + pin.position().x() * PIXELS_PER_UNIT;
        int y = ORIGIN.y() + pin.position().y() * PIXELS_PER_UNIT;
        return new LogisimAppearance.Port(inner, x - box / 2, y - box / 2, box, box);
    }

    /** The inverse of the import's sizes: VSim's 6, 9 and 14 px letters at 8 px per unit. */
    private static int fontSize(TextSize size) {
        return switch (size) {
            case SMALL -> 8;
            case MEDIUM -> 12;
            case LARGE -> 18;
        };
    }

    /** A palette colour as the import reads it back ({@link LogisimAppearanceConverter}); {@code NONE} is plain. */
    private static String color(NoteColor color, String plain) {
        int[] rgb = LogisimAppearanceConverter.PALETTE.get(color);
        return rgb == null ? plain : String.format("#%02x%02x%02x", rgb[0], rgb[1], rgb[2]);
    }

    private static LogisimPoint pixels(SymbolPoint point) {
        return pixels(point.x(), point.y());
    }

    private static LogisimPoint pixels(double eighthsX, double eighthsY) {
        return new LogisimPoint(ORIGIN.x() + (int) Math.round(eighthsX * PIXELS_PER_EIGHTH),
                ORIGIN.y() + (int) Math.round(eighthsY * PIXELS_PER_EIGHTH));
    }
}
