package cl.almejo.vsim.core.component;

import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * One parameter of a {@link ComponentType}. A {@code displayOnly} parameter (M7: «Mostrar como») changes only how the
 * component is drawn: the behaviour never reads it, so the engine keeps a running component (a register's contents, a
 * pin's typed value) when only such parameters change.
 */
public record ParameterSpec(String name, ParameterKind kind, Object defaultValue,
                            int minimum, int maximum, List<String> choices, UnaryOperator<String> textNormalizer,
                            boolean displayOnly) {

    public ParameterSpec(String name, ParameterKind kind, Object defaultValue,
                         int minimum, int maximum, List<String> choices) {
        this(name, kind, defaultValue, minimum, maximum, choices, null);
    }

    public ParameterSpec(String name, ParameterKind kind, Object defaultValue,
                         int minimum, int maximum, List<String> choices, UnaryOperator<String> textNormalizer) {
        this(name, kind, defaultValue, minimum, maximum, choices, textNormalizer, false);
    }

    public ParameterSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        choices = choices == null ? List.of() : List.copyOf(choices);
        if ((kind == ParameterKind.CHOICE || kind == ParameterKind.COLOR) && !choices.contains(defaultValue)) {
            throw new IllegalArgumentException("default " + defaultValue + " is not one of " + choices);
        }
    }

    public static ParameterSpec integer(String name, int defaultValue, int minimum, int maximum) {
        return new ParameterSpec(name, ParameterKind.INTEGER, defaultValue, minimum, maximum, List.of());
    }

    public static ParameterSpec text(String name, String defaultValue) {
        return new ParameterSpec(name, ParameterKind.TEXT, defaultValue, 0, 0, List.of());
    }

    /** A text parameter stored as {@code normalizer} gives it (which throws IllegalArgumentException for bad text). */
    public static ParameterSpec text(String name, String defaultValue, UnaryOperator<String> normalizer) {
        return new ParameterSpec(name, ParameterKind.TEXT, defaultValue, 0, 0, List.of(), normalizer);
    }

    public static ParameterSpec choice(String name, String defaultValue, List<String> choices) {
        return new ParameterSpec(name, ParameterKind.CHOICE, defaultValue, 0, 0, choices);
    }

    /**
     * A choice among colour names, shown as swatches. The names are plain values (core knows no colours); the UI
     * paints each one, so a new colour parameter gets the swatch editor once its names are in the UI's colour list
     * ({@code LedPainter}'s today; a UI test checks every COLOR parameter of the standard types against it).
     */
    public static ParameterSpec color(String name, String defaultValue, List<String> colorNames) {
        return new ParameterSpec(name, ParameterKind.COLOR, defaultValue, 0, 0, colorNames);
    }

    /** This parameter marked as {@linkplain #displayOnly() display-only}. */
    public ParameterSpec asDisplayOnly() {
        return new ParameterSpec(name, kind, defaultValue, minimum, maximum, choices, textNormalizer, true);
    }

    /** Converts a raw value (from JSON or a dialog) to the canonical type, or throws IllegalArgumentException. */
    public Object normalize(Object raw) {
        Objects.requireNonNull(raw, () -> "parameter " + name + " must not be null");
        return switch (kind) {
            case INTEGER -> normalizeInteger(raw);
            case TEXT -> textNormalizer == null ? raw.toString() : textNormalizer.apply(raw.toString());
            case CHOICE, COLOR -> {
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
