package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.memory.MemoryContents;

import java.util.HashMap;
import java.util.Map;

/**
 * «Editar contenido»: the content editor's Aceptar, one undo step. It is a {@link ChangeParametersCommand} that only
 * replaces the hidden {@code contents} parameter of the memory as it is when the command runs, named for the undo menu.
 */
public final class EditMemoryContentsCommand implements Command {

    public static final String DESCRIPTION = "command.editMemory";

    private final ChangeParametersCommand change;

    public EditMemoryContentsCommand(Component component, ComponentType type, MemoryContents contents) {
        Map<String, Object> parameters = new HashMap<>(component.parameters());
        parameters.put(MemoryTypes.CONTENTS, contents.encode());
        this.change = new ChangeParametersCommand(component.id(), type, parameters);
    }

    @Override
    public void apply(CircuitDocument document) {
        change.apply(document);
    }

    @Override
    public void revert(CircuitDocument document) {
        change.revert(document);
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }
}
