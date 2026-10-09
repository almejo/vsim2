package cl.almejo.vsim.ui.theme;

import java.util.List;
import java.util.Optional;

/** Runs an external command. Injectable so tests never start a process. */
@FunctionalInterface
public interface CommandRunner {

    /** The standard output of {@code command} when it exits with status 0; empty on any failure or timeout. */
    Optional<String> run(List<String> command);
}
