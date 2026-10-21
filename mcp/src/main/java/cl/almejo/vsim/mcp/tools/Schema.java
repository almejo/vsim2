package cl.almejo.vsim.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small builders for the tools' JSON schemas (draft 2020-12, what the MCP SDK validates against). Input objects refuse
 * properties they do not name, so a misspelt argument is an error instead of being ignored.
 */
final class Schema {

    private Schema() {
    }

    /** An object schema: build it with {@link ObjectSchema#property} and {@link ObjectSchema#required}. */
    static ObjectSchema object(String description) {
        return new ObjectSchema(description);
    }

    static Map<String, Object> string(String description) {
        return with(Map.of("type", "string"), description);
    }

    static Map<String, Object> nonEmptyString(String description) {
        Map<String, Object> schema = new LinkedHashMap<>(string(description));
        schema.put("minLength", 1);
        return schema;
    }

    static Map<String, Object> enumeration(String description, List<String> values) {
        Map<String, Object> schema = new LinkedHashMap<>(string(description));
        schema.put("enum", List.copyOf(values));
        return schema;
    }

    static Map<String, Object> constant(String value) {
        return Map.of("const", value);
    }

    static Map<String, Object> integer(String description) {
        return with(Map.of("type", "integer"), description);
    }

    static Map<String, Object> integer(String description, long minimum, long maximum) {
        Map<String, Object> schema = new LinkedHashMap<>(integer(description));
        schema.put("minimum", minimum);
        schema.put("maximum", maximum);
        return schema;
    }

    static Map<String, Object> bool(String description) {
        return with(Map.of("type", "boolean"), description);
    }

    static Map<String, Object> array(String description, Map<String, Object> items) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("items", items);
        return with(schema, description);
    }

    static Map<String, Object> array(String description, Map<String, Object> items, int minimumItems,
                                     int maximumItems) {
        Map<String, Object> schema = new LinkedHashMap<>(array(description, items));
        schema.put("minItems", minimumItems);
        schema.put("maxItems", maximumItems);
        return schema;
    }

    /** An object whose keys are free and whose values follow {@code values} (parameters, renamed pins…). */
    static Map<String, Object> mapOf(String description, Map<String, Object> values) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", values);
        return with(schema, description);
    }

    /** Any JSON value (a parameter value: a number, a text…). */
    static Map<String, Object> anyValue(String description) {
        return with(Map.of(), description);
    }

    static Map<String, Object> oneOf(String description, List<Map<String, Object>> alternatives) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("oneOf", List.copyOf(alternatives));
        return with(schema, description);
    }

    /** A grid point {@code {x, y}}. */
    static Map<String, Object> point(String description) {
        return object(description)
                .property("x", integer("Grid units to the right"))
                .property("y", integer("Grid units down"))
                .required("x", "y")
                .build();
    }

    /** A box {@code {x, y, width, height}} in grid units. */
    static Map<String, Object> bounds(String description) {
        return object(description)
                .property("x", integer("Left edge"))
                .property("y", integer("Top edge"))
                .property("width", integer("Width", 0, Integer.MAX_VALUE))
                .property("height", integer("Height", 0, Integer.MAX_VALUE))
                .required("x", "y", "width", "height")
                .build();
    }

    /** An object of the results: it names the main properties and lets others through. */
    static ObjectSchema result(String description) {
        return new ObjectSchema(description).open();
    }

    private static Map<String, Object> with(Map<String, Object> schema, String description) {
        if (description == null || description.isEmpty()) {
            return schema;
        }
        Map<String, Object> described = new LinkedHashMap<>(schema);
        described.put("description", description);
        return described;
    }

    /** An object schema under construction. */
    static final class ObjectSchema {
        private final String description;
        private final Map<String, Object> properties = new LinkedHashMap<>();
        private final List<String> required = new ArrayList<>();
        private boolean open;

        private ObjectSchema(String description) {
            this.description = description;
        }

        ObjectSchema property(String name, Map<String, Object> schema) {
            properties.put(name, schema);
            return this;
        }

        ObjectSchema required(String... names) {
            required.addAll(List.of(names));
            return this;
        }

        /** Lets properties it does not name through (results only). */
        ObjectSchema open() {
            open = true;
            return this;
        }

        Map<String, Object> build() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", new LinkedHashMap<>(properties));
            if (!required.isEmpty()) {
                schema.put("required", List.copyOf(required));
            }
            if (!open) {
                schema.put("additionalProperties", false);
            }
            return with(schema, description);
        }
    }
}
