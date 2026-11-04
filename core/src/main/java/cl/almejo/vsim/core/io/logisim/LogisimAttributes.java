package cl.almejo.vsim.core.io.logisim;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A component's attributes as Logisim's own text ({@code east}, {@code 32}, {@code 0xff}, {@code SansSerif plain 12},
 * {@code #f00000}), with Logisim 2.7's default already in place for every attribute the file left out
 * ({@link LogisimDefaults}). Unknown attributes are kept as written.
 */
public final class LogisimAttributes {

    private final Map<String, String> values;

    private LogisimAttributes(Map<String, String> values) {
        this.values = values;
    }

    /** These values, in their order (defaults first, in Logisim's attribute order, then the file's extra ones). */
    public static LogisimAttributes of(Map<String, String> values) {
        return new LogisimAttributes(Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }

    /** Whether the component has attribute {@code name} (written in the file or one of its defaults). */
    public boolean has(String name) {
        return values.containsKey(name);
    }

    /**
     * The text of attribute {@code name}.
     *
     * @throws IllegalArgumentException when the component has no such attribute
     */
    public String text(String name) {
        String value = values.get(Objects.requireNonNull(name, "name"));
        if (value == null) {
            throw new IllegalArgumentException("no attribute " + name);
        }
        return value;
    }

    /** The text of attribute {@code name}, or {@code fallback} when the component has none. */
    public String textOr(String name, String fallback) {
        return values.getOrDefault(name, fallback);
    }

    /**
     * Attribute {@code name} as a number: decimal, or hexadecimal with {@code 0x} as Logisim writes values
     * ({@code 0xffffffff} is read without sign).
     *
     * @throws IllegalArgumentException when the attribute is missing or not a number
     */
    public long number(String name) {
        String text = text(name).strip();
        try {
            if (text.startsWith("0x") || text.startsWith("0X")) {
                return Long.parseLong(text.substring(2), 16);
            }
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("attribute " + name + " is not a number: " + text, exception);
        }
    }

    /**
     * Attribute {@code name} as an {@code int} (widths, counts, sizes).
     *
     * @throws IllegalArgumentException when the attribute is missing, not a number or does not fit an {@code int}
     */
    public int integer(String name) {
        long number = number(name);
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("attribute " + name + " is too big: " + number);
        }
        return (int) number;
    }

    /**
     * Attribute {@code name} as a boolean ({@code true} / {@code false}).
     *
     * @throws IllegalArgumentException when the attribute is missing or not a boolean
     */
    public boolean bool(String name) {
        String text = text(name).strip();
        if (text.equals("true")) {
            return true;
        }
        if (text.equals("false")) {
            return false;
        }
        throw new IllegalArgumentException("attribute " + name + " is not a boolean: " + text);
    }

    /** Every attribute, in order; unmodifiable. */
    public Map<String, String> asMap() {
        return values;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LogisimAttributes attributes && attributes.values.equals(values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
