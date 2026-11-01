package cl.almejo.vsim.ui.terminal;

import java.nio.file.Path;
import java.util.Optional;

/** Asks where to save a .txt («Guardar .txt»); injectable so tests never show a chooser. */
@FunctionalInterface
public interface TextFileChooser {

    /** The file chosen, starting from {@code suggestedName}; empty when the user cancelled. */
    Optional<Path> chooseFile(java.awt.Component parent, String suggestedName);
}
