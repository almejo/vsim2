package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.PinReference;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionUpdateSafety;
import cl.almejo.vsim.core.library.PieceMemories;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.core.simulation.OutdatedPieceChecks;

/**
 * The AI's view of the Problemas rows about connections and pieces ({@link ConnectivityProblem}): a terminal with
 * nothing inside, this session's loose pins, pins placed elsewhere and wires of removed pins, the pieces a library update
 * would break, and the pieces' own memory contents that do not apply. Messages in English, as the other kinds.
 */
final class ConnectivityProblemViews {

    /** Said of the rows the editor keeps only while their edit is applied, never in the file (M6, undecided). */
    private static final String SESSION_ONLY = " Listed only during this session, while that edit is applied.";

    private ConnectivityProblemViews() {
    }

    /** The kind the AI reads for {@code cause}. */
    static String kindOf(ConnectivityProblem.Cause cause) {
        return switch (cause) {
            case INNER_PIN, LOOSE_WIRE_END -> throw neverListed(cause);
            case UNCONNECTED_INTERFACE -> "INTERFACE_UNCONNECTED_INSIDE";
            case LOOSENED_BY_SHAPE_CHANGE -> "PIN_LOOSENED_BY_SHAPE_CHANGE";
            case PIN_PLACED_ELSEWHERE, WIRE_OF_REMOVED_PIN, OUTDATED_WOULD_BREAK, OUTDATED_CANNOT_UPDATE,
                 OWN_CONTENTS_FOR_NOTHING, OWN_CONTENTS_OVER_LINK -> cause.name();
        };
    }

    static ProblemView view(ReadSource source, ConnectivityProblem problem) {
        ProblemReferences references = new ProblemReferences(source);
        String kind = kindOf(problem.cause());
        String elementId = problem.elementId();
        return switch (problem.cause()) {
            case INNER_PIN, LOOSE_WIRE_END -> throw neverListed(problem.cause());
            case UNCONNECTED_INTERFACE -> {
                references.pin(elementId, problem.pinIndex());
                yield references.view(kind, true, problem.netId(), "Terminal " + problem.pinIndex() + " of "
                        + elementId + " is not connected to anything inside the component; the fault is inside,"
                        + " not in the wire outside");
            }
            case LOOSENED_BY_SHAPE_CHANGE -> {
                references.pin(elementId, problem.pinIndex());
                yield references.view(kind, true, problem.netId(), "Pin " + pinName(source, problem) + " of "
                        + pieceName(source, elementId) + " was left unconnected on purpose when its component changed"
                        + " shape («Aplicar y conectar a mano»): connect it again by hand. Listed only during this"
                        + " session, until it is connected again.");
            }
            case PIN_PLACED_ELSEWHERE -> {
                references.pin(elementId, problem.pinIndex());
                yield references.view(kind, true, problem.netId(), "Pin " + pinName(source, problem) + " of "
                        + pieceName(source, elementId) + ", added by an edit inside its component, went to another"
                        + " spot of the piece's edge because its own spot touched a wire or a pin; the piece keeps its"
                        + " old shape." + SESSION_ONLY);
            }
            case WIRE_OF_REMOVED_PIN -> {
                references.element(elementId);
                yield references.view(kind, true, problem.netId(), "A wire end at (" + problem.position().x()
                        + ", " + problem.position().y() + ") went to pin \"" + removedPinName(problem) + "\" of "
                        + pieceName(source, elementId) + ", which an edit inside its component removed: the wire"
                        + " stays, loose." + SESSION_ONLY);
            }
            case OUTDATED_WOULD_BREAK -> {
                references.element(elementId);
                yield references.view(kind, true, problem.netId(), pieceName(source, elementId)
                        + " uses an older version of " + outdatedName(source, problem) + " than the library's;"
                        + " updating it would leave inner connections unconnected. Nothing is updated by itself");
            }
            case OUTDATED_CANNOT_UPDATE -> {
                references.element(elementId);
                yield references.view(kind, true, problem.netId(), pieceName(source, elementId)
                        + " uses an older version of " + outdatedName(source, problem) + " than the library's, which"
                        + " cannot be updated because it is used inside " + String.join(", ",
                        DefinitionUpdateSafety.containingNames(source.document(), outdatedId(problem)))
                        + ": its pins or size would change. The old copy is kept");
            }
            case OWN_CONTENTS_OVER_LINK, OWN_CONTENTS_FOR_NOTHING -> ownContents(source, problem, references);
        };
    }

    /**
     * A piece's own contents of a memory that do not do what they seem, as the Problemas list shows them: over a
     * linked file (pointing at the memory inside the piece) or for a memory no longer in the component (pointing at
     * the piece). The net id is {@code ownContents:} and the memory's path from the circuit ({@code c5/c10}).
     */
    private static ProblemView ownContents(ReadSource source, ConnectivityProblem problem,
                                           ProblemReferences references) {
        String piece = InstancePath.topLevel(problem.elementId());
        String innerPath = problem.netId().substring(ConnectivityProblem.OWN_CONTENTS_PREFIX.length());
        String memoryPath = InstancePath.child(piece, innerPath);
        String pieceName = source.document().component(new ElementId(piece)).map(ConnectivityProblemViews::nameOf)
                .orElse(piece);
        String netId = ConnectivityProblem.OWN_CONTENTS_PREFIX + memoryPath;
        String kind = kindOf(problem.cause());
        if (problem.cause() == ConnectivityProblem.Cause.OWN_CONTENTS_OVER_LINK) {
            references.element(memoryPath);
            String memoryName = PieceMemories.memory(source.document(),
                            new PieceMemories.Place(new ElementId(piece), innerPath))
                    .map(ConnectivityProblemViews::nameOf).orElse(innerPath);
            return references.view(kind, true, netId, "The piece " + pieceName + " (" + piece
                    + ") keeps its own contents for the memory " + memoryName + " (" + memoryPath
                    + "), which is linked to a file: this piece starts from its own contents, not from the linked "
                    + "file. The user can go back to the component's contents from the Problemas list (right "
                    + "click on the problem)");
        }
        references.element(piece);
        return references.view(kind, true, netId, "The piece " + pieceName + " (" + piece
                + ") keeps its own contents for a memory " + innerPath + " that is no longer in its component: they"
                + " are ignored. The user can forget them from the Problemas list (right click on the problem)");
    }

    static String nameOf(Component component) {
        return component.label().isBlank() ? component.typeId() : component.label();
    }

    /** «U1 (c20)» for a piece of the circuit; the inner path as it is for one inside a component. */
    private static String pieceName(ReadSource source, String flatId) {
        if (InstancePath.isNested(flatId)) {
            return flatId;
        }
        return source.document().component(new ElementId(flatId))
                .map(component -> nameOf(component) + " (" + flatId + ")").orElse(flatId);
    }

    /** {@code "a"} for a pin of a piece of the circuit; the pin index for one inside a component. */
    private static String pinName(ReadSource source, ConnectivityProblem problem) {
        if (InstancePath.isNested(problem.elementId())) {
            return Integer.toString(problem.pinIndex());
        }
        return CircuitReader.reference(source, new PinRef(new ElementId(problem.elementId()), problem.pinIndex()))
                .map(PinReference::pinName).map(name -> "\"" + name + "\"")
                .orElse(Integer.toString(problem.pinIndex()));
    }

    /** The name the removed pin had: what follows the element id in the net id. */
    private static String removedPinName(ConnectivityProblem problem) {
        String rest = problem.netId().substring(ConnectivityProblem.REMOVED_PIN_PREFIX.length());
        return rest.substring(rest.indexOf(':') + 1);
    }

    private static String outdatedId(ConnectivityProblem problem) {
        return problem.netId().substring(OutdatedPieceChecks.VERSION_PREFIX.length());
    }

    private static String outdatedName(ReadSource source, ConnectivityProblem problem) {
        String definitionId = outdatedId(problem);
        return source.document().definition(definitionId).map(ComponentDefinition::name).orElse(definitionId);
    }

    /**
     * Inside a component the canvas rule holds (a loose inner pin or wire end is no problem), so these two are never
     * listed; only the library-update check looks for them, as the editor's list says too.
     */
    private static IllegalStateException neverListed(ConnectivityProblem.Cause cause) {
        return new IllegalStateException("never published: " + cause);
    }
}
