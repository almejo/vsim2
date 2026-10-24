package cl.almejo.vsim.core.library;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Compares the definitions a document embeds with the user's library (spec §5.6 "Portabilidad de circuitos"). */
public final class LibraryVersions {

    /** Which counter is ahead when contents differ; equal counters can describe divergent edits. */
    public enum Relation {
        LIBRARY_NEWER, CIRCUIT_NEWER, DIVERGENT
    }

    /** A difference notice; it never authorizes updating a circuit's embedded component automatically. */
    public record RevisionDifference(String definitionId, long circuitRevision, long libraryRevision,
                                     Relation relation) {
        public RevisionDifference {
            Objects.requireNonNull(definitionId, "definitionId");
            Objects.requireNonNull(relation, "relation");
        }
    }

    private LibraryVersions() {
    }

    /** Ids of {@code documentDefinitions} whose library entry (same id) has different content. */
    public static Set<String> outdated(Collection<ComponentDefinition> documentDefinitions, ComponentLibrary library) {
        return differences(documentDefinitions, library).stream()
                .map(RevisionDifference::definitionId)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Compares every supplied definition, including nested dependencies, with its standalone library entry. */
    public static List<RevisionDifference> differences(Collection<ComponentDefinition> documentDefinitions,
                                                       ComponentLibrary library) {
        Objects.requireNonNull(documentDefinitions, "documentDefinitions");
        Objects.requireNonNull(library, "library");
        return documentDefinitions.stream().flatMap(definition -> library.find(definition.id()).stream()
                .map(DefinitionBundle::root)
                .filter(available -> !definition.sameContentAs(available))
                .map(available -> new RevisionDifference(definition.id(), definition.revision(), available.revision(),
                        relation(definition.revision(), available.revision())))).toList();
    }

    private static Relation relation(long circuitRevision, long libraryRevision) {
        if (libraryRevision > circuitRevision) {
            return Relation.LIBRARY_NEWER;
        }
        return circuitRevision > libraryRevision ? Relation.CIRCUIT_NEWER : Relation.DIVERGENT;
    }
}
