package cl.almejo.vsim.core.component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * One parameter of a {@link ComponentType}. A {@code displayOnly} parameter (M7: «Mostrar como») changes only how the
 * component is drawn: the behaviour never reads it, so the engine keeps a running component (a register's contents, a
 * pin's typed value) when only such parameters change. A {@code hidden} parameter (memory contents) is not offered as
 * an editable field by the UI. A {@code liveValue} parameter is the value an input drives (a Switch's «Valor»): when
 * only it changes the engine keeps the running component and hands it the new value ({@link Behavior#takeLiveValue}),
 * so a value the component already drives fires nothing.
 *
 * <p>How the inspector presents it: a {@code group} puts it under a header («Pantalla», «Aspecto»: bundle key
 * {@code parameter.group.<group>}); a {@code multiline} text gets a box of several lines; a {@code visibility}
 * condition shows its row only while another parameter has a given value (the encoding only with 8 bits). None of
 * them changes what is stored or simulated.
 */
public record ParameterSpec(String name, ParameterKind kind, Object defaultValue,
                            int minimum, int maximum, List<String> choices, UnaryOperator<String> textNormalizer,
                            boolean displayOnly, boolean hidden, boolean liveValue,
                            Optional<String> group, boolean multiline, Optional<VisibilityCondition> visibility) {

    public ParameterSpec(String name, ParameterKind kind, Object defaultValue,
                         int minimum, int maximum, List<String> choices, UnaryOperator<String> textNormalizer,
                         boolean displayOnly, boolean hidden, boolean liveValue) {
        this(name, kind, defaultValue, minimum, maximum, choices, textNormalizer, displayOnly, hidden, liveValue,
                Optional.empty(), false, Optional.empty());
    }

    public ParameterSpec(String name, ParameterKind kind, Object defaultValue,
                         int minimum, int maximum, List<String> choices) {
        this(name, kind, defaultValue, minimum, maximum, choices, null);
    }

    public ParameterSpec(String name, ParameterKind kind, Object defaultValue,
                         int minimum, int maximum, List<String> choices, UnaryOperator<String> textNormalizer) {
        this(name, kind, defaultValue, minimum, maximum, choices, textNormalizer, false);
    }

    public ParameterSpec(String name, ParameterKind kind, Object defaultValue,
                         int minimum, int maximum, List<String> choices, UnaryOperator<String> textNormalizer,
                         boolean displayOnly) {
        this(name, kind, defaultValue, minimum, maximum, choices, textNormalizer, displayOnly, false, false);
    }

    public ParameterSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        choices = choices == null ? List.of() : List.copyOf(choices);
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(visibility, "visibility");
        if (multiline && kind != ParameterKind.TEXT) {
            throw new IllegalArgumentException("only a text parameter can be multiline: " + name);
        }
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

    /** An on/off parameter, shown as a check box; stored as a {@link Boolean}. */
    public static ParameterSpec bool(String name, boolean defaultValue) {
        return new ParameterSpec(name, ParameterKind.BOOLEAN, defaultValue, 0, 0, List.of());
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
        return new ParameterSpec(name, kind, defaultValue, minimum, maximum, choices, textNormalizer, true, hidden,
                liveValue, group, multiline, visibility);
    }

    /**
     * This parameter marked as {@linkplain #hidden() hidden}: it is part of the component (saved, undone, copied) but
     * the inspector and the configure dialog do not show it as a field.
     */
    public ParameterSpec asHidden() {
        return new ParameterSpec(name, kind, defaultValue, minimum, maximum, choices, textNormalizer, displayOnly, true,
                liveValue, group, multiline, visibility);
    }

    /**
     * This parameter marked as the {@linkplain #liveValue() live value} of an input: the behaviour built from it takes
     * a new one with {@link Behavior#takeLiveValue}.
     */
    public ParameterSpec asLiveValue() {
        return new ParameterSpec(name, kind, defaultValue, minimum, maximum, choices, textNormalizer, displayOnly,
                hidden, true, group, multiline, visibility);
    }

    /**
     * This parameter shown under the header of {@code groupKey} (bundle key {@code parameter.group.<groupKey>}); the
     * inspector orders the groups by their first parameter.
     */
    public ParameterSpec inGroup(String groupKey) {
        Objects.requireNonNull(groupKey, "groupKey");
        return new ParameterSpec(name, kind, defaultValue, minimum, maximum, choices, textNormalizer, displayOnly,
                hidden, liveValue, Optional.of(groupKey), multiline, visibility);
    }

    /**
     * This text parameter edited in a box of several lines (an expected output), committed when the box loses the
     * focus or with Ctrl+Enter.
     *
     * @throws IllegalArgumentException for a parameter that is not a text
     */
    public ParameterSpec asMultiline() {
        return new ParameterSpec(name, kind, defaultValue, minimum, maximum, choices, textNormalizer, displayOnly,
                hidden, liveValue, group, true, visibility);
    }

    /**
     * This parameter's row shown only while parameter {@code parameter} of the same type has the value {@code value}
     * (checked by {@link ComponentType}); the value is kept while the row is hidden.
     */
    public ParameterSpec visibleWhen(String parameter, Object value) {
        return new ParameterSpec(name, kind, defaultValue, minimum, maximum, choices, textNormalizer, displayOnly,
                hidden, liveValue, group, multiline, Optional.of(new VisibilityCondition(parameter, value)));
    }

    /** Converts a raw value (from JSON or a dialog) to the canonical type, or throws IllegalArgumentException. */
    public Object normalize(Object raw) {
        Objects.requireNonNull(raw, () -> "parameter " + name + " must not be null");
        return switch (kind) {
            case INTEGER -> normalizeInteger(raw);
            case BOOLEAN -> normalizeBoolean(raw);
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

    /**
     * {@link #normalize} for a value that may be missing or unreadable (half-typed text in a form): the default for
     * null, empty when it cannot be read.
     */
    public Optional<Object> normalizeIfValid(Object raw) {
        if (raw == null) {
            return Optional.of(defaultValue);
        }
        try {
            return Optional.of(normalize(raw));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private Boolean normalizeBoolean(Object raw) {
        if (raw instanceof Boolean flag) {
            return flag;
        }
        if (raw instanceof String text && (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false"))) {
            return Boolean.parseBoolean(text);
        }
        throw new IllegalArgumentException("parameter " + name + " must be true or false: " + raw);
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
