package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.read.ComponentTypeView.ParameterView;
import cl.almejo.vsim.automation.read.ComponentTypeView.PinSpecView;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterKind;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.SubcircuitTypes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The catalogue the AI places parts from (spec §10 «describe the catalogue»): every built-in type in registration
 * order, then the circuit's own user components, then the user's library components the circuit does not have yet,
 * each with its parameters and the pins of a specialised instance.
 */
public final class CatalogReader {

    private CatalogReader() {
    }

    /**
     * Every type, with the pins of its default parameters; {@code typeFilter} keeps those whose id, Spanish or English
     * name contains it, or whose category is it (case ignored).
     */
    public static CatalogView describeAll(ReadSource source, Optional<String> typeFilter) {
        List<ComponentTypeView> types = new ArrayList<>();
        for (Map.Entry<ComponentType, ComponentTypeView.Origin> entry : catalogueTypes(source).entrySet()) {
            ComponentTypeView view = view(source, entry.getKey(), Map.of(), entry.getValue());
            if (typeFilter.map(filter -> matches(view, filter)).orElse(true)) {
                types.add(view);
            }
        }
        return new CatalogView(source.id(), source.version(), types);
    }

    /**
     * Type {@code typeId} specialised for {@code parameters} (missing ones take their defaults): the pins a part placed
     * with them would have.
     *
     * @throws AutomationException {@link AutomationError#INVALID_REFERENCE} for a type the circuit cannot place,
     *                             {@link AutomationError#INVALID_PARAMETER} for an unknown parameter or a bad value
     */
    public static ComponentTypeView describe(ReadSource source, String typeId, Map<String, Object> parameters) {
        Optional<ComponentType> known = source.registry().find(typeId);
        ComponentTypeView.Origin origin = known.map(CatalogReader::originOf).orElse(ComponentTypeView.Origin.LIBRARY);
        ComponentType type = known.or(() -> libraryType(source, typeId)).orElseThrow(() -> new AutomationException(
                AutomationError.INVALID_REFERENCE, "No component type " + typeId + " in this circuit nor in the "
                + "user's library", Map.of("hint", "list the types with describeComponents")));
        Set<String> names = type.parameters().stream().map(ParameterSpec::name).collect(Collectors.toSet());
        for (String name : parameters.keySet()) {
            if (!names.contains(name)) {
                throw new AutomationException(AutomationError.INVALID_PARAMETER,
                        "Type " + typeId + " has no parameter " + name, Map.of("parameters", List.copyOf(names)));
            }
        }
        return view(source, type, parameters, origin);
    }

    /** The library component whose type id is {@code typeId}, when the circuit does not have it yet. */
    private static Optional<ComponentType> libraryType(ReadSource source, String typeId) {
        return SubcircuitTypes.definitionIdOf(typeId).flatMap(definitionId -> source.library().stream()
                .filter(bundle -> bundle.root().id().equals(definitionId))
                .findFirst()
                .map(bundle -> SubcircuitTypes.typeOf(bundle.root())));
    }

    private static ComponentTypeView.Origin originOf(ComponentType type) {
        return SubcircuitTypes.definitionOf(type).isPresent() ? ComponentTypeView.Origin.CIRCUIT
                : ComponentTypeView.Origin.BUILT_IN;
    }

    private static Map<ComponentType, ComponentTypeView.Origin> catalogueTypes(ReadSource source) {
        Map<ComponentType, ComponentTypeView.Origin> types = new LinkedHashMap<>();
        source.registry().all().forEach(type -> types.put(type, ComponentTypeView.Origin.BUILT_IN));
        Set<String> inCircuit = new HashSet<>();
        for (ComponentDefinition definition : source.document().definitions()) {
            inCircuit.add(definition.id());
            source.registry().find(SubcircuitTypes.typeIdOf(definition.id()))
                    .ifPresent(type -> types.put(type, ComponentTypeView.Origin.CIRCUIT));
        }
        for (DefinitionBundle bundle : source.library()) {
            if (!inCircuit.contains(bundle.root().id())) {
                types.put(SubcircuitTypes.typeOf(bundle.root()), ComponentTypeView.Origin.LIBRARY);
            }
        }
        return types;
    }

    private static ComponentTypeView view(ReadSource source, ComponentType type, Map<String, Object> parameters,
                                          ComponentTypeView.Origin origin) {
        Map<String, Object> resolved;
        ComponentType specialised;
        try {
            resolved = type.resolveParameters(parameters);
            specialised = type.specializedFor(parameters);
        } catch (IllegalArgumentException exception) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, exception.getMessage(),
                    Map.of("typeId", type.id()));
        }
        Optional<ComponentDefinition> definition = SubcircuitTypes.definitionOf(type);
        return new ComponentTypeView(type.id(), type.category().name(), source.names().spanish(type),
                source.names().english(type), definition.isPresent(),
                definition.map(ComponentDefinition::name).orElse(""), resolved,
                type.parameters().stream().map(CatalogReader::parameter).toList(),
                specialised.size().width(), specialised.size().height(), pins(specialised), origin);
    }

    private static ParameterView parameter(ParameterSpec spec) {
        boolean integer = spec.kind() == ParameterKind.INTEGER;
        return new ParameterView(spec.name(), spec.kind().name(), spec.defaultValue(),
                integer ? spec.minimum() : null, integer ? spec.maximum() : null, spec.choices(),
                spec.displayOnly());
    }

    private static List<PinSpecView> pins(ComponentType type) {
        List<PinSpecView> pins = new ArrayList<>();
        for (int index = 0; index < type.pinCount(); index++) {
            PinSpec pin = type.pin(index);
            pins.add(new PinSpecView(index, pin.name(), pin.direction().name(), pin.width(), pin.isAdaptiveWidth(),
                    pin.position()));
        }
        return pins;
    }

    private static boolean matches(ComponentTypeView view, String filter) {
        String wanted = filter.toLowerCase(Locale.ROOT);
        return view.typeId().toLowerCase(Locale.ROOT).contains(wanted)
                || view.spanishName().toLowerCase(Locale.ROOT).contains(wanted)
                || view.englishName().toLowerCase(Locale.ROOT).contains(wanted)
                || view.definitionName().toLowerCase(Locale.ROOT).contains(wanted)
                || view.category().equalsIgnoreCase(filter.replace(' ', '_'));
    }
}
