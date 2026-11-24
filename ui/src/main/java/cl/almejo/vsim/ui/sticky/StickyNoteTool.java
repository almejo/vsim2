package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.ElementDrawingTool;
import cl.almejo.vsim.ui.tools.ToolContext;

import java.awt.Graphics2D;
import java.util.List;

/**
 * The «Nota adhesiva» tool (spec §3 «Create»): a click sticks a note of the default size with its top-left corner at
 * the click; a drag gives it the dragged rectangle (at least the minimum size, growing away from the start corner).
 * Both corners snap like the «Marco» tool's. The new note is yellow free text, created as one undo step; the tool
 * then returns to Selección with the note selected and its text editor open.
 */
public final class StickyNoteTool extends ElementDrawingTool {

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

    @Override
    protected boolean drawing() {
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
        finish(context, new AddElementsCommand(List.of(note)), note);
    }

    /** The note the press and the pointer make now: the default size for a click, the dragged rectangle otherwise. */
    private StickyNote noteBeingDrawn(ElementId id) {
        if (start.equals(current)) {
            return new StickyNote(id, start, StickyNote.DEFAULT_WIDTH, StickyNote.DEFAULT_HEIGHT, StickyColor.YELLOW,
                    new StickyText(""), false);
        }
        Bounds bounds = Bounds.dragged(start, current, StickyNote.MINIMUM_WIDTH, StickyNote.MINIMUM_HEIGHT);
        return new StickyNote(id, new GridPoint(bounds.x(), bounds.y()), bounds.width(), bounds.height(),
                StickyColor.YELLOW, new StickyText(""), false);
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
    protected void reset() {
        start = null;
        current = null;
    }
}
