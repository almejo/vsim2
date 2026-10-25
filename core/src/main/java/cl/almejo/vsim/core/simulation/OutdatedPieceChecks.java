package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.DefinitionUpdateSafety;
import cl.almejo.vsim.core.library.LibraryVersions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.library.UnsafeDefinitionUpdateException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Finds the pieces of a circuit that use an older version of a definition than the library's and whose update would
 * leave connections loose inside their bodies, or that cannot be updated at all because the definition is used inside
 * another component and its pins or size changed. It only reports: nothing is ever updated automatically, and the
 * document is never changed (the update is tried on a copy).
 */
public final class OutdatedPieceChecks {

    /** Marks {@link ConnectivityProblem#netId()} as naming the definition whose update is the cause. */
    public static final String VERSION_PREFIX = "version:";

    private OutdatedPieceChecks() {
    }

    /** One problem per top-level piece that an update of an older definition would break, in canvas order. */
    public static List<ConnectivityProblem> problems(CircuitDocument document, ComponentLibrary library,
                                                     ComponentTypeRegistry baseRegistry) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(library, "library");
        Objects.requireNonNull(baseRegistry, "baseRegistry");
        List<String> olderIds = LibraryVersions.differences(document.definitions(), library).stream()
                .filter(difference -> difference.relation() == LibraryVersions.Relation.LIBRARY_NEWER)
                .map(LibraryVersions.RevisionDifference::definitionId)
                .toList();
        if (olderIds.isEmpty()) {
            return List.of();
        }
        Set<FaultKey> existing = faults(document, baseRegistry).orElse(Set.of());
        List<ConnectivityProblem> reported = new ArrayList<>();
        for (String definitionId : olderIds) {
            reported.addAll(brokenBy(definitionId, document, library, baseRegistry, existing));
        }
        return List.copyOf(reported);
    }

    private static List<ConnectivityProblem> brokenBy(String definitionId, CircuitDocument document,
                                                      ComponentLibrary library, ComponentTypeRegistry baseRegistry,
                                                      Set<FaultKey> existing) {
        CircuitDocument updated = document.copy();
        DefinitionBundle available = library.find(definitionId).orElseThrow();
        for (ComponentDefinition dependency : available.dependencies()) {
            if (updated.definition(dependency.id()).isEmpty()) {
                updated.putDefinition(dependency);
            }
        }
        ConnectivityProblem.Cause cause = ConnectivityProblem.Cause.OUTDATED_WOULD_BREAK;
        try {
            DefinitionUpdateSafety.requireSafe(updated, available.root());
        } catch (UnsafeDefinitionUpdateException exception) {
            cause = ConnectivityProblem.Cause.OUTDATED_CANNOT_UPDATE;
        }
        updated.putDefinition(available.root());
        ConnectivityProblem.Cause reportedCause = cause;
        Optional<List<ConnectivityProblem>> after = problemsOf(updated, baseRegistry);
        if (after.isEmpty()) {
            return List.of();
        }
        Set<String> pieces = new LinkedHashSet<>();
        for (ConnectivityProblem problem : after.get()) {
            if (!existing.contains(FaultKey.of(problem))) {
                pieces.add(InstancePath.topLevel(problem.elementId()));
            }
        }
        List<ConnectivityProblem> reported = new ArrayList<>();
        for (String piece : pieces) {
            document.component(new ElementId(piece)).map(Component::position).ifPresent(position ->
                    reported.add(new ConnectivityProblem(VERSION_PREFIX + definitionId, piece, -1, position,
                            reportedCause)));
        }
        return reported;
    }

    private static Optional<Set<FaultKey>> faults(CircuitDocument document, ComponentTypeRegistry baseRegistry) {
        return problemsOf(document, baseRegistry).map(problems -> {
            Set<FaultKey> keys = new HashSet<>();
            problems.forEach(problem -> keys.add(FaultKey.of(problem)));
            return keys;
        });
    }

    /**
     * Empty when the circuit cannot be checked (for instance an instance override that no longer matches its
     * definition): then nothing is claimed about it.
     */
    private static Optional<List<ConnectivityProblem>> problemsOf(CircuitDocument document,
                                                                  ComponentTypeRegistry baseRegistry) {
        try {
            return Optional.of(ConnectivityChecks.problems(document,
                    baseRegistry.extendedWith(SubcircuitTypes.resolverFor(document))));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private record FaultKey(ConnectivityProblem.Cause cause, String elementId, int pinIndex) {
        static FaultKey of(ConnectivityProblem problem) {
            return new FaultKey(problem.cause(), problem.elementId(), problem.pinIndex());
        }
    }
}
