package cl.almejo.vsim.core.library;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/** Compares the definitions a document embeds with the user's library (spec §5.6 "Portabilidad de circuitos"). */
public final class LibraryVersions {

    private LibraryVersions() {
    }

    /** Ids of {@code documentDefinitions} whose library entry (same id) has different content. */
    public static Set<String> outdated(Collection<ComponentDefinition> documentDefinitions, ComponentLibrary library) {
        return documentDefinitions.stream()
                .filter(definition -> library.find(definition.id())
                        .map(bundle -> !bundle.root().equals(definition))
                        .orElse(false))
                .map(ComponentDefinition::id)
                .collect(Collectors.toUnmodifiableSet());
    }
}
