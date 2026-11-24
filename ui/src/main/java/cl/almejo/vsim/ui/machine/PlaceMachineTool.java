package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.CreateMachineCommand;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.PlacingTool;
import cl.almejo.vsim.ui.tools.ToolContext;

import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Armed by the palette's «Máquina de estados»: the next click on the canvas creates a machine there
 * ({@link MachineCreation#create}) and returns to the default tool (Shift keeps creating, as for any part).
 */
public final class PlaceMachineTool extends PlacingTool {

    private final Supplier<Set<String>> libraryIds;

    /** @param libraryIds the ids of «Mis componentes», read on each click: a new machine's id is none of them */
    public PlaceMachineTool(Supplier<Set<String>> libraryIds) {
        this.libraryIds = Objects.requireNonNull(libraryIds, "libraryIds");
    }

    @Override
    public String gesturePhrase(ToolContext context) {
        return Messages.text("machine.placing");
    }

    /** Creates a machine where {@code event} snapped and selects it. */
    @Override
    protected void place(ToolContext context, CanvasMouseEvent event) {
        CreateMachineCommand command = MachineCreation.create(context.openDocument(), event.snapped(),
                libraryIds.get());
        context.selection().select(command.instanceId().orElseThrow());
        context.repaint();
    }
}
