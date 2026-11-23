package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.document.DocumentElement;

import java.awt.event.KeyEvent;

/**
 * A tool that draws a new element by dragging a rectangle (a frame, a sticky note): the drawing goes on over the
 * other panes of the window, Escape drops a drawing in progress (with none, it goes back to the default tool, like
 * Cable and Mover vista), and the release creates the element as one undo step, selects it and opens its text.
 */
public abstract class ElementDrawingTool implements Tool {

    /** Whether a press started a drawing that is not released yet. */
    protected abstract boolean drawing();

    /** Forgets the drawing in progress. */
    protected abstract void reset();

    /** A drawing in progress goes on over the other panes of the window. */
    @Override
    public boolean crossesPanes() {
        return drawing();
    }

    @Override
    public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
        if (keyCode != KeyEvent.VK_ESCAPE) {
            return false;
        }
        if (drawing()) {
            reset();
            context.repaint();
        } else {
            context.useDefaultTool();
        }
        return true;
    }

    @Override
    public void deactivated(ToolContext context) {
        reset();
    }

    /** Creates {@code created} with {@code creation}, selects it, goes back to the default tool and edits its text. */
    protected static void finish(ToolContext context, Command creation, DocumentElement created) {
        context.openDocument().execute(creation);
        context.selection().select(created.id());
        context.useDefaultTool();
        context.editLabel(created);
    }
}
