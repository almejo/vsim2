package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.CreateMachineCommand;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.tools.ToolContext;

import java.awt.Cursor;
import java.awt.event.KeyEvent;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Armed by the palette's «Máquina de estados»: the next click on the canvas creates a machine there
 * ({@link MachineCreation#create}) and returns to the default tool (Shift keeps creating, as for any part).
 */
public final class PlaceMachineTool implements Tool {

    private final Supplier<Set<String>> libraryIds;

    /** @param libraryIds the ids of «Mis componentes», read on each click: a new machine's id is none of them */
    public PlaceMachineTool(Supplier<Set<String>> libraryIds) {
        this.libraryIds = Objects.requireNonNull(libraryIds, "libraryIds");
    }

    @Override
    public boolean placesFromPalette() {
        return true;
    }

    @Override
    public String gesturePhrase(ToolContext context) {
        return Messages.text("machine.placing");
    }

    @Override
    public void mousePressed(ToolContext context, CanvasMouseEvent event) {
        if (event.button() == CanvasMouseEvent.SECONDARY) {
            context.useDefaultTool();
            return;
        }
        if (event.button() != CanvasMouseEvent.PRIMARY) {
            return;
        }
        CreateMachineCommand command = MachineCreation.create(context.openDocument(), event.snapped(),
                libraryIds.get());
        context.selection().select(command.instanceId().orElseThrow());
        context.repaint();
        if (!event.shiftDown()) {
            context.useDefaultTool();
        }
    }

    @Override
    public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
        if (keyCode == KeyEvent.VK_ESCAPE) {
            context.useDefaultTool();
            return true;
        }
        return false;
    }

    @Override
    public Cursor cursor() {
        return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
    }
}
