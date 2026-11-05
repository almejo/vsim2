package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * How a piece is named for a person, in one place (spec §5.3): the circuit tree, the place bar's route and «Valores
 * de», and the Problemas rows all use it. The code has no designators («U4»): a piece is its label, else its type
 * name («AND de 3 entradas», «Ram»); when several unlabeled pieces of one type share a place (the circuit, or the body
 * of one definition), they are numbered in document order: «Multiplexor 1» … «Multiplexor 4». The id is never part
 * of the name.
 */
public final class InstanceNames {

    private static final String PATH_SEPARATOR = " › ";

    private InstanceNames() {
    }

    /**
     * The name of every piece of {@code place} (the circuit's components, or a definition's body) by its id: its label,
     * else its type name, numbered when another unlabeled piece of {@code place} has the same type name.
     */
    public static Map<ElementId, String> names(List<Component> place, CircuitDocument document) {
        Map<String, Integer> unlabeledCounts = new HashMap<>();
        for (Component component : place) {
            if (component.label().isBlank()) {
                unlabeledCounts.merge(unlabeledName(component, document), 1, Integer::sum);
            }
        }
        Map<String, Integer> numbersGiven = new HashMap<>();
        Map<ElementId, String> names = new LinkedHashMap<>();
        for (Component component : place) {
            if (!component.label().isBlank()) {
                names.put(component.id(), component.label());
                continue;
            }
            String typeName = unlabeledName(component, document);
            if (unlabeledCounts.get(typeName) > 1) {
                int number = numbersGiven.merge(typeName, 1, Integer::sum);
                names.put(component.id(), Messages.format("instance.numbered", typeName, number));
            } else {
                names.put(component.id(), typeName);
            }
        }
        return names;
    }

    /** The name of the piece at {@code hierarchicalId} ({@code c5/c2}); empty when a level is not in the document. */
    public static Optional<String> name(String hierarchicalId, CircuitDocument document) {
        List<String> route = route(hierarchicalId, document);
        return route.isEmpty() ? Optional.empty() : Optional.of(route.getLast());
    }

    /** The name of a part without a label, told apart from its siblings: «AND de 3 entradas». Same as the palette. */
    private static String unlabeledName(Component component, CircuitDocument document) {
        return Messages.distinguishedName(typeName(component, document), component.parameters());
    }

    /** The name of the document's definition for a sub-circuit instance, the bundle name of a standard type. */
    public static String typeName(Component component, CircuitDocument document) {
        return definitionOf(component, document).map(ComponentDefinition::name)
                .orElseGet(() -> Messages.componentName(component.typeId(), component.parameters()));
    }

    /**
     * The name of each level of an instance path ({@code c5/c2}), outermost first: «Ram», «Ram Selector 2». Empty when
     * a segment names no piece of the document.
     */
    public static List<String> route(String hierarchicalId, CircuitDocument document) {
        List<Component> place = document.components();
        List<String> names = new ArrayList<>();
        for (String segment : InstancePath.segments(hierarchicalId)) {
            Optional<Component> component = place.stream()
                    .filter(candidate -> candidate.id().value().equals(segment)).findFirst();
            if (component.isEmpty()) {
                return List.of();
            }
            names.add(names(place, document).get(component.get().id()));
            place = bodyOf(component.get(), document);
        }
        return names;
    }

    /**
     * "U1 › U3 (AND de 2 entradas)" for a simulation id such as {@code c5/c2}, as Problemas words a part (spec §6.12):
     * each level by its {@linkplain #names name}; a labeled innermost level also shows its type. Empty when a level
     * cannot be found in the document and its definitions.
     */
    public static Optional<String> path(String hierarchicalId, CircuitDocument document) {
        List<String> names = route(hierarchicalId, document);
        if (names.isEmpty()) {
            return Optional.empty();
        }
        Component innermost = chain(hierarchicalId, document).orElseThrow().getLast();
        if (!innermost.label().isBlank()) {
            names = new ArrayList<>(names);
            names.set(names.size() - 1, Messages.format("problem.pathLabeled", innermost.label(),
                    typeName(innermost, document)));
        }
        return Optional.of(String.join(PATH_SEPARATOR, names));
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
            level = bodyOf(component.get(), document);
        }
        return Optional.of(found);
    }

    /** The definition a sub-circuit instance instantiates, when the document has it. */
    public static Optional<ComponentDefinition> definitionOf(Component component, CircuitDocument document) {
        return SubcircuitTypes.definitionIdOf(component.typeId()).flatMap(document::definition);
    }

    /** The pieces inside {@code component}: its definition's body; none for a built-in part. */
    private static List<Component> bodyOf(Component component, CircuitDocument document) {
        return definitionOf(component, document).map(definition -> definition.body().components())
                .orElse(List.of());
    }
}
