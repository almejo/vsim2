package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.DefinitionBundle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renaming a component that is also in «Mis componentes» (decision 3 of hito 3): the circuit's definition and the
 * library's file get the new name in one undo step, and undo and redo touch the library only while it still holds
 * what the command left there.
 */
class RenameEverywhereCommandTest {

    private static final String ID = "semaforo";

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentLibrary library = new ComponentLibrary();

    private static ComponentDefinition definition(String name) {
        return new ComponentDefinition(ID, name, "", new GridSize(6, 4), List.of(),
                new CircuitFragment(List.of(), List.of(), List.of()), Optional.empty(), 1);
    }

    private RenameEverywhereCommand rename(String newName) {
        return new RenameEverywhereCommand(ID, newName, library::find, library::put);
    }

    private String circuitName() {
        return document.definition(ID).orElseThrow().name();
    }

    private String libraryName() {
        return library.find(ID).orElseThrow().root().name();
    }

    @Test
    void renamesTheCircuitAndTheLibraryAndUndoRestoresBoth() {
        new PutDefinitionsCommand(List.of(definition("Semáforo"))).apply(document);
        library.put(DefinitionBundle.of(definition("Semáforo"), id -> Optional.empty()));
        RenameEverywhereCommand command = rename("Cruce");

        command.apply(document);

        assertThat(circuitName()).isEqualTo("Cruce");
        assertThat(libraryName()).isEqualTo("Cruce");
        command.revert(document);
        assertThat(circuitName()).isEqualTo("Semáforo");
        assertThat(libraryName()).isEqualTo("Semáforo");
        command.apply(document);
        assertThat(libraryName()).isEqualTo("Cruce");
    }

    @Test
    void aLibraryFileChangedMeanwhileWins() {
        new PutDefinitionsCommand(List.of(definition("Semáforo"))).apply(document);
        library.put(DefinitionBundle.of(definition("Semáforo"), id -> Optional.empty()));
        RenameEverywhereCommand command = rename("Cruce");
        command.apply(document);
        library.put(DefinitionBundle.of(definition("Otro nombre"), id -> Optional.empty()));

        command.revert(document);

        assertThat(circuitName()).isEqualTo("Semáforo");
        assertThat(libraryName()).isEqualTo("Otro nombre");
    }

    @Test
    void aComponentNotInTheLibraryIsRenamedOnlyInTheCircuit() {
        new PutDefinitionsCommand(List.of(definition("Semáforo"))).apply(document);
        RenameEverywhereCommand command = rename("Cruce");

        command.apply(document);

        assertThat(circuitName()).isEqualTo("Cruce");
        assertThat(library.find(ID)).isEmpty();
        assertThat(command.description()).isEqualTo("command.renameComponent");
    }
}
