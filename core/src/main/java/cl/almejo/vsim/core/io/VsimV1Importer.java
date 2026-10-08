package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

/** Imports circuits saved by VSim 1 (pixel coordinates, "gates" and "connections"). */
public final class VsimV1Importer {

    private final ComponentTypeRegistry registry;

    public VsimV1Importer(ComponentTypeRegistry registry) {
        this.registry = registry;
    }

    public CircuitDocument importDocument(JsonNode root) throws CircuitFormatException {
        CircuitDocument document = new CircuitDocument();
        JsonNode gates = root.path("gates");
        JsonNode connections = root.path("connections");
        if (!gates.isMissingNode() && !gates.isArray()) {
            throw invalid("gates must be an array");
        }
        if (!connections.isMissingNode() && !connections.isArray()) {
            throw invalid("connections must be an array");
        }
        for (JsonNode gate : gates) {
            if (!gate.isObject()) {
                throw invalid("gate must be an object");
            }
            JsonNode typeNode = gate.path("type");
            if (!typeNode.isTextual() || typeNode.asText().isBlank()) {
                throw invalid("gate type must be a non-blank string");
            }
            String typeId = typeNode.asText();
            ComponentType type = registry.find(typeId).orElseThrow(() -> new CircuitFormatException(
                    CircuitFormatException.Reason.UNKNOWN_COMPONENT_TYPE, typeId));
            JsonNode position = gate.path("position");
            if (!position.isObject()) {
                throw invalid("gate position must be an object");
            }
            JsonNode parameters = gate.path("parameters");
            JsonNode text = parameters.path("text");
            String label = (typeId.equals("switch") || typeId.equals("led")) && text.isTextual() ? text.asText() : "";
            try {
                document.add(new Component(document.newComponentId(), type.id(),
                        GridPoint.fromPixels(pixel(position, "x"), pixel(position, "y")),
                        Rotation.R0,
                        type.resolveParameters(parameters(typeId, parameters)),
                        label));
            } catch (IllegalArgumentException exception) {
                throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT, exception.getMessage(), exception);
            }
        }
        for (JsonNode connection : connections) {
            if (!connection.isObject()) {
                throw invalid("connection must be an object");
            }
            int startX = pixel(connection, "xi");
            int startY = pixel(connection, "yi");
            int endX = pixel(connection, "xf");
            int endY = pixel(connection, "yf");
            try {
                GridPoint start = GridPoint.fromPixels(startX, startY);
                GridPoint end = GridPoint.fromPixels(endX, endY);
                if (start.equals(end)) {
                    continue;
                }
                document.add(new Wire(document.newWireId(), start, end));
            } catch (IllegalArgumentException exception) {
                throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT, exception.getMessage(), exception);
            }
        }
        return document;
    }

    private static int pixel(JsonNode parent, String field) throws CircuitFormatException {
        JsonNode node = parent.path(field);
        if (!node.isNumber() || !node.canConvertToInt()) {
            throw invalid("'" + field + "' must be an integer");
        }
        return node.asInt();
    }

    private static CircuitFormatException invalid(String message) {
        return new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT, message);
    }

    private static Map<String, Object> parameters(String typeId, JsonNode parameters) {
        Map<String, Object> result = new HashMap<>();
        switch (typeId) {
            case "switch" -> {
                if (parameters.has("value")) {
                    int value = parameters.path("value").asInt();
                    result.put("value", value == 1 ? "1" : value == 0 ? "0" : "Z");
                }
            }
            case "clock" -> {
                copyInteger(parameters, "time-up", result);
                copyInteger(parameters, "time-down", result);
            }
            default -> copyInteger(parameters, "delay", result);
        }
        return result;
    }

    private static void copyInteger(JsonNode parameters, String name, Map<String, Object> result) {
        if (parameters.has(name)) {
            result.put(name, parameters.path(name).asInt());
        }
    }
}
