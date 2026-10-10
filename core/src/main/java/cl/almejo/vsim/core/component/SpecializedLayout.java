package cl.almejo.vsim.core.component;

import java.util.Map;
import java.util.Objects;

/**
 * The layout of a type specialized for {@code parameters} (raw, as the component holds them): it lays out like
 * {@code base}, and tells {@link ComponentType} that a component with those same parameters has the type's own size
 * and pins, so its pin locations and bounds need no new parameter resolution. Equal to its base, so a specialized type
 * stays equal to any other type of the same shape and layout.
 */
final class SpecializedLayout implements ComponentLayout {

    private final ComponentLayout base;
    private final Map<String, Object> parameters;

    SpecializedLayout(ComponentLayout base, Map<String, Object> parameters) {
        this.base = Objects.requireNonNull(base, "base");
        this.parameters = parameters;
    }

    /** {@code layout} without any specialization. */
    static ComponentLayout baseOf(ComponentLayout layout) {
        return layout instanceof SpecializedLayout specialized ? specialized.base : layout;
    }

    /** Whether {@code componentParameters} are the ones this layout was specialized for. */
    boolean isFor(Map<String, Object> componentParameters) {
        return componentParameters == parameters
                || componentParameters != null && componentParameters.equals(parameters);
    }

    @Override
    public ComponentShape shapeFor(Map<String, Object> resolvedParameters) {
        return base.shapeFor(resolvedParameters);
    }

    @Override
    public boolean equals(Object other) {
        return baseOf(this).equals(other instanceof ComponentLayout layout ? baseOf(layout) : other);
    }

    @Override
    public int hashCode() {
        return base.hashCode();
    }
}
