package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.geometry.GridSize;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A part whose size comes from an integer parameter that the user may also change by dragging one of its edges on the
 * canvas (the Teclado's «Ancho»: its right edge). The parameter's range is the range of the drag; the size each value
 * gives comes from the type's layout, so a parameter that is not counted in grid units (a Terminal's columns) works
 * the same. {@code edge} is the edge that moves, in the part's own (unrotated) coordinates: the opposite one and the
 * part's position stay put.
 *
 * <p>Declared on the type with {@link ComponentType#withResize}; the canvas offers the grip only for such types.
 */
public record ResizeSpec(String parameter, Edge edge) {

    /** The edge that a larger value moves outwards. */
    public enum Edge {
        /** The right edge: the value sets the part's width. */
        RIGHT,
        /** The bottom edge: the value sets the part's height. */
        BOTTOM
    }

    public ResizeSpec {
        Objects.requireNonNull(parameter, "parameter");
        Objects.requireNonNull(edge, "edge");
    }

    /** The smallest value of the parameter, the narrowest the drag goes. */
    public int minimum(ComponentType type) {
        return spec(type).minimum();
    }

    /** The largest value of the parameter, the widest the drag goes. */
    public int maximum(ComponentType type) {
        return spec(type).maximum();
    }

    /** The parameter's value in {@code parameters} (its default when they do not set it). */
    public int valueOf(ComponentType type, Map<String, Object> parameters) {
        return (Integer) type.resolveParameters(parameters).get(parameter);
    }

    /** {@code parameters} with the parameter set to {@code value}. */
    public Map<String, Object> parametersWith(Map<String, Object> parameters, int value) {
        Map<String, Object> changed = new HashMap<>(parameters);
        changed.put(parameter, value);
        return Map.copyOf(changed);
    }

    /** The part's size along the moving edge's axis, in grid units, with {@code parameters}. */
    public int extentOf(ComponentType type, Map<String, Object> parameters) {
        GridSize size = type.specializedFor(parameters).size();
        return edge == Edge.RIGHT ? size.width() : size.height();
    }

    /**
     * The value whose size along the axis is nearest to {@code extent} grid units, inside the parameter's range: where
     * an edge dragged to {@code extent} lands. On a tie the smaller value wins.
     */
    public int valueFor(ComponentType type, Map<String, Object> parameters, double extent) {
        int best = minimum(type);
        double bestDistance = Double.MAX_VALUE;
        for (int value = minimum(type); value <= maximum(type); value++) {
            double distance = Math.abs(extentOf(type, parametersWith(parameters, value)) - extent);
            if (distance < bestDistance) {
                best = value;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Checks that {@code type} has the parameter and that it is an integer. */
    void checkFor(ComponentType type) {
        spec(type);
    }

    private ParameterSpec spec(ComponentType type) {
        ParameterSpec spec = type.parameterNamed(parameter).orElseThrow(() -> new IllegalArgumentException(
                type.id() + " has no parameter " + parameter + " to resize with"));
        if (spec.kind() != ParameterKind.INTEGER) {
            throw new IllegalArgumentException(type.id() + "'s " + parameter + " is not an integer");
        }
        return spec;
    }
}
