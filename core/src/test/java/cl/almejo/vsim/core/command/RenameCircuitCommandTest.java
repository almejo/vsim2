package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RenameCircuitCommandTest {

    private final CircuitDocument document = new CircuitDocument();
    private final History history = new History(document);

    @Test
    void renamingIsOneUndoStepThatMarksTheDocumentModified() {
        document.setName("Sumador");

        history.execute(RenameCircuitCommand.renaming(document, "Sumador de 4 bits").orElseThrow());

        assertThat(document.name()).isEqualTo("Sumador de 4 bits");
        assertThat(history.isModified()).isTrue();
        assertThat(history.undoDescription()).contains("command.renameCircuit");
        history.undo();
        assertThat(document.name()).isEqualTo("Sumador");
        assertThat(history.isModified()).isFalse();
        history.redo();
        assertThat(document.name()).isEqualTo("Sumador de 4 bits");
    }

    @Test
    void theNameIsTrimmedAndTheSameNameIsNoCommand() {
        document.setName("Sumador");

        assertThat(RenameCircuitCommand.renaming(document, "  Sumador ")).isEmpty();
        RenameCircuitCommand.renaming(document, " Restador ").orElseThrow().apply(document);
        assertThat(document.name()).isEqualTo("Restador");
    }

    @Test
    void aBlankNameIsNoCommand() {
        document.setName("Sumador");

        assertThat(RenameCircuitCommand.renaming(document, "   ")).isEmpty();
    }
}
