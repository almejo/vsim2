package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.document.Guide;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static cl.almejo.vsim.core.io.CircuitFormatException.invalid;

/**
 * The JSON shape of the alignment guides of a circuit file: {@code {"orientation": "horizontal" | "vertical",
 * "position": <int>}}. Entries are read straight from the JSON tree, so a fractional or quoted position is refused
 * instead of being coerced by the mapper.
 */
final class GuideEntries {

    static final String KEY = "guides";
    private static final String ORIENTATION = "orientation";
    private static final String POSITION = "position";

    record GuideEntry(String orientation, int position) {
    }

    private GuideEntries() {
    }

    static GuideEntry entry(Guide guide) {
        return new GuideEntry(name(guide.orientation()), guide.position());
    }

    /** {@code root} without its guides, so the record mapping never sees (nor coerces) them. */
    static JsonNode withoutGuides(JsonNode root) {
        if (!(root instanceof ObjectNode object) || !object.has(KEY)) {
            return root;
        }
        ObjectNode copy = object.objectNode();
        copy.setAll(object);
        copy.remove(KEY);
        return copy;
    }

    /**
     * The guides of {@code root}, in file order; an absent or null key is no guides. A malformed entry fails with
     * {@code INVALID_ELEMENT} and a detail that starts with {@code guides[<index>]: }.
     */
    static List<Guide> read(JsonNode root) throws CircuitFormatException {
        JsonNode node = root.get(KEY);
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw invalid(KEY + ": must be a list");
        }
        List<Guide> guides = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            guides.add(guide(node.get(index), KEY + "[" + index + "]: "));
        }
        return guides;
    }

    private static Guide guide(JsonNode entry, String where) throws CircuitFormatException {
        if (entry.isNull()) {
            throw invalid(where + "null entry");
        }
        if (!entry.isObject()) {
            throw invalid(where + "not an object");
        }
        return new Guide(orientation(entry.get(ORIENTATION), where), position(entry.get(POSITION), where));
    }

    private static Guide.Orientation orientation(JsonNode node, String where) throws CircuitFormatException {
        String text = node != null && node.isTextual() ? node.textValue() : String.valueOf(node);
        for (Guide.Orientation orientation : Guide.Orientation.values()) {
            if (name(orientation).equals(text)) {
                return orientation;
            }
        }
        throw invalid(where + "unknown orientation " + text);
    }

    private static int position(JsonNode node, String where) throws CircuitFormatException {
        if (node == null || node.isNull()) {
            throw invalid(where + "missing position");
        }
        if (!node.isIntegralNumber()) {
            throw invalid(where + "position must be whole: " + node);
        }
        if (!node.canConvertToInt()) {
            throw invalid(where + "position out of range: " + node);
        }
        return node.intValue();
    }

    /** The lower-case name the file uses, like the other words of the format ({@code "circuit"}). */
    private static String name(Guide.Orientation orientation) {
        return orientation.name().toLowerCase(Locale.ROOT);
    }

}
