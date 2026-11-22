package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.io.CircuitFormatException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reads a Logisim {@code .circ} file into a {@link LogisimProject}, with the JDK's XML parser. It follows Logisim
 * 2.7's own {@code XmlReader}: libraries are declared by number ({@code <lib name="1" desc="#Gates">}) and components
 * name theirs by that number; a component without {@code lib} is an instance of one of the file's circuits; every
 * attribute the file leaves out gets Logisim's default ({@link LogisimDefaults}); the {@code <tool>}s inside a
 * {@code <lib>}, the toolbar and the mouse mappings only configure Logisim's editor, so they are not read.
 *
 * <p>Only a file that is not XML, or XML that is not a Logisim project, is refused: anything inside a project that
 * cannot be taken (an unknown library, a broken wire, an odd shape) becomes an {@link ImportNote} and the rest is
 * read.
 */
public final class LogisimReader {

    /** Parse errors become the exception {@link #parse} turns into a refusal, never a line on the console. */
    private static final ErrorHandler SILENT_ERRORS = new ErrorHandler() {
        @Override
        public void warning(SAXParseException exception) {
            // a warning never stops the parse and says nothing the user can act on
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    };

    /**
     * The name a circuit with a blank name is imported under: a plain word like the export's {@code main}, since a
     * circuit's name is the file's data, not a text of VSim's.
     */
    static final String UNNAMED_CIRCUIT = "circuit";

    private final LogisimDefaults defaults;

    /** A reader of Logisim 2.7.x files. */
    public LogisimReader() {
        this(LogisimDefaults.logisim27());
    }

    /** A reader that fills omitted attributes from {@code defaults} (another Logisim dialect's table). */
    public LogisimReader(LogisimDefaults defaults) {
        this.defaults = Objects.requireNonNull(defaults, "defaults");
    }

    /**
     * The project in {@code input}; the stream is read to its end but not closed.
     *
     * @throws CircuitFormatException {@code READ_ERROR} when the stream fails, {@code NOT_XML} when the text is
     *                                not XML, {@code NOT_LOGISIM_PROJECT} when its root is not a Logisim
     *                                {@code <project>}
     */
    public LogisimProject read(InputStream input) throws CircuitFormatException {
        Element project = parse(input).getDocumentElement();
        if (!project.getTagName().equals("project")) {
            throw new CircuitFormatException(CircuitFormatException.Reason.NOT_LOGISIM_PROJECT,
                    "not a Logisim project: <" + project.getTagName() + ">");
        }
        return new ProjectReading(project).read();
    }

    private static Document parse(InputStream input) throws CircuitFormatException {
        try {
            return builder().parse(input);
        } catch (SAXException exception) {
            throw new CircuitFormatException(CircuitFormatException.Reason.NOT_XML,
                    "not XML: " + exception.getMessage(), exception);
        } catch (IOException exception) {
            throw new CircuitFormatException(CircuitFormatException.Reason.READ_ERROR, exception.getMessage(),
                    exception);
        }
    }

    /** A parser that refuses document types, so a file can never make it read other files or expand entities. */
    private static DocumentBuilder builder() throws CircuitFormatException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(SILENT_ERRORS);
            return builder;
        } catch (ParserConfigurationException exception) {
            throw new CircuitFormatException(CircuitFormatException.Reason.READ_ERROR, exception.getMessage(),
                    exception);
        }
    }

    /** One file being read: its version, its libraries by number and the notes so far. */
    private final class ProjectReading {
        private final Element project;
        private final LogisimVersion version;
        private final Map<String, String> libraries = new HashMap<>();
        /** The name each blank circuit name stands for: the fallback of the first circuit written with it. */
        private final Map<String, String> renamed = new HashMap<>();
        private final List<ImportNote> notes = new ArrayList<>();

        ProjectReading(Element project) {
            this.project = project;
            this.version = LogisimVersion.parse(project.getAttribute("source"));
        }

        LogisimProject read() {
            for (Element library : children(project, "lib")) {
                readLibrary(library);
            }
            List<Element> circuitElements = children(project, "circuit");
            List<String> names = circuitNames(circuitElements);
            List<LogisimCircuit> circuits = new ArrayList<>();
            for (int index = 0; index < circuitElements.size(); index++) {
                circuits.add(readCircuit(circuitElements.get(index), names.get(index)));
            }
            Map<String, String> options = new LinkedHashMap<>();
            for (Element optionsElement : children(project, "options")) {
                options.putAll(attributes(optionsElement));
            }
            return new LogisimProject(mainCircuit(circuits), options, circuits, notes);
        }

        private void readLibrary(Element library) {
            String description = library.getAttribute("desc");
            libraries.put(library.getAttribute("name"), description);
            if (!defaults.knowsLibrary(description)) {
                notes.add(ImportNote.about(ImportNote.Kind.UNSUPPORTED, "", LogisimNotes.UNKNOWN_LIBRARY,
                        description));
            }
        }

        /**
         * Each circuit's name, in file order. A blank one, which no VSim component can have, becomes
         * {@code circuit} (then {@code circuit 2}, … when taken), noted; the {@code <main>} and the instances
         * that name it by its blank name (the first circuit with it) follow it.
         */
        private List<String> circuitNames(List<Element> circuitElements) {
            Set<String> taken = new HashSet<>();
            circuitElements.forEach(circuit -> taken.add(circuit.getAttribute("name")));
            List<String> names = new ArrayList<>();
            for (Element circuit : circuitElements) {
                String name = circuit.getAttribute("name");
                if (name.isBlank()) {
                    String fallback = unusedName(taken);
                    taken.add(fallback);
                    renamed.putIfAbsent(name, fallback);
                    notes.add(ImportNote.about(ImportNote.Kind.APPROXIMATED, fallback, LogisimNotes.UNNAMED_CIRCUIT,
                            fallback));
                    name = fallback;
                }
                names.add(name);
            }
            return names;
        }

        private static String unusedName(Set<String> taken) {
            String name = UNNAMED_CIRCUIT;
            for (int number = 2; taken.contains(name); number++) {
                name = UNNAMED_CIRCUIT + " " + number;
            }
            return name;
        }

        /** {@code <main name>}; without it (or when it names no circuit) the first circuit, as Logisim does. */
        private String mainCircuit(List<LogisimCircuit> circuits) {
            String first = circuits.isEmpty() ? "" : circuits.getFirst().name();
            Optional<String> named = children(project, "main").stream()
                    .map(main -> circuitNamed(main.getAttribute("name")))
                    .findFirst();
            if (named.isEmpty()) {
                return first;
            }
            if (circuits.stream().noneMatch(circuit -> circuit.name().equals(named.get()))) {
                notes.add(ImportNote.about(ImportNote.Kind.INVALID, "", LogisimNotes.MISSING_MAIN, named.get()));
                return first;
            }
            return named.get();
        }

        /** The name the circuit the file calls {@code written} goes by ({@link #circuitNames}). */
        private String circuitNamed(String written) {
            return renamed.getOrDefault(written, written);
        }

        private LogisimCircuit readCircuit(Element circuit, String name) {
            List<LogisimComponent> components = new ArrayList<>();
            List<LogisimWire> wires = new ArrayList<>();
            for (Element child : children(circuit, null)) {
                switch (child.getTagName()) {
                    case "comp" -> readComponent(name, child).ifPresent(components::add);
                    case "wire" -> readWire(name, child).ifPresent(wires::add);
                    default -> {
                        // <a> (the circuit's own attributes) and <appear> are read below
                    }
                }
            }
            Map<String, String> circuitAttributes = new LinkedHashMap<>(LogisimCircuit.DEFAULT_ATTRIBUTES);
            circuitAttributes.putAll(attributes(circuit));
            return new LogisimCircuit(name, components, wires, readAppearance(name, circuit),
                    LogisimAttributes.of(circuitAttributes));
        }

        private Optional<LogisimComponent> readComponent(String circuit, Element component) {
            String name = component.getAttribute("name");
            LogisimPoint location;
            try {
                location = LogisimPoint.parse(component.getAttribute("loc"));
            } catch (IllegalArgumentException exception) {
                notes.add(ImportNote.about(ImportNote.Kind.INVALID, circuit, LogisimNotes.BAD_COMPONENT, name,
                        component.getAttribute("loc")));
                return Optional.empty();
            }
            if (name.isEmpty()) {
                notes.add(ImportNote.at(ImportNote.Kind.INVALID, circuit, location, LogisimNotes.BAD_COMPONENT, name,
                        component.getAttribute("loc")));
                return Optional.empty();
            }
            String libraryNumber = component.getAttribute("lib");
            String library = libraryNumber.isEmpty() ? LogisimLibraries.SUBCIRCUIT : libraries.get(libraryNumber);
            if (library == null) {
                notes.add(ImportNote.at(ImportNote.Kind.INVALID, circuit, location, LogisimNotes.MISSING_LIBRARY,
                        name, libraryNumber));
                return Optional.empty();
            }
            LogisimAttributes attributes = defaults.resolve(library, name, attributes(component), version);
            String part = library.equals(LogisimLibraries.SUBCIRCUIT) ? circuitNamed(name) : name;
            return Optional.of(new LogisimComponent(library, part, location, attributes));
        }

        private Optional<LogisimWire> readWire(String circuit, Element wire) {
            try {
                return Optional.of(new LogisimWire(LogisimPoint.parse(wire.getAttribute("from")),
                        LogisimPoint.parse(wire.getAttribute("to"))));
            } catch (IllegalArgumentException exception) {
                notes.add(ImportNote.about(ImportNote.Kind.INVALID, circuit, LogisimNotes.BAD_WIRE,
                        wire.getAttribute("from"), wire.getAttribute("to")));
                return Optional.empty();
            }
        }

        private LogisimAppearance readAppearance(String circuit, Element circuitElement) {
            List<LogisimAppearance.Shape> shapes = new ArrayList<>();
            for (Element appear : children(circuitElement, "appear")) {
                for (Element element : children(appear, null)) {
                    readShape(circuit, element).ifPresent(shapes::add);
                }
            }
            return shapes.isEmpty() ? LogisimAppearance.NONE : new LogisimAppearance(shapes);
        }

        private Optional<LogisimAppearance.Shape> readShape(String circuit, Element element) {
            try {
                Optional<LogisimAppearance.Shape> shape = AppearanceReader.shape(element);
                if (shape.isEmpty()) {
                    notes.add(ImportNote.about(ImportNote.Kind.UNSUPPORTED, circuit, LogisimNotes.UNKNOWN_SHAPE,
                            element.getTagName()));
                }
                return shape;
            } catch (IllegalArgumentException exception) {
                notes.add(ImportNote.about(ImportNote.Kind.INVALID, circuit, LogisimNotes.BAD_SHAPE,
                        element.getTagName()));
                return Optional.empty();
            }
        }
    }

    /** The {@code <a name="…" val="…"/>} children of {@code element}; a value too long for one line is its text. */
    private static Map<String, String> attributes(Element element) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Element attribute : children(element, "a")) {
            String name = attribute.getAttribute("name");
            if (!name.isEmpty()) {
                values.put(name, attribute.hasAttribute("val") ? attribute.getAttribute("val")
                        : attribute.getTextContent());
            }
        }
        return values;
    }

    /** The child elements of {@code parent} called {@code tag}, or all of them when {@code tag} is null. */
    private static List<Element> children(Element parent, String tag) {
        List<Element> elements = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && (tag == null || element.getTagName().equals(tag))) {
                elements.add(element);
            }
        }
        return elements;
    }
}
