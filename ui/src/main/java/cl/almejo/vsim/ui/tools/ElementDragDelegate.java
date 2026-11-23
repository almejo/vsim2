package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Guide;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A select-tool delegate whose gestures drag one grabbed element (a frame, a sticky note) with an {@link ElementDrag}:
 * the drag answers the delegate's questions about the gesture in progress; the subclass presses, releases, resizes
 * and paints.
 */
public abstract class ElementDragDelegate implements SelectToolDelegate {

    /** The drag of the element the last press grabbed. */
    protected final ElementDrag drag = new ElementDrag();

    /** Forgets the gesture: the grabbed element and the drag. */
    protected abstract void reset();

    /**
     * The secondary press on {@code element}: it becomes the selection unless it is in it already, and its context
     * menu opens at the pointer.
     */
    protected static Press showMenu(ToolContext context, CanvasMouseEvent event, DocumentElement element) {
        if (!context.selection().contains(element.id())) {
            context.selection().select(element.id());
        }
        context.showContextMenu(Optional.of(element), event.screenPosition());
        return Press.HANDLED;
    }

    @Override
    public void dragged(ToolContext context, CanvasMouseEvent event) {
        drag.dragged(context, event);
    }

    /** Redoes the drag at the last pointer position with the new keys, so the wires preview follows them. */
    @Override
    public boolean modifiersChanged(ToolContext context, boolean shiftDown, boolean controlDown, boolean altDown) {
        return drag.modifiersChanged(context, shiftDown, controlDown, altDown);
    }

    /** Redoes a move at the last pointer position after a snap preference, the zoom or the guides changed. */
    @Override
    public boolean refreshSnap(ToolContext context) {
        return drag.refreshSnap(context);
    }

    @Override
    public List<Guide> snappedGuides() {
        return drag.snappedGuides();
    }

    @Override
    public Set<ElementId> hiddenWires(ToolContext context) {
        return drag.hiddenWires(context);
    }

    @Override
    public List<DocumentElement> movingElements(ToolContext context) {
        return drag.movingElements(context);
    }

    @Override
    public void cancelled(ToolContext context) {
        reset();
        context.repaint();
    }
}
