package cl.almejo.vsim.ui.memory;

import java.nio.file.Path;
import java.util.Optional;

/**
 * What linking a memory and importing a file as a memory ask the user through a window, so tests answer without one
 * ({@link SwingMemoryLinkPrompts} is the real one).
 */
public interface MemoryLinkPrompts {

    /** «Enlazar…»: the text file to link the memory to. */
    Optional<Path> chooseLinkFile(java.awt.Component parent);

    /** Archivo ▸ Importar ▸ «Memoria desde archivo…»: the file to make a new memory of. */
    Optional<Path> chooseMemoryFile(java.awt.Component parent);

    /**
     * A file without a header: RAM or ROM, words, width and name, starting from what the file suggests
     * ({@code proposal}); empty when the user cancels.
     */
    Optional<MemoryFileImport.Proposal> chooseKind(java.awt.Component parent, String fileName,
                                                   MemoryFileImport.Proposal proposal);

    void inform(java.awt.Component parent, String message);
}
