package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.ComponentDefinition;

import java.util.Objects;

/**
 * Gives the document's definition {@code definitionId} a new description (the text «Editar descripción…» edits), as one
 * undo step. Only the text and the revision change: the instances keep their shape and their wires.
 */
public final class ChangeDefinitionDescriptionCommand implements Command {

    private final String definitionId;
    private final String newDescription;
    private ComponentDefinition previous;

    public ChangeDefinitionDescriptionCommand(String definitionId, String newDescription) {
        this.definitionId = Objects.requireNonNull(definitionId, "definitionId");
        this.newDescription = Objects.requireNonNull(newDescription, "newDescription");
    }

    /** @throws IllegalArgumentException when the document has no definition {@code definitionId} */
    @Override
    public void apply(CircuitDocument document) {
        previous = document.definition(definitionId).orElseThrow(() -> new IllegalArgumentException(
                "no definition " + definitionId + " in the document"));
        document.putDefinition(previous.withDescription(newDescription));
    }

    @Override
    public void revert(CircuitDocument document) {
        document.putDefinition(previous);
    }

    @Override
    public String description() {
        return "command.changeDescription";
    }
}
