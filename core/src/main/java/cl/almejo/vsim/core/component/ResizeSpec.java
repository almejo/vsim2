package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.geometry.GridSize;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A part whose size comes from integer parameters that the user may also change by dragging it on the canvas: one
 * edge for one parameter (the Teclado's «Ancho»: its right edge), or the bottom-right corner for two (the Terminal's
 * columns and rows, Task 7b). Each {@link Axis} names its parameter and the edge it moves; the parameter's range is the
 * range of the drag and the size each value gives comes from the type's layout, so a parameter that is not counted in
 * grid units works the same. The edges are in the part's own (unrotated) coordinates: the opposite ones and the part's
 * position stay put.
 *
 * <p>Declared on the type with {@link ComponentType#withResize}; the canvas offers the grip only for such types. The
 * one-axis accessors ({@link #parameter()}, {@link #edge()}, {@link #minimum}, …) speak of the first axis.
 */
public record ResizeSpec(List<Axis> axes) {

    /** The edge that a larger value moves outwards. */
    public enum Edge {
        /** The right edge: the value sets the part's width. */
        RIGHT,
        /** The bottom edge: the value sets the part's height. */
        BOTTOM
    }

    /** How far, in grid units, an edge moves for each step of its value, given the part's resolved parameters. */
    @FunctionalInterface
    public interface Step {
        double unitsPerValue(Map<String, Object> resolvedParameters);
    }

    /**
     * One parameter that one edge follows. Without a {@code step} a dragged edge lands on the value whose size is
     * nearest to where it was dropped; with one (a Terminal's cells, whose size depends on «Tamaño de letra») the value
     * moves one for every {@code step} grid units dragged, so every value is reachable even when the part's size is
     * rounded to whole units.
     */
    public record Axis(String parameter, Edge edge, Optional<Step> step) {

        public Axis {
            Objects.requireNonNull(parameter, "parameter");
            Objects.requireNonNull(edge, "edge");
            Objects.requireNonNull(step, "step");
        }

        /** An axis that lands on the nearest size. */
        public Axis(String parameter, Edge edge) {
            this(parameter, edge, Optional.empty());
        }

        /** An axis whose value moves one for every {@code step} grid units dragged. */
        public static Axis stepped(String parameter, Edge edge, Step step) {
            return new Axis(parameter, edge, Optional.of(step));
        }

        /** The smallest value of the parameter. */
        public int minimum(ComponentType type) {
            return spec(type).minimum();
        }

        /** The largest value of the parameter. */
        public int maximum(ComponentType type) {
            return spec(type).maximum();
        }

        /** The parameter's default value. */
        public int defaultValue(ComponentType type) {
            return (Integer) spec(type).defaultValue();
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

        /** The part's size along this axis, in grid units, with {@code parameters}. */
        public int extentOf(ComponentType type, Map<String, Object> parameters) {
            GridSize size = type.specializedFor(parameters).size();
            return edge == Edge.RIGHT ? size.width() : size.height();
        }

        /**
         * The value whose size along the axis is nearest to {@code extent} grid units, inside the parameter's range:
         * where an edge dragged to {@code extent} lands. On a tie the smaller value wins.
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

        /**
         * The value after the edge of a part with {@code parameters} (as at the press) is dragged {@code movedUnits}
         * grid units outwards (negative: inwards), inside the parameter's range.
         */
        public int valueAfter(ComponentType type, Map<String, Object> parameters, double movedUnits) {
            if (step.isEmpty()) {
                return valueFor(type, parameters, extentOf(type, parameters) + movedUnits);
            }
            double unitsPerValue = step.get().unitsPerValue(type.resolveParameters(parameters));
            long target = valueOf(type, parameters) + Math.round(movedUnits / unitsPerValue);
            return (int) Math.max(minimum(type), Math.min(maximum(type), target));
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

    /**
     * @throws IllegalArgumentException unless there are one or two axes, on different edges and parameters
     */
    public ResizeSpec {
        axes = List.copyOf(axes);
        if (axes.isEmpty() || axes.size() > Edge.values().length) {
            throw new IllegalArgumentException("a resize has one or two axes");
        }
        Set<Edge> edges = new HashSet<>();
        Set<String> parameters = new HashSet<>();
        for (Axis axis : axes) {
            if (!edges.add(axis.edge()) || !parameters.add(axis.parameter())) {
                throw new IllegalArgumentException("two axes of a resize share an edge or a parameter");
            }
        }
    }

    /** One parameter on one edge, landing on the nearest size. */
    public ResizeSpec(String parameter, Edge edge) {
        this(List.of(new Axis(parameter, edge)));
    }

    /** The bottom-right corner: {@code horizontal} on the right edge, {@code vertical} on the bottom one. */
    public static ResizeSpec corner(Axis horizontal, Axis vertical) {
        if (horizontal.edge() != Edge.RIGHT || vertical.edge() != Edge.BOTTOM) {
            throw new IllegalArgumentException("a corner grows to the right and to the bottom");
        }
        return new ResizeSpec(List.of(horizontal, vertical));
    }

    /** True when both edges move: the grip is the bottom-right corner. */
    public boolean isCorner() {
        return axes.size() == Edge.values().length;
    }

    /** The axis that moves {@code edge}, if any. */
    public Optional<Axis> axis(Edge edge) {
        return axes.stream().filter(axis -> axis.edge() == edge).findFirst();
    }

    /** The axis of {@code parameter}, if any. */
    public Optional<Axis> axisOf(String parameter) {
        return axes.stream().filter(axis -> axis.parameter().equals(parameter)).findFirst();
    }

    /** Every axis's value in {@code parameters}, by parameter, in the axes' order. */
    public Map<String, Integer> valuesOf(ComponentType type, Map<String, Object> parameters) {
        Map<String, Integer> values = new LinkedHashMap<>();
        axes.forEach(axis -> values.put(axis.parameter(), axis.valueOf(type, parameters)));
        return values;
    }

    /** Every axis's default value, by parameter: where a double click on the grip goes back to. */
    public Map<String, Integer> defaults(ComponentType type) {
        Map<String, Integer> values = new LinkedHashMap<>();
        axes.forEach(axis -> values.put(axis.parameter(), axis.defaultValue(type)));
        return values;
    }

    /** {@code parameters} with each parameter in {@code values} set. */
    public Map<String, Object> parametersWith(Map<String, Object> parameters, Map<String, Integer> values) {
        Map<String, Object> changed = new HashMap<>(parameters);
        changed.putAll(values);
        return Map.copyOf(changed);
    }

    private Axis first() {
        return axes.getFirst();
    }

    /** The first axis's parameter. */
    public String parameter() {
        return first().parameter();
    }

    /** The first axis's edge. */
    public Edge edge() {
        return first().edge();
    }

    /** The smallest value of the first axis's parameter. */
    public int minimum(ComponentType type) {
        return first().minimum(type);
    }

    /** The largest value of the first axis's parameter. */
    public int maximum(ComponentType type) {
        return first().maximum(type);
    }

    /** The first axis's value in {@code parameters}. */
    public int valueOf(ComponentType type, Map<String, Object> parameters) {
        return first().valueOf(type, parameters);
    }

    /** {@code parameters} with the first axis's parameter set to {@code value}. */
    public Map<String, Object> parametersWith(Map<String, Object> parameters, int value) {
        return first().parametersWith(parameters, value);
    }

    /** The part's size along the first axis, in grid units. */
    public int extentOf(ComponentType type, Map<String, Object> parameters) {
        return first().extentOf(type, parameters);
    }

    /** {@link Axis#valueFor} of the first axis. */
    public int valueFor(ComponentType type, Map<String, Object> parameters, double extent) {
        return first().valueFor(type, parameters, extent);
    }

    /** Checks that {@code type} has every axis's parameter and that they are integers. */
    void checkFor(ComponentType type) {
        axes.forEach(axis -> axis.spec(type));
    }
}
