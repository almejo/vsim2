package cl.almejo.vsim.ui.tree;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameContents;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.core.search.CompiledQuery;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.canvas.BusValueText;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.search.TypeNames;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The component tree of a document (spec §6.9), without Swing: the document, its frames and components (each under
 * the innermost frame that encloses it, frames first, then components in document order or grouped by type) and, for
 * instances of user components, their parts with hierarchical ids. Also reads the values
 * that nested interface pins and LEDs show.
 */
public final class ComponentTreeModel {

    /** Deeper nesting is cut off; the document already rejects recursive definitions. */
    public static final int MAXIMUM_DEPTH = 32;

    private ComponentTreeModel() {
    }

    public static ComponentTreeNode build(String documentName, CircuitDocument document,
                                          ComponentTypeRegistry registry, boolean groupByType) {
        Map<ElementId, ElementId> parents = FrameContents.parents(document, registry);
        Map<Optional<ElementId>, List<Frame>> framesByParent = new HashMap<>();
        for (Frame frame : document.frames()) {
            framesByParent.computeIfAbsent(Optional.ofNullable(parents.get(frame.id())), key -> new ArrayList<>())
                    .add(frame);
        }
        Map<Optional<ElementId>, List<Component>> componentsByParent = new HashMap<>();
        for (Component component : document.components()) {
            componentsByParent.computeIfAbsent(Optional.ofNullable(parents.get(component.id())),
                    key -> new ArrayList<>()).add(component);
        }
        Contents contents = new Contents(document, registry, groupByType, framesByParent, componentsByParent);
        return new ComponentTreeNode(ComponentTreeNode.Kind.DOCUMENT, ComponentTreeNode.DOCUMENT_PATH, documentName,
                "", false, contents.childrenOf(Optional.empty(), ComponentTreeNode.DOCUMENT_PATH), List.of());
    }

    /** What each parent (a frame, or the document for {@code Optional.empty()}) holds directly. */
    private record Contents(CircuitDocument document, ComponentTypeRegistry registry, boolean groupByType,
                            Map<Optional<ElementId>, List<Frame>> framesByParent,
                            Map<Optional<ElementId>, List<Component>> componentsByParent) {

        /** Frames first (document order), then the components (document order, or grouped by type). */
        List<ComponentTreeNode> childrenOf(Optional<ElementId> parent, String parentPath) {
            List<ComponentTreeNode> children = new ArrayList<>();
            for (Frame frame : framesByParent.getOrDefault(parent, List.of())) {
                String path = ComponentTreeNode.framePath(frame.id());
                String text = frame.title().isBlank() ? Messages.text("properties.frame") : frame.title();
                children.add(new ComponentTreeNode(ComponentTreeNode.Kind.FRAME, path, text, "", false,
                        childrenOf(Optional.of(frame.id()), path), List.of(frame.title())));
            }
            List<Component> components = componentsByParent.getOrDefault(parent, List.of());
            List<ComponentTreeNode> nodes = new ArrayList<>();
            for (Component component : components) {
                nodes.add(componentNode(component, component.id().value(), document, registry, 0));
            }
            children.addAll(groupByType ? groups(parentPath, components, nodes, registry) : nodes);
            return children;
        }
    }

    /** Path → value text of every row under {@code root} that shows a value and has one in {@code snapshot}. */
    public static Map<String, String> values(ComponentTreeNode root, SimulationSnapshot snapshot) {
        Map<String, String> values = new HashMap<>();
        collectValues(root, snapshot, values);
        return values;
    }

    /** "0", "1", "Z" for one bit; a bus in hex ("0x3F"); "X" for a conflict. */
    public static String valueText(Value value) {
        if (value.conflict()) {
            return BusValueText.of(value);
        }
        return value.width() > 1 ? BusValueText.of(value) : value.toString();
    }

    private static void collectValues(ComponentTreeNode node, SimulationSnapshot snapshot, Map<String, String> values) {
        if (node.showsValue()) {
            switch (snapshot.componentState(node.path())) {
                case VisibleState.LedState led -> values.put(node.path(), valueText(led.value()));
                case VisibleState.SwitchState switchState -> values.put(node.path(), valueText(switchState.value()));
                default -> {
                }
            }
        }
        for (ComponentTreeNode child : node.children()) {
            collectValues(child, snapshot, values);
        }
    }

    private static ComponentTreeNode componentNode(Component component, String path, CircuitDocument document,
                                                   ComponentTypeRegistry registry, int depth) {
        String name = TypeNames.of(component, registry);
        String text = component.label().isBlank() ? name
                : Messages.format("tree.component.labeled", component.label(), name);
        List<ComponentTreeNode> children = new ArrayList<>();
        Optional<ComponentDefinition> definition = SubcircuitTypes.definitionIdOf(component.typeId())
                .flatMap(document::definition);
        if (definition.isPresent() && depth < MAXIMUM_DEPTH) {
            for (Component child : definition.get().body().components()) {
                children.add(componentNode(child, InstancePath.child(path, child.id().value()), document, registry,
                        depth + 1));
            }
        }
        return new ComponentTreeNode(ComponentTreeNode.Kind.COMPONENT, path, text, paletteId(component),
                showsValue(path, component.typeId()), children, List.of(component.label(), name));
    }

    private static boolean showsValue(String path, String typeId) {
        return InstancePath.isNested(path)
                && (InterfacePins.isInterface(typeId) || StandardComponentTypes.LED.equals(typeId));
    }

    private static List<ComponentTreeNode> groups(String parentPath, List<Component> components, List<ComponentTreeNode> nodes,
                                                  ComponentTypeRegistry registry) {
        Map<String, List<ComponentTreeNode>> byType = new LinkedHashMap<>();
        for (int index = 0; index < components.size(); index++) {
            byType.computeIfAbsent(paletteId(components.get(index)), paletteId -> new ArrayList<>())
                    .add(nodes.get(index));
        }
        record Group(String paletteId, String name, List<ComponentTreeNode> members) {
        }
        return byType.entrySet().stream()
                .map(entry -> new Group(entry.getKey(), typeName(entry.getKey(), registry), entry.getValue()))
                .sorted(Comparator.comparing(Group::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Group::paletteId))
                .map(group -> groupNode(ComponentTreeNode.groupPath(parentPath, group.paletteId()), group.name(),
                        group.paletteId(), group.members()))
                .toList();
    }

    /** A group row: «NOT (3)», counting {@code members}. */
    private static ComponentTreeNode groupNode(String path, String name, String paletteId,
                                               List<ComponentTreeNode> members) {
        return new ComponentTreeNode(ComponentTreeNode.Kind.GROUP, path,
                Messages.format("tree.group", name, members.size()), paletteId, false, members, List.of());
    }

    /**
     * The visible name of a {@link #paletteId} (the definition name for user components); the raw id when the type is
     * unknown.
     */
    private static String typeName(String paletteId, ComponentTypeRegistry registry) {
        return TypeNames.of(paletteId, registry);
    }

    /** What groups and names {@code component}: its type id, or {@code nand} for an AND with «Negar salida». */
    private static String paletteId(Component component) {
        return StandardComponentTypes.nameId(component.typeId(), component.parameters());
    }

    /**
     * The rows of {@code node} that {@code query} matches (one of their {@link ComponentTreeNode#matchTexts()}) and
     * their ancestors; empty when none under it matches. The rows kept under a match that do not match themselves are
     * left out, and a group counts the rows it keeps. The document row itself is never matched: callers keep it.
     */
    public static Optional<ComponentTreeNode> filter(ComponentTreeNode node, CompiledQuery query,
                                                     ComponentTypeRegistry registry) {
        List<ComponentTreeNode> kept = new ArrayList<>();
        for (ComponentTreeNode child : node.children()) {
            filter(child, query, registry).ifPresent(kept::add);
        }
        boolean matches = node.matchTexts().stream().anyMatch(query::matches);
        if (!matches && kept.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(node.kind() == ComponentTreeNode.Kind.GROUP
                ? groupNode(node.path(), typeName(node.paletteId(), registry), node.paletteId(), kept)
                : node.withChildren(kept));
    }
}
