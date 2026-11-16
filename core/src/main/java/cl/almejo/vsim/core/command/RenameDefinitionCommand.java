package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.ComponentDefinition;

import java.util.Objects;

/**
 * Gives the document's definition {@code definitionId} a new name (a machine's «Nombre» in Propiedades), as one undo
 * step. The caller checks the name first ({@code DefinitionNames}). Only the name and the revision change: the
 * instances keep their shape and their wires, and everything that shows the name reads it from the definition.
 */
public final class RenameDefinitionCommand implements Command {

    private final String definitionId;
    private final String newName;
    private ComponentDefinition previous;

    public RenameDefinitionCommand(String definitionId, String newName) {
        this.definitionId = Objects.requireNonNull(definitionId, "definitionId");
        this.newName = Objects.requireNonNull(newName, "newName");
    }

    /** @throws IllegalArgumentException when the document has no definition {@code definitionId} */
    @Override
    public void apply(CircuitDocument document) {
        previous = document.definition(definitionId).orElseThrow(() -> new IllegalArgumentException(
                "no definition " + definitionId + " in the document"));
        document.putDefinition(previous.withName(newName.strip()));
    }

    @Override
    public void revert(CircuitDocument document) {
        document.putDefinition(previous);
    }

    @Override
    public String description() {
        return "command.renameComponent";
    }
}
