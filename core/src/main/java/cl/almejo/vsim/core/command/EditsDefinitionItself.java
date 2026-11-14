package cl.almejo.vsim.core.command;

import java.util.Optional;

/**
 * A command that changes a definition of the document itself instead of editing its body: a «Máquina de estados»'s
 * diagram ({@link EditMachineCommand}). Made from the definition's tab, {@link EditInsideCommand} applies it to the
 * document (not to the tab's working copy) and then brings the copy to the new body, still one undo step of the
 * document.
 */
public interface EditsDefinitionItself extends Command {

    /** The definition the command changes; it must be the one of the tab it is made from. */
    String definitionId();

    /**
     * The update of the placed pieces the last apply planned (when the definition's pins changed), so the tab can
     * report what could not follow as for any inside edit; empty when the pieces did not have to change.
     */
    default Optional<ReplaceDefinitionCommand> plannedUpdate() {
        return Optional.empty();
    }
}
