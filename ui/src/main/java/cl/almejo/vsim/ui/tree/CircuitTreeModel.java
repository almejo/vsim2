package cl.almejo.vsim.ui.tree;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.search.CompiledQuery;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.ui.search.TypeNames;
import cl.almejo.vsim.ui.window.InstanceNames;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The rows of the circuit tree (spec §5.3), without Swing: the document, then every piece of the circuit in document
 * order, each named by {@link InstanceNames}; an instance of a user component holds the pieces of its body, at any
 * depth, with hierarchical ids ({@code c5/c2}). Wires, frames and notes are not pieces. The search walks the same rows
 * and lists the matches flat, each with its route.
 */
public final class CircuitTreeModel {

    /** Deeper nesting is cut off; the document already rejects recursive definitions. */
    public static final int MAXIMUM_DEPTH = 32;

    /** A piece the search found and the names of the pieces it is inside, outermost first (empty on the circuit). */
    public record Match(CircuitTreeNode node, List<String> route) {
        public Match {
            route = List.copyOf(route);
        }
    }

    private CircuitTreeModel() {
    }

    /** The tree of {@code document}, its row named {@code documentName}; {@code registry} resolves its types. */
    public static CircuitTreeNode build(String documentName, CircuitDocument document,
                                        ComponentTypeRegistry registry) {
        return CircuitTreeNode.document(documentName, pieces(document.components(), "", document, registry, 0));
    }

    private static List<CircuitTreeNode> pieces(List<Component> place, String parentPath, CircuitDocument document,
                                                ComponentTypeRegistry registry, int depth) {
        Map<ElementId, String> names = InstanceNames.names(place, document);
        List<CircuitTreeNode> nodes = new ArrayList<>();
        for (Component component : place) {
            String path = parentPath.isEmpty() ? component.id().value()
                    : InstancePath.child(parentPath, component.id().value());
            Optional<ComponentDefinition> definition = InstanceNames.definitionOf(component, document);
            List<CircuitTreeNode> children = definition.isPresent() && depth < MAXIMUM_DEPTH
                    ? pieces(definition.get().body().components(), path, document, registry, depth + 1) : List.of();
            String typeName = TypeNames.of(component, registry);
            Optional<String> kind = component.label().isBlank() || definition.isPresent() ? Optional.empty()
                    : Optional.of(typeName);
            nodes.add(new CircuitTreeNode(path, names.get(component.id()), kind, typeName,
                    StandardComponentTypes.nameId(component.typeId(), component.parameters()),
                    registry.findTypeOf(component), definition.map(ComponentDefinition::id), children));
        }
        return nodes;
    }

    /** Every piece under {@code root} that {@code query} matches (its name or type name), in the tree's order. */
    public static List<Match> search(CircuitTreeNode root, CompiledQuery query) {
        List<Match> matches = new ArrayList<>();
        collect(root.children(), List.of(), query, matches);
        return matches;
    }

    private static void collect(List<CircuitTreeNode> nodes, List<String> route, CompiledQuery query,
                                List<Match> matches) {
        for (CircuitTreeNode node : nodes) {
            if (node.matchTexts().stream().anyMatch(query::matches)) {
                matches.add(new Match(node, route));
            }
            List<String> inside = new ArrayList<>(route);
            inside.add(node.name());
            collect(node.children(), inside, query, matches);
        }
    }
}
