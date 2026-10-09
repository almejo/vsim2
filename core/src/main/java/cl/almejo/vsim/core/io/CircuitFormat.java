package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Reads and writes the VSim 2 circuit file format (JSON, formatVersion 2). */
public final class CircuitFormat {

    public static final int FORMAT_VERSION = 2;
    static final String KIND_CIRCUIT = "circuit";

    record ComponentEntry(String id, String type, int x, int y, int rotation, String label,
                          Map<String, Object> parameters) {
    }

    record WireEntry(String id, int x1, int y1, int x2, int y2) {
    }

    record LabelEntry(String id, int x, int y, String text) {
    }

    static ComponentEntry componentEntry(Component component) {
        return new ComponentEntry(component.id().value(), component.typeId(), component.position().x(),
                component.position().y(), component.rotation().quarterTurns(), component.label(),
                new TreeMap<>(component.parameters()));
    }

    static WireEntry wireEntry(Wire wire) {
        return new WireEntry(wire.id().value(), wire.start().x(), wire.start().y(), wire.end().x(), wire.end().y());
    }

    static LabelEntry labelEntry(TextLabel label) {
        return new LabelEntry(label.id().value(), label.position().x(), label.position().y(), label.text());
    }

    record CircuitFile(int formatVersion, String kind, String name, List<ComponentEntry> components,
                       List<WireEntry> wires, List<LabelEntry> labels,
                       @JsonInclude(JsonInclude.Include.NON_EMPTY) List<DefinitionEntries.DefinitionEntry> definitions) {
    }

    private final ComponentTypeRegistry registry;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);

    public CircuitFormat(ComponentTypeRegistry registry) {
        this.registry = registry;
    }

    public String write(CircuitDocument document) {
        CircuitFile file = new CircuitFile(FORMAT_VERSION, KIND_CIRCUIT, document.name(),
                document.components().stream().map(CircuitFormat::componentEntry).toList(),
                document.wires().stream().map(CircuitFormat::wireEntry).toList(),
                document.labels().stream().map(CircuitFormat::labelEntry).toList(),
                document.usedDefinitions().stream().map(DefinitionEntries::entry).toList());
        try {
            return mapper.writeValueAsString(file);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize circuit", exception);
        }
    }

    public CircuitDocument read(String json) throws CircuitFormatException {
        if (json == null) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_JSON, "json is null");
        }
        try {
            return read(mapper.readTree(json));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_JSON, exception.getMessage(), exception);
        }
    }

    public CircuitDocument read(JsonNode root) throws CircuitFormatException {
        if (root == null || !root.isObject()) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT, "root must be a JSON object");
        }
        requireFormatVersion(root);
        CircuitFile file;
        try {
            file = mapper.treeToValue(root, CircuitFile.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT, exception.getMessage(), exception);
        }
        if (!KIND_CIRCUIT.equals(file.kind())) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT, "kind " + file.kind());
        }
        CircuitDocument document = new CircuitDocument();
        document.setName(file.name());
        // The file's own definitions resolve its subcircuit:<id> types; the caller's resolvers are not consulted.
        ComponentTypeRegistry local = registry.copy();
        local.addResolver(SubcircuitTypes.resolverFor(document));
        DefinitionEntries.readInto(document, entries(file.definitions()), local);
        for (ComponentEntry entry : entries(file.components())) {
            requireEntry(entry, "components");
            ComponentType type = local.find(entry.type()).orElseThrow(() -> new CircuitFormatException(
                    CircuitFormatException.Reason.UNKNOWN_COMPONENT_TYPE, String.valueOf(entry.type())));
            requireRotation(entry);
            add(document, () -> component(entry, type.resolveParameters(entry.parameters())));
        }
        for (WireEntry entry : entries(file.wires())) {
            requireEntry(entry, "wires");
            add(document, () -> wire(entry));
        }
        for (LabelEntry entry : entries(file.labels())) {
            requireEntry(entry, "labels");
            add(document, () -> label(entry));
        }
        return document;
    }

    static void requireFormatVersion(JsonNode root) throws CircuitFormatException {
        JsonNode version = root.path("formatVersion");
        if (!version.isIntegralNumber() || !version.canConvertToInt() || version.intValue() != FORMAT_VERSION) {
            throw new CircuitFormatException(CircuitFormatException.Reason.UNSUPPORTED_VERSION,
                    "formatVersion " + (version.isMissingNode() ? "missing" : version.toString()));
        }
    }

    /** {@code entries}, or an empty list when the key was absent. */
    static <T> List<T> entries(List<T> entries) {
        return Optional.ofNullable(entries).orElse(List.of());
    }

    static <T> T requireEntry(T entry, String key) throws CircuitFormatException {
        if (entry == null) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT, "null entry in " + key);
        }
        return entry;
    }

    static void requireRotation(ComponentEntry entry) throws CircuitFormatException {
        if (entry.rotation() < 0 || entry.rotation() > 3) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT,
                    "rotation must be 0..3 for " + entry.id() + ": " + entry.rotation());
        }
    }

    /** The component {@code entry} describes, with {@code parameters} (call {@link #requireRotation} first). */
    static Component component(ComponentEntry entry, Map<String, Object> parameters) {
        return new Component(new ElementId(entry.id()), entry.type(), new GridPoint(entry.x(), entry.y()),
                Rotation.fromQuarterTurns(entry.rotation()), parameters, entry.label());
    }

    static Wire wire(WireEntry entry) {
        return new Wire(new ElementId(entry.id()), new GridPoint(entry.x1(), entry.y1()),
                new GridPoint(entry.x2(), entry.y2()));
    }

    static TextLabel label(LabelEntry entry) {
        return new TextLabel(new ElementId(entry.id()), new GridPoint(entry.x(), entry.y()), entry.text());
    }

    private interface ElementSupplier {
        DocumentElement create();
    }

    private static void add(CircuitDocument document, ElementSupplier supplier) throws CircuitFormatException {
        try {
            document.add(supplier.create());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT, exception.getMessage(), exception);
        }
    }
}
