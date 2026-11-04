package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;

import java.util.Optional;

/**
 * Gives the circuit another name of its own (Propiedades ▸ «Nombre del circuito»), kept apart from its file's name:
 * one undo step. The name lives in the file; nothing on the canvas changes, so no document listener hears it.
 */
public final class RenameCircuitCommand extends BeforeAfterCommand<String> {

    private static final String DESCRIPTION = "command.renameCircuit";

    private RenameCircuitCommand(String before, String after) {
        super(before, after);
    }

    /** {@code name} without its outer spaces; empty when that is blank or the circuit's name already. */
    public static Optional<RenameCircuitCommand> renaming(CircuitDocument document, String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty() || trimmed.equals(document.name())) {
            return Optional.empty();
        }
        return Optional.of(new RenameCircuitCommand(document.name(), trimmed));
    }

    @Override
    protected void set(CircuitDocument document, String name) {
        document.setName(name);
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }
}
