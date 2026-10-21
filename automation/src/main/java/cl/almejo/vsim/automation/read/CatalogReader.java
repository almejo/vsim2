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
import cl.almejo.vsim.core.library.SubcircuitTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The catalogue the AI places parts from (spec §10 «describe the catalogue»): every built-in type in registration
 * order, then the circuit's own user components, each with its parameters and the pins of a specialised instance.
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
        for (ComponentType type : catalogueTypes(source)) {
            ComponentTypeView view = view(source, type, Map.of());
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
        ComponentType type = source.registry().find(typeId).orElseThrow(() -> new AutomationException(
                AutomationError.INVALID_REFERENCE, "No component type " + typeId + " in this circuit",
                Map.of("hint", "list the types with describeComponents")));
        Set<String> known = type.parameters().stream().map(ParameterSpec::name).collect(Collectors.toSet());
        for (String name : parameters.keySet()) {
            if (!known.contains(name)) {
                throw new AutomationException(AutomationError.INVALID_PARAMETER,
                        "Type " + typeId + " has no parameter " + name, Map.of("parameters", List.copyOf(known)));
            }
        }
        return view(source, type, parameters);
    }

    private static List<ComponentType> catalogueTypes(ReadSource source) {
        List<ComponentType> types = new ArrayList<>(source.registry().all());
        for (ComponentDefinition definition : source.document().definitions()) {
            source.registry().find(SubcircuitTypes.typeIdOf(definition.id())).ifPresent(types::add);
        }
        return types;
    }

    private static ComponentTypeView view(ReadSource source, ComponentType type, Map<String, Object> parameters) {
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
                specialised.size().width(), specialised.size().height(), pins(specialised));
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
