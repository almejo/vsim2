package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Turns the automation layer's results into plain JSON values (maps, lists, strings, numbers, booleans) for the
 * structured tool results: a record becomes an object with its components' names, an empty {@link Optional} is left
 * out, an enum is its name, ids and versions are their values, points and boxes are {@code {x, y}} and
 * {@code {x, y, width, height}}. Picture bytes are never put in: they travel as image content.
 */
final class Json {

    private Json() {
    }

    /** {@code value} as a JSON object; it must be a record or a map. */
    static Map<String, Object> object(Object value) {
        if (toJson(value) instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> object = (Map<String, Object>) map;
            return object;
        }
        throw new IllegalArgumentException("not a JSON object: " + value);
    }

    static Object toJson(Object value) {
        return switch (value) {
            case null -> null;
            case String text -> text;
            case Boolean flag -> flag;
            case Number number -> number;
            case Character character -> character.toString();
            case Enum<?> constant -> constant.name();
            case DocumentId id -> id.value();
            case DocumentVersion version -> version.value();
            case GridPoint point -> point(point);
            case Bounds bounds -> bounds(bounds);
            case Optional<?> optional -> optional.map(Json::toJson).orElse(null);
            case OptionalInt optional -> optional.isPresent() ? optional.getAsInt() : null;
            case OptionalLong optional -> optional.isPresent() ? optional.getAsLong() : null;
            case Map<?, ?> map -> map(map);
            case Collection<?> collection -> list(collection);
            case byte[] ignored -> null;
            case Record record -> record(record);
            default -> value.toString();
        };
    }

    static Map<String, Object> point(GridPoint point) {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("x", point.x());
        object.put("y", point.y());
        return object;
    }

    static Map<String, Object> bounds(Bounds bounds) {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("x", bounds.x());
        object.put("y", bounds.y());
        object.put("width", bounds.width());
        object.put("height", bounds.height());
        return object;
    }

    private static Map<String, Object> map(Map<?, ?> map) {
        Map<String, Object> object = new LinkedHashMap<>();
        map.entrySet().stream()
                .sorted((first, second) -> String.valueOf(first.getKey()).compareTo(String.valueOf(second.getKey())))
                .forEach(entry -> {
                    Object converted = toJson(entry.getValue());
                    if (converted != null) {
                        object.put(String.valueOf(entry.getKey()), converted);
                    }
                });
        return object;
    }

    private static List<Object> list(Collection<?> collection) {
        List<Object> list = new ArrayList<>(collection.size());
        for (Object element : collection) {
            list.add(toJson(element));
        }
        return list;
    }

    private static Map<String, Object> record(Record record) {
        Map<String, Object> object = new LinkedHashMap<>();
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            Object converted = toJson(read(component, record));
            if (converted != null) {
                object.put(component.getName(), converted);
            }
        }
        return object;
    }

    private static Object read(RecordComponent component, Record record) {
        try {
            var accessor = component.getAccessor();
            accessor.setAccessible(true);
            return accessor.invoke(record);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("cannot read " + component.getName() + " of " + record.getClass(),
                    exception);
        }
    }
}
