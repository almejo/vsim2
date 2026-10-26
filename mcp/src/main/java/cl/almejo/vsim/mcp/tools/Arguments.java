package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.PinReference;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A tool call's arguments, already checked against the tool's input schema, read into the automation layer's types.
 * Anything still wrong (a value the schema cannot express) is an {@link AutomationError#INVALID_PARAMETER} naming the
 * argument.
 */
final class Arguments {

    private final Map<String, Object> values;
    private final String path;

    Arguments(Map<String, Object> values) {
        this(values == null ? Map.of() : values, "");
    }

    private Arguments(Map<String, Object> values, String path) {
        this.values = values;
        this.path = path;
    }

    boolean has(String name) {
        return values.get(name) != null;
    }

    String string(String name) {
        return optionalString(name).orElseThrow(() -> invalid(name, "is required"));
    }

    Optional<String> optionalString(String name) {
        Object value = values.get(name);
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof String text) {
            return Optional.of(text);
        }
        throw invalid(name, "must be a string");
    }

    String string(String name, String fallback) {
        return optionalString(name).orElse(fallback);
    }

    long longValue(String name) {
        Object value = values.get(name);
        if (value == null) {
            throw invalid(name, "is required");
        }
        if (value instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue())) {
            return number.longValue();
        }
        throw invalid(name, "must be an integer");
    }

    long longValue(String name, long fallback) {
        return has(name) ? longValue(name) : fallback;
    }

    int integer(String name) {
        long value = longValue(name);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw invalid(name, "is out of range");
        }
        return (int) value;
    }

    int integer(String name, int fallback) {
        return has(name) ? integer(name) : fallback;
    }

    boolean bool(String name, boolean fallback) {
        Object value = values.get(name);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        throw invalid(name, "must be true or false");
    }

    DocumentId document() {
        return new DocumentId(string("document"));
    }

    DocumentVersion version(String name) {
        return new DocumentVersion(longValue(name));
    }

    <E extends Enum<E>> E enumeration(String name, Class<E> type) {
        String text = string(name);
        try {
            return Enum.valueOf(type, text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid(name, "is not one of " + List.of(type.getEnumConstants()));
        }
    }

    <E extends Enum<E>> E enumeration(String name, Class<E> type, E fallback) {
        return has(name) ? enumeration(name, type) : fallback;
    }

    List<String> strings(String name) {
        Object value = values.get(name);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw invalid(name, "must be a list of strings");
        }
        List<String> strings = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof String text)) {
                throw invalid(name, "must be a list of strings");
            }
            strings.add(text);
        }
        return strings;
    }

    Set<String> stringSet(String name) {
        return new LinkedHashSet<>(strings(name));
    }

    /** A JSON object argument as it came (parameter values), empty when absent. */
    Map<String, Object> map(String name) {
        Object value = values.get(name);
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw invalid(name, "must be an object");
        }
        return copyOf(map);
    }

    private static Map<String, Object> copyOf(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, element) -> copy.put(String.valueOf(key), element));
        return copy;
    }

    /** A JSON object of strings (pin names, expected values), empty when absent. */
    Map<String, String> stringMap(String name) {
        Map<String, String> strings = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map(name).entrySet()) {
            if (!(entry.getValue() instanceof String text)) {
                throw invalid(name + "." + entry.getKey(), "must be a string");
            }
            strings.put(entry.getKey(), text);
        }
        return strings;
    }

    Arguments object(String name) {
        return optionalObject(name).orElseThrow(() -> invalid(name, "is required"));
    }

    Optional<Arguments> optionalObject(String name) {
        Object value = values.get(name);
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof Map<?, ?>)) {
            throw invalid(name, "must be an object");
        }
        return Optional.of(new Arguments(map(name), qualified(name)));
    }

    List<Arguments> objects(String name) {
        Object value = values.get(name);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw invalid(name, "must be a list of objects");
        }
        List<Arguments> objects = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            if (!(list.get(index) instanceof Map<?, ?> element)) {
                throw invalid(name + "[" + index + "]", "must be an object");
            }
            objects.add(new Arguments(copyOf(element), qualified(name + "[" + index + "]")));
        }
        return objects;
    }

    GridPoint point() {
        return new GridPoint(integer("x"), integer("y"));
    }

    List<GridPoint> points(String name) {
        return objects(name).stream().map(Arguments::point).toList();
    }

    Optional<Bounds> optionalBounds(String name) {
        return optionalObject(name).map(box -> {
            try {
                return new Bounds(box.integer("x"), box.integer("y"), box.integer("width"), box.integer("height"));
            } catch (IllegalArgumentException exception) {
                throw invalid(name, exception.getMessage());
            }
        });
    }

    /** {@code {elementId, pinName, index}} as a pin reference; index 0 when absent. */
    PinReference pin() {
        return new PinReference(string("elementId"), string("pinName"), integer("index", 0));
    }

    AutomationException invalid(String name, String problem) {
        String argument = qualified(name);
        return new AutomationException(AutomationError.INVALID_PARAMETER, "The argument " + argument + " " + problem,
                Map.of("argument", argument));
    }

    private String qualified(String name) {
        return path.isEmpty() ? name : path + "." + name;
    }
}
