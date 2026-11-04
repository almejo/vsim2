package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.io.AtomicFiles;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Writes a {@link LogisimProject} as Logisim 2.7.1 saves one: every built-in library declared, the
 * main circuit, the options, Logisim's usual mouse mappings and toolbar, then each circuit with its own attributes, its
 * {@code <appear>}, its wires and its components. Like Logisim, a component carries only the attributes that differ
 * from its part's default ({@link LogisimDefaults}); a splitter's {@code bitN} are always written, since their default
 * depends on the others.
 */
public final class LogisimWriter {

    /** The Logisim version the file says it comes from: the one whose defaults are left out. */
    private static final String SOURCE = "2.7.1";
    private static final LogisimDefaults DEFAULTS = LogisimDefaults.logisim27();
    /** Logisim's own libraries, numbered in this order as a new Logisim file numbers them ({@code default.templ}). */
    private static final List<String> LIBRARIES = List.of(LogisimLibraries.WIRING, LogisimLibraries.GATES,
            LogisimLibraries.PLEXERS, LogisimLibraries.ARITHMETIC, LogisimLibraries.MEMORY,
            LogisimLibraries.INPUT_OUTPUT, LogisimLibraries.BASE);
    private static final Pattern SPLITTER_BIT = Pattern.compile("bit\\d+");
    private static final String SPLITTER = "Splitter";
    private static final String NONE = "none";

    private LogisimWriter() {
    }

    /** The project as the text of a {@code .circ} file. */
    public static String toText(LogisimProject project) {
        StringWriter text = new StringWriter();
        transform(project, new StreamResult(text));
        return text.toString();
    }

    /**
     * Writes the project to the file {@code target} in UTF-8, whole or not at all: a failure part way leaves an older
     * file there as it was ({@link AtomicFiles}).
     *
     * @throws IOException when the file cannot be written
     */
    public static void write(LogisimProject project, Path target) throws IOException {
        AtomicFiles.writeString(target, toText(project));
    }

    private static void transform(LogisimProject project, StreamResult result) {
        Objects.requireNonNull(project, "project");
        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            document.setXmlStandalone(false);
            new Writing(document, project).write();
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(new DOMSource(document), result);
        } catch (ParserConfigurationException | TransformerException exception) {
            throw new IllegalStateException("the JDK cannot write XML", exception);
        }
    }

    /** One project being written into one XML document. */
    private static final class Writing {
        private final Document document;
        private final LogisimProject project;
        private final List<String> libraries = new ArrayList<>(LIBRARIES);

        Writing(Document document, LogisimProject project) {
            this.document = document;
            this.project = project;
            for (LogisimCircuit circuit : project.circuits()) {
                for (LogisimComponent component : circuit.components()) {
                    if (!component.isSubcircuit() && !libraries.contains(component.library())) {
                        libraries.add(component.library());
                    }
                }
            }
        }

        void write() {
            Element root = document.createElement("project");
            root.setAttribute("source", SOURCE);
            root.setAttribute("version", "1.0");
            document.appendChild(root);
            for (int index = 0; index < libraries.size(); index++) {
                Element library = element(root, "lib");
                library.setAttribute("desc", libraries.get(index));
                library.setAttribute("name", String.valueOf(index));
            }
            element(root, "main").setAttribute("name", project.mainCircuit());
            Element options = element(root, "options");
            project.options().forEach((name, value) -> attribute(options, name, value));
            writeMappings(root);
            writeToolbar(root);
            project.circuits().forEach(circuit -> writeCircuit(root, circuit));
        }

        /** Logisim's usual mouse mappings: the right and middle buttons open the menu. */
        private void writeMappings(Element root) {
            Element mappings = element(root, "mappings");
            for (String button : List.of("Button2", "Button3", "Ctrl Button1")) {
                Element tool = baseTool(mappings, "Menu Tool");
                tool.setAttribute("map", button);
            }
        }

        /** Logisim's usual toolbar: poke, edit, text, then pins and three gates. */
        private void writeToolbar(Element root) {
            Element toolbar = element(root, "toolbar");
            baseTool(toolbar, "Poke Tool");
            baseTool(toolbar, "Edit Tool");
            baseTool(toolbar, "Text Tool");
            element(toolbar, "sep");
            Element inputPin = tool(toolbar, LogisimLibraries.WIRING, "Pin");
            attribute(inputPin, "tristate", "false");
            Element outputPin = tool(toolbar, LogisimLibraries.WIRING, "Pin");
            attribute(outputPin, "facing", "west");
            attribute(outputPin, "output", "true");
            attribute(outputPin, "labelloc", "east");
            for (String gate : List.of("NOT Gate", "AND Gate", "OR Gate")) {
                tool(toolbar, LogisimLibraries.GATES, gate);
            }
        }

        private Element baseTool(Element parent, String name) {
            return tool(parent, LogisimLibraries.BASE, name);
        }

        private Element tool(Element parent, String library, String name) {
            Element tool = element(parent, "tool");
            tool.setAttribute("lib", String.valueOf(libraries.indexOf(library)));
            tool.setAttribute("name", name);
            return tool;
        }

        private void writeCircuit(Element root, LogisimCircuit circuit) {
            Element element = element(root, "circuit");
            element.setAttribute("name", circuit.name());
            attribute(element, "circuit", circuit.name());
            circuit.attributes().asMap().forEach((name, value) -> {
                if (!name.equals("circuit") && !value.equals(LogisimCircuit.DEFAULT_ATTRIBUTES.get(name))) {
                    attribute(element, name, value);
                }
            });
            if (!circuit.appearance().shapes().isEmpty()) {
                Element appearance = element(element, "appear");
                circuit.appearance().shapes().forEach(shape -> writeShape(appearance, shape));
            }
            for (LogisimWire wire : circuit.wires()) {
                Element wireElement = element(element, "wire");
                wireElement.setAttribute("from", point(wire.from()));
                wireElement.setAttribute("to", point(wire.to()));
            }
            circuit.components().forEach(component -> writeComponent(element, component));
        }

        private void writeComponent(Element circuit, LogisimComponent component) {
            Element element = element(circuit, "comp");
            if (!component.isSubcircuit()) {
                element.setAttribute("lib", String.valueOf(libraries.indexOf(component.library())));
            }
            element.setAttribute("loc", point(component.location()));
            element.setAttribute("name", component.name());
            Map<String, String> defaults = DEFAULTS.resolve(component.library(), component.name(), Map.of(),
                    LogisimVersion.LATEST).asMap();
            boolean splitter = component.library().equals(LogisimLibraries.WIRING)
                    && component.name().equals(SPLITTER);
            component.attributes().asMap().forEach((name, value) -> {
                boolean always = splitter && SPLITTER_BIT.matcher(name).matches();
                if (always || !value.equals(defaults.get(name))) {
                    attribute(element, name, value);
                }
            });
        }

        // --- <appear> ---

        private void writeShape(Element appearance, LogisimAppearance.Shape shape) {
            switch (shape) {
                case LogisimAppearance.Rectangle rectangle -> {
                    Element element = element(appearance, "rect");
                    paint(element, rectangle.paint());
                    numbers(element, "height", rectangle.height(), "width", rectangle.width(), "x", rectangle.x(),
                            "y", rectangle.y());
                    if (rectangle.cornerRadius() > 0) {
                        numbers(element, "rx", rectangle.cornerRadius(), "ry", rectangle.cornerRadius());
                    }
                }
                case LogisimAppearance.Oval oval -> {
                    Element element = element(appearance, "ellipse");
                    paint(element, oval.paint());
                    element.setAttribute("cx", decimal(oval.x() + oval.width() / 2.0));
                    element.setAttribute("cy", decimal(oval.y() + oval.height() / 2.0));
                    element.setAttribute("rx", decimal(oval.width() / 2.0));
                    element.setAttribute("ry", decimal(oval.height() / 2.0));
                }
                case LogisimAppearance.Line line -> {
                    Element element = element(appearance, "line");
                    paint(element, line.paint());
                    numbers(element, "x1", line.from().x(), "x2", line.to().x(), "y1", line.from().y(), "y2",
                            line.to().y());
                }
                case LogisimAppearance.Polyline polyline -> {
                    Element element = element(appearance, polyline.closed() ? "polygon" : "polyline");
                    paint(element, polyline.paint());
                    element.setAttribute("points", String.join(" ", polyline.points().stream()
                            .map(point -> point.x() + "," + point.y()).toList()));
                }
                case LogisimAppearance.Curve curve -> {
                    Element element = element(appearance, "path");
                    element.setAttribute("d", "M" + curve.from().x() + "," + curve.from().y() + " Q"
                            + curve.control().x() + "," + curve.control().y() + " " + curve.to().x() + ","
                            + curve.to().y());
                    paint(element, curve.paint());
                }
                case LogisimAppearance.Text text -> writeText(appearance, text);
                case LogisimAppearance.Port port -> {
                    Element element = element(appearance, "circ-port");
                    numbers(element, "height", port.height());
                    element.setAttribute("pin", port.pin().x() + "," + port.pin().y());
                    numbers(element, "width", port.width(), "x", port.x(), "y", port.y());
                }
                case LogisimAppearance.Anchor anchor -> {
                    Element element = element(appearance, "circ-anchor");
                    element.setAttribute("facing", anchor.facing());
                    numbers(element, "height", anchor.height(), "width", anchor.width(), "x", anchor.x(), "y",
                            anchor.y());
                }
            }
        }

        private void writeText(Element appearance, LogisimAppearance.Text text) {
            Element element = element(appearance, "text");
            element.setAttribute("fill", text.color());
            element.setAttribute("font-family", text.font().family());
            element.setAttribute("font-size", String.valueOf(text.font().size()));
            if (text.font().italic()) {
                element.setAttribute("font-style", "italic");
            }
            if (text.font().bold()) {
                element.setAttribute("font-weight", "bold");
            }
            element.setAttribute("text-anchor", switch (text.anchor()) {
                case START -> "start";
                case MIDDLE -> "middle";
                case END -> "end";
            });
            numbers(element, "x", text.x(), "y", text.y());
            element.setTextContent(text.text());
        }

        /** The stroke (or {@code none}), its width when not 1, and the fill, as Logisim writes them. */
        private static void paint(Element element, LogisimAppearance.Paint paint) {
            element.setAttribute("fill", paint.fill());
            element.setAttribute("stroke", paint.stroke());
            if (!paint.stroke().equals(NONE) && paint.strokeWidth() != 1) {
                element.setAttribute("stroke-width", String.valueOf(paint.strokeWidth()));
            }
        }

        private static void numbers(Element element, Object... namesAndValues) {
            for (int index = 0; index < namesAndValues.length; index += 2) {
                element.setAttribute((String) namesAndValues[index], String.valueOf(namesAndValues[index + 1]));
            }
        }

        private static String decimal(double value) {
            return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
        }

        // --- XML ---

        private Element element(Element parent, String tag) {
            Element element = document.createElement(tag);
            parent.appendChild(element);
            return element;
        }

        /** {@code <a name val/>}, or with the value as text when it has several lines (a ROM's contents). */
        private void attribute(Element parent, String name, String value) {
            Element attribute = element(parent, "a");
            attribute.setAttribute("name", name);
            if (value.contains("\n")) {
                attribute.setTextContent(value);
            } else {
                attribute.setAttribute("val", value);
            }
        }

        private static String point(LogisimPoint point) {
            return "(" + point.x() + "," + point.y() + ")";
        }
    }
}
