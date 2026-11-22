package cl.almejo.vsim.ui.app;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The command line's arguments as files. An argument that is not a valid path on this system (characters Windows
 * forbids, a NUL) is kept apart in {@link #unusable()} so it can be reported like any file that does not open, and
 * never stops the start.
 */
record CommandLineFiles(List<Path> files, List<String> unusable) {

    static CommandLineFiles parse(String[] arguments) {
        List<Path> files = new ArrayList<>();
        List<String> unusable = new ArrayList<>();
        for (String argument : arguments) {
            try {
                files.add(Path.of(argument));
            } catch (InvalidPathException exception) {
                unusable.add(argument);
            }
        }
        return new CommandLineFiles(List.copyOf(files), List.copyOf(unusable));
    }
}
