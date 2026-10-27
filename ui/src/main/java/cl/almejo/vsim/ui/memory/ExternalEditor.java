package cl.almejo.vsim.ui.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * «Abrir en editor externo» (memory spec §8.2): opens a linked memory file with the command chosen in Preferencias
 * («Editor externo»), or with the system's editor when none is chosen. Injectable, so tests never start a program.
 */
public interface ExternalEditor {

    /**
     * Opens {@code file} with {@code command} (a program and its arguments, the file is added last), or with the
     * system's editor when {@code command} is empty. False when it could not be opened.
     */
    boolean open(Path file, Optional<String> command);

    /** The real one: a process for a chosen command, {@link Desktop} otherwise, guarded where it is unsupported. */
    static ExternalEditor system() {
        return new SystemExternalEditor();
    }

    /** {@link ExternalEditor#system()}. */
    final class SystemExternalEditor implements ExternalEditor {

        private static final Logger LOGGER = LoggerFactory.getLogger(SystemExternalEditor.class);

        @Override
        public boolean open(Path file, Optional<String> command) {
            Optional<String> chosen = command.map(String::strip).filter(text -> !text.isEmpty());
            return chosen.isPresent() ? startCommand(chosen.get(), file) : openWithDesktop(file);
        }

        private static boolean startCommand(String command, Path file) {
            List<String> arguments = new ArrayList<>(words(command));
            arguments.add(file.toString());
            try {
                new ProcessBuilder(arguments).start();
                return true;
            } catch (IOException | SecurityException exception) {
                LOGGER.warn("Could not start the external editor {} for {}", command, file, exception);
                return false;
            }
        }

        /** {@code command} split on spaces, keeping what is between double quotes together ("C:\Program Files\…"). */
        static List<String> words(String command) {
            List<String> words = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean quoted = false;
            for (char character : command.toCharArray()) {
                if (character == '"') {
                    quoted = !quoted;
                } else if (Character.isWhitespace(character) && !quoted) {
                    if (!current.isEmpty()) {
                        words.add(current.toString());
                        current.setLength(0);
                    }
                } else {
                    current.append(character);
                }
            }
            if (!current.isEmpty()) {
                words.add(current.toString());
            }
            return words;
        }

        private static boolean openWithDesktop(Path file) {
            try {
                if (GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()) {
                    return false;
                }
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.EDIT)) {
                    try {
                        desktop.edit(file.toFile());
                        return true;
                    } catch (IOException exception) {
                        LOGGER.info("No editor registered for {}; trying to open it", file, exception);
                    }
                }
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    desktop.open(file.toFile());
                    return true;
                }
                return false;
            } catch (IOException | UnsupportedOperationException | SecurityException | IllegalArgumentException
                     exception) {
                LOGGER.warn("Could not open {} in the system's editor", file, exception);
                return false;
            }
        }
    }
}
