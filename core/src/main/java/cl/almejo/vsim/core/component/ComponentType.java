package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Describes a kind of component: geometry, pins, parameters and behavior. Drawing lives in the UI. A type with a
 * {@code layout} (bus widths) has the size and pins of its default parameters; {@link #forComponent} and
 * {@link ComponentTypeRegistry#typeOf} give the ones of a placed component. {@code layout} is null for fixed types.
 * {@code parameterCheck}, when not null, refuses sets of parameters that no single parameter can judge (a value that
 * must fit a width); see {@link #check}.
 */
public record ComponentType(String id, ComponentCategory category, GridSize size, List<PinSpec> pins,
                            List<ParameterSpec> parameters, BehaviorFactory behaviorFactory,
                            ComponentLayout layout,
                            Function<Map<String, Object>, Optional<ParameterProblem>> parameterCheck) {

    public ComponentType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(behaviorFactory, "behaviorFactory");
        pins = List.copyOf(pins);
        parameters = List.copyOf(parameters);
        layout = layout == null ? null : MemoizedLayout.of(layout);
    }

    public ComponentType(String id, ComponentCategory category, GridSize size, List<PinSpec> pins,
                         List<ParameterSpec> parameters, BehaviorFactory behaviorFactory, ComponentLayout layout) {
        this(id, category, size, pins, parameters, behaviorFactory, layout, null);
    }

    /** A type whose size and pins never change. */
    public ComponentType(String id, ComponentCategory category, GridSize size, List<PinSpec> pins,
                         List<ParameterSpec> parameters, BehaviorFactory behaviorFactory) {
        this(id, category, size, pins, parameters, behaviorFactory, null);
    }

    /**
     * A type whose size and pins follow its parameters; its own {@code size()} and {@code pins()} are the defaults'.
     * The layout runs once per set of parameters ({@link MemoizedLayout}).
     */
    public static ComponentType parameterized(String id, ComponentCategory category, List<ParameterSpec> parameters,
                                              ComponentLayout layout, BehaviorFactory behaviorFactory) {
        Objects.requireNonNull(layout, "layout");
        Map<String, Object> defaults = new LinkedHashMap<>();
        parameters.forEach(parameter -> defaults.put(parameter.name(), parameter.defaultValue()));
        ComponentShape shape = layout.shapeFor(Map.copyOf(defaults));
        return new ComponentType(id, category, shape.size(), shape.pins(), parameters, behaviorFactory, layout);
    }

    /** Like {@link #parameterized} for a type that also has a {@code parameterCheck}. */
    public static ComponentType parameterized(String id, ComponentCategory category, List<ParameterSpec> parameters,
                                              ComponentLayout layout,
                                              Function<Map<String, Object>, Optional<ParameterProblem>> parameterCheck,
                                              BehaviorFactory behaviorFactory) {
        ComponentType base = parameterized(id, category, parameters, layout, behaviorFactory);
        return new ComponentType(id, category, base.size(), base.pins(), parameters, behaviorFactory, base.layout(),
                parameterCheck);
    }

    public boolean isParameterized() {
        return layout != null;
    }

    /**
     * This type with the size and pins {@code rawParameters} give (normalized first; invalid values throw
     * IllegalArgumentException). Returns {@code this} for a fixed type and whenever the shape does not change.
     */
    public ComponentType specializedFor(Map<String, Object> rawParameters) {
        if (layout == null) {
            return this;
        }
        ComponentShape shape = layout.shapeFor(resolveParameters(rawParameters));
        if (shape.size().equals(size) && shape.pins().equals(pins)) {
            return this;
        }
        return new ComponentType(id, category, shape.size(), shape.pins(), parameters, behaviorFactory,
                new SpecializedLayout(SpecializedLayout.baseOf(layout), rawParameters), parameterCheck);
    }

    /** This type as {@code component} has it: {@link #specializedFor} its parameters. */
    public ComponentType forComponent(Component component) {
        return specializedFor(component.parameters());
    }

    public int pinCount() {
        return pins.size();
    }

    public PinSpec pin(int index) {
        return pins.get(index);
    }

    /**
     * What this type's own check finds wrong with {@code rawParameters}, empty when it finds nothing or the type has no
     * check. A single parameter that is out of range is not reported here: {@link #resolveParameters} throws for it.
     */
    public Optional<ParameterProblem> check(Map<String, Object> rawParameters) {
        return parameterCheck == null ? Optional.empty() : parameterCheck.apply(rawParameters);
    }

    /**
     * The parameters with defaults filled in and every value normalized.
     *
     * @throws IllegalArgumentException for an invalid value or when {@link #check} finds a problem, so files and every
     *                                  other path refuse it
     */
    public Map<String, Object> resolveParameters(Map<String, Object> raw) {
        Optional<ParameterProblem> problem = check(raw);
        if (problem.isPresent()) {
            throw new IllegalArgumentException("parameter " + problem.get().parameterName() + ": "
                    + problem.get().messageKey() + " " + problem.get().arguments());
        }
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (ParameterSpec parameter : parameters) {
            Object rawValue = raw == null ? null : raw.get(parameter.name());
            resolved.put(parameter.name(), rawValue == null ? parameter.defaultValue() : parameter.normalize(rawValue));
        }
        return Map.copyOf(resolved);
    }

    /** Creates a document component of this type, normalizing {@code rawParameters} (invalid values throw). */
    public Component newComponent(ElementId id, GridPoint position, Rotation rotation,
                                  Map<String, Object> rawParameters, String label) {
        return new Component(id, this.id, position, rotation, resolveParameters(rawParameters), label);
    }

    /** Where pin {@code pinIndex} of {@code component} is, with the pins {@code component}'s parameters give. */
    public GridPoint pinLocation(Component component, int pinIndex) {
        PinSpec pin = hasOwnShape(component) ? pins.get(pinIndex) : shapeOf(component).pins().get(pinIndex);
        return component.position().plus(component.rotation().apply(pin.position()));
    }

    /** The box of {@code component}, with the size its parameters give. */
    public Bounds bounds(Component component) {
        GridSize componentSize = hasOwnShape(component) ? size : shapeOf(component).size();
        return Bounds.rotated(component.position(), componentSize, component.rotation());
    }

    /** A fixed type, or one already specialized for {@code component}'s parameters: its own size and pins apply. */
    private boolean hasOwnShape(Component component) {
        return layout == null
                || layout instanceof SpecializedLayout specialized && specialized.isFor(component.parameters());
    }

    /** The shape {@code component}'s parameters give, without building a specialized type (asked on every paint). */
    private ComponentShape shapeOf(Component component) {
        return layout.shapeFor(resolveParameters(component.parameters()));
    }

    public Behavior createBehavior(Map<String, Object> rawParameters) {
        return behaviorFactory.create(resolveParameters(rawParameters));
    }
}
