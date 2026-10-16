package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;

import java.util.Optional;

/** A reversible edit. {@link #description()} is an i18n key such as "command.move". */
public interface Command {
    void apply(CircuitDocument document);

    void revert(CircuitDocument document);

    String description();

    /**
     * When {@code next}, executed right after this command with nothing in between, continues it, the single command
     * that does both (and is undone in one step); empty, the default, when it does not. {@link History} asks the
     * command on top of its undo stack, and only about a command executed straight after it; when merged, it reverts
     * this command and applies the merged one ({@code next} itself is never applied), so the merged command is applied
     * to the state this command started from.
     */
    default Optional<Command> mergedWith(Command next) {
        return Optional.empty();
    }
}
