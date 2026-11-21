package cl.almejo.vsim.ui.tree;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.machine.MachineChecks;
import cl.almejo.vsim.core.machine.MachineFinding;
import cl.almejo.vsim.core.machine.MachineNote;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.core.search.CompiledQuery;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.search.TypeNames;
import cl.almejo.vsim.ui.tree.CircuitTreeNode.MachineRow;
import cl.almejo.vsim.ui.tree.CircuitTreeNode.MachineRow.Kind;
import cl.almejo.vsim.ui.tree.CircuitTreeNode.MachineRow.Severity;
import cl.almejo.vsim.ui.window.InstanceNames;

import java.util.ArrayList;
import java.util.HashMap;
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

    /** Between a machine piece's path and a state's id; no piece id has it. */
    public static final String STATE_SEPARATOR = "#";
    /** After a machine piece's path: the notes group; a note's id follows it. */
    public static final String NOTES_SEPARATOR = "#notas:";
    private static final int NOTE_NAME_LIMIT = 28;

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
            // a state machine's body is only the pins generated from its diagram: its rows are its states and notes
            List<CircuitTreeNode> children = definition.filter(found -> !found.isMachine()).isPresent()
                    && depth < MAXIMUM_DEPTH
                    ? pieces(definition.get().body().components(), path, document, registry, depth + 1)
                    : definition.map(found -> machineRows(found, path)).orElse(List.of());
            String typeName = TypeNames.of(component, registry);
            Optional<String> kind = component.label().isBlank() || definition.isPresent() ? Optional.empty()
                    : Optional.of(typeName);
            nodes.add(new CircuitTreeNode(path, names.get(component.id()), kind, typeName,
                    StandardComponentTypes.nameId(component.typeId(), component.parameters()),
                    registry.findTypeOf(component), definition.map(ComponentDefinition::id), Optional.empty(),
                    children));
        }
        return nodes;
    }

    /** The rows of a machine's piece: a row per state, then the notes group (none for a circuit's definition). */
    private static List<CircuitTreeNode> machineRows(ComponentDefinition definition, String path) {
        if (definition.machine().isEmpty()) {
            return List.of();
        }
        StateMachine machine = definition.machine().get();
        Map<String, Severity> problems = problemsOf(machine);
        List<CircuitTreeNode> rows = new ArrayList<>();
        for (MachineState state : machine.states()) {
            boolean initial = machine.initialStateId().filter(state.id()::equals).isPresent();
            rows.add(machineNode(path + STATE_SEPARATOR + state.id(), state.name(), new MachineRow(Kind.STATE,
                    definition.id(), state.id(), initial, state.color(),
                    problems.getOrDefault(state.id(), Severity.NONE)), List.of()));
        }
        if (!machine.notes().isEmpty()) {
            String notesPath = path + NOTES_SEPARATOR;
            List<CircuitTreeNode> notes = machine.notes().stream().map(note -> machineNode(
                    notesPath + note.id(), noteName(note), new MachineRow(Kind.NOTE, definition.id(), note.id(), false,
                            Optional.empty(), Severity.NONE), List.of())).toList();
            rows.add(machineNode(notesPath, Messages.format("tree.machine.notes", machine.notes().size()),
                    new MachineRow(Kind.NOTES, definition.id(), "", false, Optional.empty(), Severity.NONE), notes));
        }
        return rows;
    }

    private static CircuitTreeNode machineNode(String path, String name, MachineRow row,
                                               List<CircuitTreeNode> children) {
        return new CircuitTreeNode(path, name, Optional.empty(), "", "", Optional.empty(), Optional.empty(),
                Optional.of(row), children);
    }

    /** The first line of the note, cut; a word when it is empty. */
    private static String noteName(MachineNote note) {
        String firstLine = note.text().strip().lines().findFirst().orElse("").strip();
        if (firstLine.isEmpty()) {
            return Messages.text("tree.machine.noteEmpty");
        }
        return firstLine.length() <= NOTE_NAME_LIMIT ? firstLine : firstLine.substring(0, NOTE_NAME_LIMIT - 1) + "…";
    }

    /** The worst finding of each state that is in one: an error over a warning. */
    private static Map<String, Severity> problemsOf(StateMachine machine) {
        Map<String, Severity> problems = new HashMap<>();
        for (MachineFinding finding : MachineChecks.check(machine)) {
            Severity severity = finding.isWarning() ? Severity.WARNING : Severity.ERROR;
            for (String stateId : finding.stateIds()) {
                problems.merge(stateId, severity, (first, second) -> first.compareTo(second) >= 0 ? first : second);
            }
        }
        return problems;
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
