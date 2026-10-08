package cl.almejo.vsim.core.component;

import java.util.List;
import java.util.Objects;

public record ParameterSpec(String name, ParameterKind kind, Object defaultValue,
                            int minimum, int maximum, List<String> choices) {

    public ParameterSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        choices = choices == null ? List.of() : List.copyOf(choices);
        if (kind == ParameterKind.CHOICE && !choices.contains(defaultValue)) {
            throw new IllegalArgumentException("default " + defaultValue + " is not one of " + choices);
        }
    }

    public static ParameterSpec integer(String name, int defaultValue, int minimum, int maximum) {
        return new ParameterSpec(name, ParameterKind.INTEGER, defaultValue, minimum, maximum, List.of());
    }

    public static ParameterSpec text(String name, String defaultValue) {
        return new ParameterSpec(name, ParameterKind.TEXT, defaultValue, 0, 0, List.of());
    }

    public static ParameterSpec choice(String name, String defaultValue, List<String> choices) {
        return new ParameterSpec(name, ParameterKind.CHOICE, defaultValue, 0, 0, choices);
    }

    /** Converts a raw value (from JSON or a dialog) to the canonical type, or throws IllegalArgumentException. */
    public Object normalize(Object raw) {
        Objects.requireNonNull(raw, () -> "parameter " + name + " must not be null");
        return switch (kind) {
            case INTEGER -> normalizeInteger(raw);
            case TEXT -> raw.toString();
            case CHOICE -> {
                String text = raw.toString();
                if (!choices.contains(text)) {
                    throw new IllegalArgumentException("parameter " + name + ": " + text + " is not one of " + choices);
                }
                yield text;
            }
        };
    }

    private Integer normalizeInteger(Object raw) {
        long number;
        if (raw instanceof Number numeric) {
            number = numeric.longValue();
        } else {
            try {
                number = Long.parseLong(raw.toString().trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("parameter " + name + " must be an integer: " + raw, exception);
            }
        }
        if (number < minimum || number > maximum) {
            throw new IllegalArgumentException("parameter " + name + " must be between "
                    + minimum + " and " + maximum + ": " + number);
        }
        return (int) number;
    }
}
