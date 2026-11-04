package cl.almejo.vsim.core.io.logisim;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads the elements of a {@code <appear>} the way Logisim 2.7 does:
 * the SVG subset it writes plus {@code circ-port} and {@code circ-anchor}. An element Logisim would not draw either is
 * empty, so the reader can note it.
 */
final class AppearanceReader {

    /** What SVG says when a shape has no stroke or no fill. */
    private static final String NONE = "none";
    /** Logisim's colour for a fill the file does not give. */
    private static final String BLACK = "#000000";

    private AppearanceReader() {
    }

    /**
     * The shape {@code element} draws, empty when Logisim does not know its tag.
     *
     * @throws IllegalArgumentException when a known element has values that cannot be read
     */
    static Optional<LogisimAppearance.Shape> shape(Element element) {
        return Optional.ofNullable(switch (element.getTagName()) {
            case "rect" -> rectangle(element);
            case "ellipse" -> oval(element);
            case "line" -> new LogisimAppearance.Line(point(element, "x1", "y1"), point(element, "x2", "y2"),
                    paint(element));
            case "polyline" -> new LogisimAppearance.Polyline(false, points(element.getAttribute("points")),
                    paint(element));
            case "polygon" -> new LogisimAppearance.Polyline(true, points(element.getAttribute("points")),
                    paint(element));
            case "path" -> curve(element);
            case "text" -> text(element);
            case "circ-port" -> new LogisimAppearance.Port(LogisimPoint.parse(element.getAttribute("pin")),
                    integer(element, "x"), integer(element, "y"), integer(element, "width"),
                    integer(element, "height"));
            case "circ-anchor", "circ-origin" -> new LogisimAppearance.Anchor(integer(element, "x"),
                    integer(element, "y"), integer(element, "width"), integer(element, "height"),
                    element.hasAttribute("facing") ? element.getAttribute("facing") : "east");
            default -> null;
        });
    }

    private static LogisimAppearance.Shape rectangle(Element element) {
        int cornerRadius = element.hasAttribute("rx") ? integer(element, "rx") : 0;
        return new LogisimAppearance.Rectangle(integer(element, "x"), integer(element, "y"),
                integer(element, "width"), integer(element, "height"), cornerRadius, paint(element));
    }

    /** By its box, rounded the way Logisim does. */
    private static LogisimAppearance.Shape oval(Element element) {
        double centreX = decimal(element, "cx");
        double centreY = decimal(element, "cy");
        double radiusX = decimal(element, "rx");
        double radiusY = decimal(element, "ry");
        return new LogisimAppearance.Oval((int) Math.round(centreX - radiusX), (int) Math.round(centreY - radiusY),
                (int) Math.round(radiusX * 2), (int) Math.round(radiusY * 2), paint(element));
    }

    /**
     * {@code <path>}: Logisim writes only one quadratic curve, {@code M x0,y0 Q cx,cy x1,y1}, or with {@code q} the
     * control and end points relative to the start. Whole numbers only; spaces, commas or anything else may part them.
     */
    private static LogisimAppearance.Shape curve(Element element) {
        String data = element.getAttribute("d");
        List<String> pieces = pathPieces(data);
        boolean quadratic = pieces.size() == 8 && pieces.get(0).equals("M")
                && (pieces.get(3).equals("Q") || pieces.get(3).equals("q"));
        if (!quadratic) {
            throw new IllegalArgumentException("not a single quadratic curve: " + data);
        }
        LogisimPoint from = new LogisimPoint(whole(pieces.get(1)), whole(pieces.get(2)));
        LogisimPoint control = new LogisimPoint(whole(pieces.get(4)), whole(pieces.get(5)));
        LogisimPoint to = new LogisimPoint(whole(pieces.get(6)), whole(pieces.get(7)));
        if (pieces.get(3).equals("q")) {
            control = new LogisimPoint(from.x() + control.x(), from.y() + control.y());
            to = new LogisimPoint(from.x() + to.x(), from.y() + to.y());
        }
        return new LogisimAppearance.Curve(from, to, control, paint(element));
    }

    /** The command letters and the numbers of path data, in order; any other character only parts them. */
    private static List<String> pathPieces(String data) {
        List<String> pieces = new ArrayList<>();
        int index = 0;
        while (index < data.length()) {
            char character = data.charAt(index);
            if (isAsciiLetter(character)) {
                pieces.add(String.valueOf(character));
                index++;
            } else if (startsNumber(data, index)) {
                int end = index + 1;
                while (end < data.length() && (isDigit(data.charAt(end)) || data.charAt(end) == '.')) {
                    end++;
                }
                pieces.add(data.substring(index, end));
                index = end;
            } else {
                index++;
            }
        }
        return pieces;
    }

    private static boolean startsNumber(String data, int index) {
        char character = data.charAt(index);
        return isDigit(character) || character == '-' && index + 1 < data.length() && isDigit(data.charAt(index + 1));
    }

    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }

    private static boolean isAsciiLetter(char character) {
        return character >= 'a' && character <= 'z' || character >= 'A' && character <= 'Z';
    }

    private static LogisimAppearance.Shape text(Element element) {
        LogisimFont font = new LogisimFont(element.getAttribute("font-family"),
                element.getAttribute("font-weight").equals("bold"),
                element.getAttribute("font-style").equals("italic"), integer(element, "font-size"));
        LogisimAppearance.TextAnchor anchor = switch (element.getAttribute("text-anchor")) {
            case "start" -> LogisimAppearance.TextAnchor.START;
            case "end" -> LogisimAppearance.TextAnchor.END;
            default -> LogisimAppearance.TextAnchor.MIDDLE;
        };
        String color = element.getAttribute("fill");
        return new LogisimAppearance.Text(integer(element, "x"), integer(element, "y"), element.getTextContent(),
                font, anchor, color.isEmpty() ? BLACK : color);
    }

    private static LogisimAppearance.Paint paint(Element element) {
        String stroke = element.getAttribute("stroke");
        String fill = element.getAttribute("fill");
        int strokeWidth = element.hasAttribute("stroke-width") ? integer(element, "stroke-width") : 1;
        return new LogisimAppearance.Paint(stroke.isEmpty() ? NONE : stroke, fill.isEmpty() ? BLACK : fill,
                strokeWidth);
    }

    private static List<LogisimPoint> points(String text) {
        String[] numbers = text.strip().split("[ ,\\n\\r\\t]+");
        List<LogisimPoint> points = new ArrayList<>();
        for (int index = 0; index + 1 < numbers.length; index += 2) {
            points.add(new LogisimPoint(whole(numbers[index]), whole(numbers[index + 1])));
        }
        return points;
    }

    private static LogisimPoint point(Element element, String xName, String yName) {
        return new LogisimPoint(integer(element, xName), integer(element, yName));
    }

    private static int integer(Element element, String name) {
        return whole(element.getAttribute(name));
    }

    private static double decimal(Element element, String name) {
        try {
            return Double.parseDouble(element.getAttribute(name));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " is not a number", exception);
        }
    }

    private static int whole(String text) {
        try {
            return Integer.parseInt(text.strip());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("not an integer: " + text, exception);
        }
    }
}
