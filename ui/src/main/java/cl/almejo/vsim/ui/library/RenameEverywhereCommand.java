package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.RenameDefinitionCommand;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.DefinitionBundle;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Renaming a component from Propiedades as one undo step (a machine's «Nombre», hito 3 decision 3): the circuit's
 * definition gets the new name ({@link RenameDefinitionCommand}) and, when «Mis componentes» has the component, so
 * does its library file, so the palette, the library and the circuit keep calling it the same. Deshacer puts the
 * library file back as it was and Rehacer writes the new name again.
 *
 * <p>As in {@link EditDescriptionCommand}, only the library's own name changes (the rest of its version stays, newer or
 * not), and undo and redo touch the library only while it still holds what this command left there
 * ({@link LibraryFollower}).
 */
public final class RenameEverywhereCommand implements Command {

    private final RenameDefinitionCommand change;
    private final LibraryFollower library;

    /**
     * @param libraryFind the library's bundle of a component id, as it is now
     * @param librarySave writes a bundle to the library file (and reports a failure to the user)
     */
    public RenameEverywhereCommand(String definitionId, String newName,
                                   Function<String, Optional<DefinitionBundle>> libraryFind,
                                   Consumer<DefinitionBundle> librarySave) {
        this.change = new RenameDefinitionCommand(definitionId, newName);
        this.library = new LibraryFollower(definitionId, definition -> definition.withName(newName.strip()),
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
