package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.KeptOldShape;
import cl.almejo.vsim.core.library.PieceMemories;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The rows of the Problemas list of one circuit, in its order: what the simulation found, the wiring warnings (a wire
 * found at a piece's edge only once: this session's row for the removed pin it went to stands for it), the pins
 * this session's shape changes left loose, the new pins an inside edit put elsewhere, the pieces an update of the
 * library would break, the linked memory files that cannot be used and the pieces' own memory contents that do not
 * apply. The editor's list and the AI's read are both made here, so they never disagree (fix round D, the user: a
 * problem is never hidden, not from the AI either). The component library's own load problems are not the circuit's
 * and are listed apart.
 */
public final class ListedProblems {

    private ListedProblems() {
    }

    /**
     * What the rows are made of. {@code simulated}: the problems of the snapshot shown (its connectivity problems are
     * replaced by {@code connectivity}, the editor's, which are up to date without a simulation tick and hold what inside
     * edits could not carry); {@code loosenedPins} and {@code keptOldShapes}: this session's, while their edit is applied;
     * {@code outdatedPieces} and {@code linkedFiles}: what the editor's checks of the library and of the files reported.
     */
    public record Sources(List<Problem> simulated, List<ConnectivityProblem> connectivity, Set<PinRef> loosenedPins,
                          List<KeptOldShape> keptOldShapes, List<ConnectivityProblem> outdatedPieces,
                          List<LinkedFileProblem> linkedFiles) {

        public Sources {
            simulated = List.copyOf(simulated);
            connectivity = List.copyOf(connectivity);
            loosenedPins = Set.copyOf(loosenedPins);
            keptOldShapes = List.copyOf(keptOldShapes);
            outdatedPieces = List.copyOf(outdatedPieces);
            linkedFiles = List.copyOf(linkedFiles);
        }

        /**
         * The sources of a circuit no editor shows (no session, no library check): what {@code snapshot} published,
         * its connectivity problems included, and {@code linkedFiles}.
         */
        public static Sources published(SimulationSnapshot snapshot, List<LinkedFileProblem> linkedFiles) {
            List<ConnectivityProblem> connectivity = snapshot.problems().stream()
                    .filter(ConnectivityProblem.class::isInstance).map(ConnectivityProblem.class::cast).toList();
            return new Sources(snapshot.problems(), connectivity, Set.of(), List.of(), List.of(), linkedFiles);
        }

        /** These sources with another view's own reports (a component tab lists its own document's). */
        public Sources withReports(List<ConnectivityProblem> otherOutdatedPieces,
                                   List<LinkedFileProblem> otherLinkedFiles) {
            return new Sources(simulated, connectivity, loosenedPins, keptOldShapes, otherOutdatedPieces,
                    otherLinkedFiles);
        }
    }

    /** The rows for circuit {@code document} (its types resolved by {@code registry}), in the list's order. */
    public static List<Problem> of(CircuitDocument document, ComponentTypeRegistry registry, Sources sources) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(sources, "sources");
        List<Problem> problems = new ArrayList<>(sources.simulated().stream()
                .filter(problem -> !(problem instanceof ConnectivityProblem)).toList());
        problems.addAll(withoutRepeatedWires(sources.connectivity()));
        problems.addAll(loosenedPins(document, registry, sources.loosenedPins()));
        problems.addAll(pinsPlacedElsewhere(document, registry, sources.keptOldShapes()));
        problems.addAll(sources.outdatedPieces());
        problems.addAll(sources.linkedFiles());
        problems.addAll(storedContents(document));
        return List.copyOf(problems);
    }

    /**
     * {@code problems} without the wire ends found at a piece's edge
     * ({@link ConnectivityProblem.Cause#WIRE_AT_PIECE_EDGE}) that this session also lists as the wire of a removed
     * pin, at the same place: that row says which pin it was, so the wire is listed once, with the richer text. The
     * order is kept.
     */
    public static List<ConnectivityProblem> withoutRepeatedWires(List<ConnectivityProblem> problems) {
        Set<WireEnd> named = new HashSet<>();
        for (ConnectivityProblem problem : problems) {
            if (problem.cause() == ConnectivityProblem.Cause.WIRE_OF_REMOVED_PIN) {
                named.add(new WireEnd(problem.elementId(), problem.position()));
            }
        }
        if (named.isEmpty()) {
            return problems;
        }
        return problems.stream().filter(problem -> problem.cause() != ConnectivityProblem.Cause.WIRE_AT_PIECE_EDGE
                || !named.contains(new WireEnd(problem.elementId(), problem.position()))).toList();
    }

    /** A wire end of piece {@code elementId} (a path inside a component) at {@code position} of its level. */
    private record WireEnd(String elementId, GridPoint position) {
    }

    /** One problem per pin a shape change left loose in this session, for the pieces still on the canvas. */
    private static List<ConnectivityProblem> loosenedPins(CircuitDocument document, ComponentTypeRegistry registry,
                                                          Set<PinRef> pins) {
        List<ConnectivityProblem> loosened = new ArrayList<>();
        for (PinRef pin : pins) {
            Optional<Component> component = document.component(pin.componentId());
            if (component.isEmpty()) {
                continue;
            }
            ComponentType type = registry.typeOf(component.get());
            if (pin.pinIndex() < type.pinCount()) {
                loosened.add(new ConnectivityProblem(ConnectivityProblem.LOOSENED_PREFIX + pin.componentId().value()
                        + ":" + pin.pinIndex(), pin.componentId().value(), pin.pinIndex(),
                        type.pinLocation(component.get(), pin.pinIndex()),
                        ConnectivityProblem.Cause.LOOSENED_BY_SHAPE_CHANGE));
            }
        }
        loosened.sort(Comparator.comparing(ConnectivityProblem::netId));
        return loosened;
    }

    /**
     * One problem per new pin an inside edit could not put where its component puts it, for the pieces still on the
     * canvas (spec §6.2 a): the pin is on another spot of the piece's edge, and its old shape stays.
     */
    private static List<ConnectivityProblem> pinsPlacedElsewhere(CircuitDocument document,
                                                                 ComponentTypeRegistry registry,
                                                                 List<KeptOldShape> keptOldShapes) {
        List<ConnectivityProblem> placed = new ArrayList<>();
        for (KeptOldShape kept : keptOldShapes) {
            Optional<Component> component = document.component(kept.instance());
            if (component.isEmpty()) {
                continue;
            }
            ComponentType type = registry.typeOf(component.get());
            for (String pinName : kept.newPinNames()) {
                type.pinIndexOf(pinName).ifPresent(pinIndex -> placed.add(new ConnectivityProblem(
                        ConnectivityProblem.PLACED_ELSEWHERE_PREFIX + kept.instance().value() + ":" + pinIndex,
                        kept.instance().value(), pinIndex, type.pinLocation(component.get(), pinIndex),
                        ConnectivityProblem.Cause.PIN_PLACED_ELSEWHERE)));
            }
        }
        return placed;
    }

    /**
     * One problem per piece's own memory contents that do not apply (debt of task 9b: they were ignored and
     * invisible): kept for a memory no longer in its component, or for a linked memory, whose file the piece then
     * does not use.
     */
    private static List<ConnectivityProblem> storedContents(CircuitDocument document) {
        List<ConnectivityProblem> problems = new ArrayList<>();
        for (PieceMemories.StoredContentsIssue issue : PieceMemories.storedContentsIssues(document)) {
            ElementId piece = issue.place().piece();
            GridPoint position = document.component(piece).map(Component::position).orElse(GridPoint.ORIGIN);
            String netId = ConnectivityProblem.OWN_CONTENTS_PREFIX + issue.place().innerPath();
            problems.add(switch (issue.kind()) {
                case FOR_NOTHING -> new ConnectivityProblem(netId, piece.value(), -1, position,
                        ConnectivityProblem.Cause.OWN_CONTENTS_FOR_NOTHING);
                // named by the memory's path, so F8 enters the component with this piece's values and selects it
                case OVER_LINK -> new ConnectivityProblem(netId, InstancePath.child(piece.value(),
                        issue.place().innerPath()), -1, position, ConnectivityProblem.Cause.OWN_CONTENTS_OVER_LINK);
            });
        }
        return problems;
    }
}
