package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.tools.ToolContext;

import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * The «Nota adhesiva» tool (spec §3 «Create»): a click sticks a note of the default size with its top-left corner at
 * the click; a drag gives it the dragged rectangle (at least the minimum size, growing away from the start corner).
 * Both corners snap like the «Marco» tool's. The new note is yellow free text, created as one undo step; the tool
 * then returns to Selección with the note selected and its text editor open.
 */
public final class StickyNoteTool implements Tool {

    private GridPoint start;
    private GridPoint current;

    @Override
    public void mousePressed(ToolContext context, CanvasMouseEvent event) {
        if (event.button() != CanvasMouseEvent.PRIMARY) {
            return;
        }
        start = context.snapper().snapPoint(context, event.snapped()).point();
        current = start;
        context.repaint();
    }

    @Override
    public void mouseDragged(ToolContext context, CanvasMouseEvent event) {
        if (start == null) {
            return;
        }
        current = context.snapper().snapPoint(context, event.snapped()).point();
        context.repaint();
    }

    /** A note being drawn goes on over the other panes of the window, like a frame. */
    @Override
    public boolean crossesPanes() {
        return start != null;
    }

    @Override
    public void mouseReleased(ToolContext context, CanvasMouseEvent event) {
        if (start == null) {
            return;
        }
        current = context.snapper().snapPoint(context, event.snapped()).point();
        StickyNote note = noteBeingDrawn(context.openDocument().document().newStickyNoteId());
        reset();
        context.openDocument().execute(new AddElementsCommand(List.of(note)));
        context.selection().select(note.id());
        context.useDefaultTool();
        context.editLabel(note);
    }

    /** The note the press and the pointer make now: the default size for a click, the dragged rectangle otherwise. */
    private StickyNote noteBeingDrawn(ElementId id) {
        if (start.equals(current)) {
            return new StickyNote(id, start, StickyNote.DEFAULT_WIDTH, StickyNote.DEFAULT_HEIGHT, StickyColor.YELLOW,
                    new StickyText(""), false);
        }
        int width = Math.max(StickyNote.MINIMUM_WIDTH, Math.abs(current.x() - start.x()));
        int height = Math.max(StickyNote.MINIMUM_HEIGHT, Math.abs(current.y() - start.y()));
        int left = current.x() < start.x() ? start.x() - width : start.x();
        int top = current.y() < start.y() ? start.y() - height : start.y();
        return new StickyNote(id, new GridPoint(left, top), width, height, StickyColor.YELLOW, new StickyText(""),
                false);
    }

    @Override
    public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
        if (keyCode != KeyEvent.VK_ESCAPE) {
            return false;
        }
        if (start != null) {
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

    /** The paper outline of the note the release would stick, while the button is held. */
    @Override
    public void paintOverlay(Graphics2D graphics, ToolContext context) {
        if (start == null) {
            return;
        }
        StickyNote preview = noteBeingDrawn(new ElementId("preview"));
        StickyNotePainter.paintGhost(graphics, preview, context.scheme().preview());
    }

    @Override
    public Cursor cursor() {
        return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
    }

    private void reset() {
        start = null;
        current = null;
    }
}
