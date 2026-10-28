package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.ChangeDefinitionDescriptionCommand;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.DefinitionBundle;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * «Editar descripción…» as one undo step: the circuit's definition gets the new text
 * ({@link ChangeDefinitionDescriptionCommand}) and, when the library has the component, so does its library file, so
 * the instance does not look outdated. Deshacer puts the library file back as it was and Rehacer writes the edit again,
 * so neither leaves the library ahead of the circuit.
 *
 * <p>Only the library's own text changes (the rest of its version stays, newer or not). Undo and redo touch the
 * library only while it still holds what this command left there: if something else saved the component meanwhile,
 * that newer file wins and only the circuit changes.
 */
final class EditDescriptionCommand implements Command {

    private final ChangeDefinitionDescriptionCommand change;
    private final Function<String, Optional<DefinitionBundle>> libraryFind;
    private final Consumer<DefinitionBundle> librarySave;
    private final Optional<DefinitionBundle> libraryBefore;
    private final Optional<DefinitionBundle> libraryAfter;

    /**
     * @param libraryFind the library's bundle of a component id, as it is now
     * @param librarySave writes a bundle to the library file (and reports a failure to the user)
     */
    EditDescriptionCommand(String definitionId, String newDescription,
                           Function<String, Optional<DefinitionBundle>> libraryFind,
                           Consumer<DefinitionBundle> librarySave) {
        this.change = new ChangeDefinitionDescriptionCommand(definitionId, newDescription);
        this.libraryFind = Objects.requireNonNull(libraryFind, "libraryFind");
        this.librarySave = Objects.requireNonNull(librarySave, "librarySave");
        this.libraryBefore = libraryFind.apply(definitionId);
        this.libraryAfter = libraryBefore.map(bundle -> new DefinitionBundle(
                bundle.root().withDescription(newDescription), bundle.dependencies()));
    }

    @Override
    public void apply(CircuitDocument document) {
        change.apply(document);
        replaceInLibrary(libraryBefore, libraryAfter);
    }

    @Override
    public void revert(CircuitDocument document) {
        change.revert(document);
        replaceInLibrary(libraryAfter, libraryBefore);
    }

    @Override
    public String description() {
        return change.description();
    }

    /** Saves {@code replacement} when the library still holds {@code expected}; nothing when it has no such component. */
    private void replaceInLibrary(Optional<DefinitionBundle> expected, Optional<DefinitionBundle> replacement) {
        if (expected.isEmpty() || replacement.isEmpty()) {
            return;
        }
        String id = expected.get().root().id();
        if (libraryFind.apply(id).equals(expected)) {
            librarySave.accept(replacement.get());
        }
    }
}
