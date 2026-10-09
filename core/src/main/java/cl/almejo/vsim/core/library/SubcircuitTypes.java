package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorFactory;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    /** Equal definitions give equal types (the engine treats them as the same component). */
    public static ComponentType typeOf(ComponentDefinition definition) {
        return new ComponentType(typeIdOf(definition.id()), ComponentCategory.CUSTOM, definition.size(),
                definition.pinout().stream().map(DefinitionPin::spec).toList(), List.of(), new Factory(definition));
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
     * object (a replaced definition gets a new type, so the engine recreates its instances). Only the type of each
     * id's current definition is kept, so replaced definitions are not retained.
     */
    public static ComponentTypeRegistry.TypeResolver resolverFor(CircuitDocument document) {
        Map<String, ComponentType> types = new ConcurrentHashMap<>();
        return typeId -> definitionIdOf(typeId)
                .flatMap(document::definition)
                .map(definition -> types.compute(definition.id(), (id, cached) ->
                        cached != null && definitionOf(cached).orElseThrow() == definition ? cached : typeOf(definition)));
    }

    public static Optional<ComponentDefinition> definitionOf(ComponentType type) {
        return type.behaviorFactory() instanceof Factory factory ? Optional.of(factory.definition()) : Optional.empty();
    }

    /** The flattener expands instances, so this behavior never runs in a simulation; tools may still create it. */
    private record Factory(ComponentDefinition definition) implements BehaviorFactory {
        @Override
        public Behavior create(Map<String, Object> parameters) {
            return (context, pinIndex) -> {
            };
        }
    }
}
