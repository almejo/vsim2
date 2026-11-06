package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.SubcircuitTypes;

import java.util.ArrayList;
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
 * must fit a width); see {@link #check}. A type with {@linkplain #withProfiles profiles} names the choice parameter
 * that remembers the chosen one ({@code profileParameter}) and the {@code profiles} themselves, their values
 * normalized; see {@link ParameterProfiles}. A type with a {@linkplain #withResize resize} ({@code resize}) can be made
 * wider or taller by dragging an edge on the canvas.
 */
public record ComponentType(String id, ComponentCategory category, GridSize size, List<PinSpec> pins,
                            List<ParameterSpec> parameters, BehaviorFactory behaviorFactory,
                            ComponentLayout layout,
                            Function<Map<String, Object>, Optional<ParameterProblem>> parameterCheck,
                            Optional<String> profileParameter, List<ParameterProfile> profiles,
                            Optional<ResizeSpec> resize) {

    public ComponentType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(behaviorFactory, "behaviorFactory");
        Objects.requireNonNull(profileParameter, "profileParameter");
        Objects.requireNonNull(resize, "resize");
        pins = List.copyOf(pins);
        parameters = List.copyOf(parameters);
        layout = layout == null ? null : MemoizedLayout.of(layout, parameters);
        checkVisibilityConditions(id, parameters);
        profiles = normalizedProfiles(id, parameters, profileParameter, profiles);
    }

    public ComponentType(String id, ComponentCategory category, GridSize size, List<PinSpec> pins,
                         List<ParameterSpec> parameters, BehaviorFactory behaviorFactory, ComponentLayout layout,
                         Function<Map<String, Object>, Optional<ParameterProblem>> parameterCheck) {
        this(id, category, size, pins, parameters, behaviorFactory, layout, parameterCheck, Optional.empty(),
                List.of(), Optional.empty());
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
                new SpecializedLayout(SpecializedLayout.baseOf(layout), rawParameters), parameterCheck,
                profileParameter, profiles, resize);
    }

    /**
     * This type with {@code profiles} chosen by the choice parameter {@code profileParameter} (spec §7: the part
     * remembers its profile). Every choice of that parameter needs exactly one profile, and every value a profile
     * lists must be a valid value of another parameter; the values are kept normalized.
     *
     * @throws IllegalArgumentException when they are not
     */
    public ComponentType withProfiles(String profileParameter, List<ParameterProfile> profiles) {
        Objects.requireNonNull(profileParameter, "profileParameter");
        return new ComponentType(id, category, size, pins, parameters, behaviorFactory, layout, parameterCheck,
                Optional.of(profileParameter), profiles, resize);
    }

    /**
     * This type with {@code resize}: its size follows those integer parameters, which the canvas also lets the user
     * change by dragging an edge (Task 6b, the Teclado's width).
     *
     * @throws IllegalArgumentException when the type has no such integer parameter
     */
    public ComponentType withResize(ResizeSpec resize) {
        Objects.requireNonNull(resize, "resize");
        resize.checkFor(this);
        return new ComponentType(id, category, size, pins, parameters, behaviorFactory, layout, parameterCheck,
                profileParameter, profiles, Optional.of(resize));
    }

    /** The profile called {@code name}, empty when this type has none by that name. */
    public Optional<ParameterProfile> profile(String name) {
        return profiles.stream().filter(profile -> profile.name().equals(name)).findFirst();
    }

    /**
     * Whether the inspector shows {@code parameter}'s row while the form holds {@code rawValues}: never a hidden one,
     * and one with a {@linkplain ParameterSpec#visibility() condition} only while it holds (a value that cannot be
     * read does not meet it), and only while the parameter its condition reads is shown itself (a UART's «Texto
     * esperado» follows «Salida esperada», which shows only with the integrated screen).
     */
    public boolean isShown(ParameterSpec parameter, Map<String, Object> rawValues) {
        return isShown(parameter, rawValues, parameters.size());
    }

    /** {@link #isShown(ParameterSpec, Map)} following at most {@code depth} conditions, so a cycle cannot loop. */
    private boolean isShown(ParameterSpec parameter, Map<String, Object> rawValues, int depth) {
        if (parameter.hidden() || depth < 0) {
            return false;
        }
        return parameter.visibility().map(condition -> parameterNamed(condition.parameter())
                .filter(other -> isShown(other, rawValues, depth - 1))
                .flatMap(other -> other.normalizeIfValid(rawValues.get(other.name())))
                .filter(condition.value()::equals)
                .isPresent()).orElse(true);
    }

    /** The parameter called {@code name}, empty when this type has none. */
    public Optional<ParameterSpec> parameterNamed(String name) {
        return parameters.stream().filter(parameter -> parameter.name().equals(name)).findFirst();
    }

    private static void checkVisibilityConditions(String id, List<ParameterSpec> parameters) {
        for (ParameterSpec parameter : parameters) {
            parameter.visibility().ifPresent(condition -> {
                ParameterSpec other = parameters.stream()
                        .filter(candidate -> candidate.name().equals(condition.parameter())
                                && !candidate.name().equals(parameter.name()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(id + ": " + parameter.name()
                                + " is shown after an unknown parameter " + condition.parameter()));
                if (!other.normalizeIfValid(condition.value()).filter(condition.value()::equals).isPresent()) {
                    throw new IllegalArgumentException(id + ": " + parameter.name() + " is shown when "
                            + other.name() + " is " + condition.value() + ", which is not one of its values");
                }
            });
        }
    }

    private static List<ParameterProfile> normalizedProfiles(String id, List<ParameterSpec> parameters,
                                                             Optional<String> profileParameter,
                                                             List<ParameterProfile> profiles) {
        if (profileParameter.isEmpty()) {
            if (!profiles.isEmpty()) {
                throw new IllegalArgumentException(id + ": profiles without a profile parameter");
            }
            return List.of();
        }
        String chooser = profileParameter.get();
        ParameterSpec chooserSpec = parameters.stream().filter(parameter -> parameter.name().equals(chooser))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(id + ": no profile parameter " + chooser));
        if (chooserSpec.kind() != ParameterKind.CHOICE) {
            throw new IllegalArgumentException(id + ": the profile parameter " + chooser + " is not a choice");
        }
        List<String> names = profiles.stream().map(ParameterProfile::name).toList();
        if (names.size() != chooserSpec.choices().size() || !names.containsAll(chooserSpec.choices())) {
            throw new IllegalArgumentException(id + ": profiles " + names + " do not match the choices "
                    + chooserSpec.choices());
        }
        return profiles.stream().map(profile -> normalized(id, parameters, chooser, profile)).toList();
    }

    private static ParameterProfile normalized(String id, List<ParameterSpec> parameters, String chooser,
                                               ParameterProfile profile) {
        Map<String, Object> values = new LinkedHashMap<>();
        profile.values().forEach((name, value) -> {
            ParameterSpec parameter = parameters.stream()
                    .filter(candidate -> candidate.name().equals(name) && !name.equals(chooser))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(id + ": profile " + profile.name()
                            + " sets an unknown parameter " + name));
            values.put(name, parameter.normalize(value));
        });
        return new ParameterProfile(profile.name(), values);
    }

    /** This type as {@code component} has it: {@link #specializedFor} its parameters. */
    public ComponentType forComponent(Component component) {
        if (SubcircuitTypes.definitionOf(this).isPresent()) {
            return SubcircuitTypes.forComponent(this, component);
        }
        if (component.appearance().isPresent()) {
            throw new IllegalArgumentException("only user components can have an appearance override");
        }
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

    /**
     * The resolved parameters that shape the running behaviour: {@link #resolveParameters} without the
     * {@linkplain ParameterSpec#displayOnly() display-only} ones and without the
     * {@linkplain ParameterSpec#liveValue() live values}. Two components with equal behaviour parameters can share one
     * running behaviour; a different live value is handed to it ({@link #liveValueParameters}).
     */
    public Map<String, Object> behaviorParameters(Map<String, Object> raw) {
        Map<String, Object> resolved = new LinkedHashMap<>(resolveParameters(raw));
        parameters.stream().filter(parameter -> parameter.displayOnly() || parameter.liveValue())
                .forEach(parameter -> resolved.remove(parameter.name()));
        return Map.copyOf(resolved);
    }

    /** The resolved {@linkplain ParameterSpec#liveValue() live-value} parameters of {@code raw} (most types: none). */
    public Map<String, Object> liveValueParameters(Map<String, Object> raw) {
        if (parameters.stream().noneMatch(ParameterSpec::liveValue)) {
            return Map.of();
        }
        Map<String, Object> resolved = resolveParameters(raw);
        Map<String, Object> live = new LinkedHashMap<>();
        parameters.stream().filter(ParameterSpec::liveValue)
                .forEach(parameter -> live.put(parameter.name(), resolved.get(parameter.name())));
        return Map.copyOf(live);
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

    /** Where every pin of {@code component} is, in pin index order (see {@link #pinLocation}). */
    public List<GridPoint> pinLocations(Component component) {
        List<GridPoint> locations = new ArrayList<>(pinCount());
        for (int pinIndex = 0; pinIndex < pinCount(); pinIndex++) {
            locations.add(pinLocation(component, pinIndex));
        }
        return locations;
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
