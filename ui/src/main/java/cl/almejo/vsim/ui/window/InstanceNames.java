package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * How an instance of a piece is named for a person, in one place: the Problemas rows, the place bar and the Proyecto
 * panel all use it. The code has no designators («U4»): an instance is its label, else its unlabeled name, and a
 * route step adds the instance's reference (its label, else its id) to the definition's name: «Ram (c5)».
 */
public final class InstanceNames {

    private InstanceNames() {
    }

    /** The label of {@code instance}, else its unlabeled name («AND de 3 entradas», «Ram»). */
    public static String of(Component instance, CircuitDocument document) {
        return instance.label().isBlank() ? unlabeledName(instance, document) : instance.label();
    }

    /** The name of a part without a label, told apart from its siblings: «AND de 3 entradas». Same as the tree. */
    public static String unlabeledName(Component component, CircuitDocument document) {
        return Messages.distinguishedName(typeName(component, document), component.parameters());
    }

    /** The name of the document's definition for a sub-circuit instance, the bundle name of a standard type. */
    public static String typeName(Component component, CircuitDocument document) {
        return definitionOf(component, document).map(ComponentDefinition::name)
                .orElseGet(() -> Messages.componentName(component.typeId(), component.parameters()));
    }

    /** What points an instance out: its label, else its id («c5»). */
    public static String reference(Component instance) {
        return instance.label().isBlank() ? instance.id().value() : instance.label();
    }

    /** One step of a route: «Ram (c5)». */
    public static String step(Component instance, CircuitDocument document) {
        return Messages.format("place.step", typeName(instance, document), reference(instance));
    }

    /**
     * The route of an instance path ({@code c5/c2}), outermost first: «Ram (c5)», «Ram Selector (c2)». Empty when a
     * segment names no instance of the document.
     */
    public static List<String> route(String path, CircuitDocument document) {
        return chain(path, document).map(components -> components.stream()
                .map(component -> step(component, document)).toList()).orElse(List.of());
    }

    /** The components named by each segment of {@code hierarchicalId}, outermost first. */
    public static Optional<List<Component>> chain(String hierarchicalId, CircuitDocument document) {
        List<Component> level = document.components();
        List<Component> found = new ArrayList<>();
        for (String segment : InstancePath.segments(hierarchicalId)) {
            Optional<Component> component = level.stream()
                    .filter(candidate -> candidate.id().value().equals(segment))
                    .findFirst();
            if (component.isEmpty()) {
                return Optional.empty();
            }
            found.add(component.get());
            level = definitionOf(component.get(), document)
                    .map(definition -> definition.body().components())
                    .orElse(List.of());
        }
        return Optional.of(found);
    }

    /** The definition a sub-circuit instance instantiates, when the document has it. */
    public static Optional<ComponentDefinition> definitionOf(Component component, CircuitDocument document) {
        return SubcircuitTypes.definitionIdOf(component.typeId()).flatMap(document::definition);
    }
}
