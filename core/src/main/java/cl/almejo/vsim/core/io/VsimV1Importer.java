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
        for (JsonNode gate : root.path("gates")) {
            String typeId = gate.path("type").asText();
            ComponentType type = registry.find(typeId).orElseThrow(() -> new CircuitFormatException(
                    CircuitFormatException.Reason.UNKNOWN_COMPONENT_TYPE, typeId));
            JsonNode position = gate.path("position");
            JsonNode parameters = gate.path("parameters");
            try {
                document.add(new Component(document.newComponentId(), type.id(),
                        GridPoint.fromPixels(position.path("x").asInt(), position.path("y").asInt()),
                        Rotation.R0,
                        type.resolveParameters(parameters(typeId, parameters)),
                        parameters.path("text").asText("")));
            } catch (IllegalArgumentException exception) {
                throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT, exception.getMessage(), exception);
            }
        }
        for (JsonNode connection : root.path("connections")) {
            GridPoint start = GridPoint.fromPixels(connection.path("xi").asInt(), connection.path("yi").asInt());
            GridPoint end = GridPoint.fromPixels(connection.path("xf").asInt(), connection.path("yf").asInt());
            if (start.equals(end)) {
                continue;
            }
            try {
                document.add(new Wire(document.newWireId(), start, end));
            } catch (IllegalArgumentException exception) {
                throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT, exception.getMessage(), exception);
            }
        }
        return document;
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
