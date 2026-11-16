package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * The library side of an edit made in the circuit that «Mis componentes» follows («Editar descripción…», renaming a
 * machine): the library's bundle of the component, as it is when the edit is made, and that bundle with the same
 * change. {@link #forward()} and {@link #back()} save one or the other only while the library still holds what the
 * command left there: if something else saved the component meanwhile, that newer file wins.
 */
final class LibraryFollower {

    private final Function<String, Optional<DefinitionBundle>> libraryFind;
    private final Consumer<DefinitionBundle> librarySave;
    private final Optional<DefinitionBundle> before;
    private final Optional<DefinitionBundle> after;

    /**
     * @param change      what the edit does to the library's version of the component (only that part changes)
     * @param libraryFind the library's bundle of a component id, as it is now
     * @param librarySave writes a bundle to the library file (and reports a failure to the user)
     */
    LibraryFollower(String definitionId, UnaryOperator<ComponentDefinition> change,
                    Function<String, Optional<DefinitionBundle>> libraryFind,
                    Consumer<DefinitionBundle> librarySave) {
        this.libraryFind = Objects.requireNonNull(libraryFind, "libraryFind");
        this.librarySave = Objects.requireNonNull(librarySave, "librarySave");
        this.before = libraryFind.apply(definitionId);
        this.after = before.map(bundle -> new DefinitionBundle(change.apply(bundle.root()), bundle.dependencies()));
    }

    /** Saves the changed bundle (apply and redo). */
    void forward() {
        replace(before, after);
    }

    /** Saves the bundle as it was (undo). */
    void back() {
        replace(after, before);
    }

    /** Saves {@code replacement} when the library still holds {@code expected}; nothing when it has no such component. */
    private void replace(Optional<DefinitionBundle> expected, Optional<DefinitionBundle> replacement) {
        if (expected.isEmpty() || replacement.isEmpty()) {
            return;
        }
        String id = expected.get().root().id();
        if (libraryFind.apply(id).equals(expected)) {
            librarySave.accept(replacement.get());
        }
    }
}
