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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    record CircuitFile(int formatVersion, String kind, String name, List<ComponentEntry> components,
                       List<WireEntry> wires, List<LabelEntry> labels) {
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
                document.components().stream()
                        .map(component -> new ComponentEntry(component.id().value(), component.typeId(),
                                component.position().x(), component.position().y(),
                                component.rotation().quarterTurns(), component.label(), component.parameters()))
                        .toList(),
                document.wires().stream()
                        .map(wire -> new WireEntry(wire.id().value(), wire.start().x(), wire.start().y(),
                                wire.end().x(), wire.end().y()))
                        .toList(),
                document.labels().stream()
                        .map(label -> new LabelEntry(label.id().value(), label.position().x(), label.position().y(),
                                label.text()))
                        .toList());
        try {
            return mapper.writeValueAsString(file);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize circuit", exception);
        }
    }

    public CircuitDocument read(String json) throws CircuitFormatException {
        try {
            return read(mapper.readTree(json));
        } catch (JsonProcessingException exception) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_JSON, exception.getOriginalMessage(), exception);
        }
    }

    public CircuitDocument read(JsonNode root) throws CircuitFormatException {
        if (root == null || !root.isObject()) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT, "root must be a JSON object");
        }
        int version = root.path("formatVersion").asInt(-1);
        if (version != FORMAT_VERSION) {
            throw new CircuitFormatException(CircuitFormatException.Reason.UNSUPPORTED_VERSION, "formatVersion " + version);
        }
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
        for (ComponentEntry entry : Optional.ofNullable(file.components()).orElse(List.of())) {
            ComponentType type = registry.find(entry.type()).orElseThrow(() -> new CircuitFormatException(
                    CircuitFormatException.Reason.UNKNOWN_COMPONENT_TYPE, String.valueOf(entry.type())));
            add(document, () -> new Component(new ElementId(entry.id()), type.id(), new GridPoint(entry.x(), entry.y()),
                    Rotation.fromQuarterTurns(entry.rotation()), type.resolveParameters(entry.parameters()), entry.label()));
        }
        for (WireEntry entry : Optional.ofNullable(file.wires()).orElse(List.of())) {
            add(document, () -> new Wire(new ElementId(entry.id()), new GridPoint(entry.x1(), entry.y1()),
                    new GridPoint(entry.x2(), entry.y2())));
        }
        for (LabelEntry entry : Optional.ofNullable(file.labels()).orElse(List.of())) {
            add(document, () -> new TextLabel(new ElementId(entry.id()), new GridPoint(entry.x(), entry.y()), entry.text()));
        }
        return document;
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
