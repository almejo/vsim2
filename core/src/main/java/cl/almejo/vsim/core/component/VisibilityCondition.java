package cl.almejo.vsim.core.component;

import java.util.Objects;

/**
 * When a parameter's row is shown ({@link ParameterSpec#visibleWhen}): while parameter {@code parameter} of the same
 * type has the value {@code value} (normalized, as {@link ParameterSpec#normalize} gives it).
 */
public record VisibilityCondition(String parameter, Object value) {

    public VisibilityCondition {
        Objects.requireNonNull(parameter, "parameter");
        Objects.requireNonNull(value, "value");
    }
}
