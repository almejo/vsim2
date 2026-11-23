package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.ChangeDefinitionDescriptionCommand;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.DefinitionBundle;

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
 * that newer file wins and only the circuit changes ({@link LibraryFollower}).
 */
final class EditDescriptionCommand implements Command {

    private final ChangeDefinitionDescriptionCommand change;
    private final LibraryFollower library;

    /**
     * @param libraryFind the library's bundle of a component id, as it is now
     * @param librarySave writes a bundle to the library file (and reports a failure to the user)
     */
    EditDescriptionCommand(String definitionId, String newDescription,
                           Function<String, Optional<DefinitionBundle>> libraryFind,
                           Consumer<DefinitionBundle> librarySave) {
        this.change = new ChangeDefinitionDescriptionCommand(definitionId, newDescription);
        this.library = new LibraryFollower(definitionId, definition -> definition.withDescription(newDescription),
                libraryFind, librarySave);
    }

    @Override
    public void apply(CircuitDocument document) {
        change.apply(document);
        library.forward();
    }

    @Override
    public void revert(CircuitDocument document) {
        change.revert(document);
        library.back();
    }

    @Override
    public String description() {
        return change.description();
    }
}
