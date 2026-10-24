package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorFactory;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.SymbolDrawing;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;

import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Component types for instances of definitions: id {@code subcircuit:<definition id>}, no parameters. */
public final class SubcircuitTypes {

    public static final String PREFIX = "subcircuit:";

    private SubcircuitTypes() {
    }

    public static String typeIdOf(String definitionId) {
        return PREFIX + definitionId;
    }

    public static Optional<String> definitionIdOf(String typeId) {
        if (typeId == null || !typeId.startsWith(PREFIX) || typeId.length() == PREFIX.length()) {
            return Optional.empty();
        }
        return Optional.of(typeId.substring(PREFIX.length()));
    }

    /**
     * Equal definitions give equal types. The instance type never reaches the engine (the flattener expands instances
     * into their bodies); see {@link #resolverFor}.
     */
    public static ComponentType typeOf(ComponentDefinition definition) {
        Factory factory = new Factory(definition);
        ComponentType type = new ComponentType(typeIdOf(definition.id()), ComponentCategory.CUSTOM, definition.size(),
                definition.pinout().stream().map(DefinitionPin::spec).toList(), List.of(), factory);
        factory.baseType = type;
        return type;
    }

    /** The distinct definition ids {@code components} instantiate directly, in order of first use. */
    public static List<String> definitionIds(Collection<Component> components) {
        return components.stream()
                .map(Component::typeId)
                .map(SubcircuitTypes::definitionIdOf)
                .flatMap(Optional::stream)
                .distinct()
                .toList();
    }

    /**
     * Resolves {@code subcircuit:<id>} through {@code document}'s definitions, with one type object per definition
     * object: a replaced definition gets a new type, carrying its new size and pins. The instance type never reaches
     * the engine (the flattener expands instances into their bodies), so after a replacement the engine keeps the
     * inner components whose id, type and parameters did not change, with their state, and recreates only the rest.
     * Only the type of each id's current definition is kept, so replaced definitions are not retained.
     *
     * <p>The resolver reads {@code document}'s definitions, which are not thread-safe: a registry that has it must be
     * used from the document's thread (the EDT in the application) only. Flatten there and hand the simulation thread
     * already-resolved components; a base registry without document resolvers stays safe on any thread.
     */
    public static ComponentTypeRegistry.TypeResolver resolverFor(CircuitDocument document) {
        Map<String, ComponentType> types = new ConcurrentHashMap<>();
        return typeId -> {
            types.keySet().removeIf(id -> document.definition(id).isEmpty());
            return definitionIdOf(typeId)
                .flatMap(document::definition)
                .map(definition -> types.compute(definition.id(), (id, cached) ->
                        cached != null && definitionOf(cached).orElseThrow() == definition ? cached : typeOf(definition)));
        };
    }

    public static Optional<ComponentDefinition> definitionOf(ComponentType type) {
        return type.behaviorFactory() instanceof Factory factory ? Optional.of(factory.definition) : Optional.empty();
    }

    /** The drawing of this effective type; an override may deliberately restore the default box. */
    public static Optional<SymbolDrawing> appearanceOf(ComponentType type) {
        if (!(type.behaviorFactory() instanceof Factory factory)) {
            return Optional.empty();
        }
        return factory.appearance.map(ComponentAppearance::drawing).orElse(factory.definition.appearance());
    }

    /**
     * Resolves the instance override against its current definition, preserving pinout order for the flattener.
     * Equal canonical overrides share one bounded cached type; replacing a definition replaces its cache too.
     */
    public static ComponentType forComponent(ComponentType type, Component component) {
        if (!(type.behaviorFactory() instanceof Factory factory)) {
            throw new IllegalArgumentException("not a user component type: " + type.id());
        }
        if (component.appearance().isEmpty()) {
            return factory.root.baseType;
        }
        ComponentAppearance canonical = component.appearance().orElseThrow().canonical(factory.definition.pinout());
        if (factory.appearance.filter(canonical::equals).isPresent()) {
            return type;
        }
        return factory.root.typeFor(canonical);
    }

    /** The flattener expands instances, so this behavior never runs in a simulation; tools may still create it. */
    private static final class Factory implements BehaviorFactory {
        /** Bound undo-session variants; live definitions own their caches and removed definitions release them. */
        private static final int MAXIMUM_EFFECTIVE_TYPES = 512;
        private final ComponentDefinition definition;
        private final Optional<ComponentAppearance> appearance;
        private final Factory root;
        private final Map<ComponentAppearance, ComponentType> types = new LinkedHashMap<>(16, 0.75f, true);
        private ComponentType baseType;

        private Factory(ComponentDefinition definition) {
            this.definition = definition;
            appearance = Optional.empty();
            root = this;
        }

        private Factory(Factory root, ComponentAppearance appearance) {
            this.definition = root.definition;
            this.appearance = Optional.of(appearance);
            this.root = root;
        }

        private synchronized ComponentType typeFor(ComponentAppearance appearance) {
            ComponentType cached = types.get(appearance);
            if (cached != null) {
                return cached;
            }
            List<PinSpec> pins = new ArrayList<>();
            for (int index = 0; index < definition.pinout().size(); index++) {
                PinSpec pin = definition.pinout().get(index).spec();
                pins.add(new PinSpec(pin.name(), appearance.pins().get(index).offset(), pin.direction(), pin.width()));
            }
            ComponentType effective = new ComponentType(typeIdOf(definition.id()), ComponentCategory.CUSTOM,
                    appearance.size(), pins, List.of(), new Factory(this, appearance));
            if (types.size() >= MAXIMUM_EFFECTIVE_TYPES) {
                types.remove(types.keySet().iterator().next());
            }
            types.put(appearance, effective);
            return effective;
        }

        @Override
        public Behavior create(Map<String, Object> parameters) {
            return (context, pinIndex) -> {
            };
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Factory factory && definition.equals(factory.definition)
                    && appearance.equals(factory.appearance);
        }

        @Override
        public int hashCode() {
            return Objects.hash(definition, appearance);
        }
    }
}
