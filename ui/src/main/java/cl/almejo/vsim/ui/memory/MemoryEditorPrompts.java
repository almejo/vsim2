package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.memory.MemoryFileFormat;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

/** Everything the content editor asks the user through a window or the system, so tests can answer without one. */
public interface MemoryEditorPrompts {

    /** A file chosen to save, and the format the user picked for it. */
    record SaveChoice(Path path, MemoryFileFormat format) {
    }

    /** What «Llenar…» was given: first and last address (hex text) and the value (text in the shown base). */
    record FillRequest(String firstAddress, String lastAddress, String value) {
    }

    Optional<Path> chooseImport();

    /**
     * A file to save and its format, chosen among the formats that can write. {@code titleKey} is the bundle key of
     * the window title; {@code refusal} gives the reason a chosen file must not be written (shown at once, before
     * any overwrite question, and the choice is then cancelled). An existing file was confirmed.
     */
    Optional<SaveChoice> chooseSave(String suggestedName, String titleKey,
                                    Function<Path, Optional<String>> refusal);

    Optional<FillRequest> askFill(String firstAddress, String lastAddress);

    /** The text typed in «Ir a…». */
    Optional<String> askAddress();

    boolean confirm(String message);

    void inform(String message);

    void copyToClipboard(String text);

    Optional<String> clipboardText();
}
