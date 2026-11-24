package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.snap.AlignmentLinesPainter;
import cl.almejo.vsim.ui.snap.MatchedGuides;
import cl.almejo.vsim.ui.snap.SnappedMove;
import cl.almejo.vsim.ui.wiring.MovePreview;
import cl.almejo.vsim.ui.wiring.SelectionMoves;

import java.awt.Graphics2D;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The drag of one element a {@link SelectToolDelegate} grabbed (a frame by its title or edge, a sticky note by its
 * paper or corner), shared by those delegates: the snapped delta from the press point, and, unless the grab resizes
 * the element, the move of what the selection acts on like the select tool's: snapped (Ajustar), stopped by the walls
 * of a locked frame, with the wires following (the configured key inverts the mode), as one undo step at the release.
 * A release without moving narrows the selection to the element or takes it out (the Shift toggle). The delegate keeps
 * the element and does its own resize.
 */
public final class ElementDrag {

    /** What the press grabbed the element for: to move it (with the selection) or to resize it. */
    public enum Grab {
        MOVE,
        RESIZE
    }

    private GridPoint pressPoint;
    private boolean resizes;
    /** What the press left for a release without moving: narrow the selection to the element or take it out. */
    private final SelectionOnRelease selectionOnRelease = new SelectionOnRelease();
    /** Whether the pointer left the press point: a drag a wall stopped is still not a click. */
    private boolean pointerMoved;
    private int deltaX;
    private int deltaY;
    private boolean followWires;
    /** The last drag event, so a modifier key pressed or released without moving can redo it. */
    private CanvasMouseEvent lastDrag;
    private final MovePreview movePreview = new MovePreview();
    private SnappedMove snappedMove = SnappedMove.unsnapped(0, 0);

    /** Starts a drag at {@code event}'s snapped point, to move or to resize the element as {@code grab} says. */
    public void start(CanvasMouseEvent event, Grab grab) {
        this.pressPoint = event.snapped();
        this.resizes = grab == Grab.RESIZE;
    }

    /** Whether a drag started and has not been released or cancelled. */
    public boolean active() {
        return pressPoint != null;
    }

    /** Whether the drag started resizes the element (no snap, no move of the selection). */
    public boolean resizes() {
        return resizes;
    }

    /**
     * The Shift press on the element {@code id}: a selected element leaves the selection at a release without moving
     * (Shift+drag moves the selection with it instead), an unselected one joins it now.
     */
    public void toggleOnPress(Selection selection, ElementId id) {
        selectionOnRelease.toggleOnPress(selection, id);
    }

    /**
     * The plain press on the element {@code id}: an unselected element becomes the selection; a selected one stays
     * with the others, and a move's release without moving narrows the selection to it.
     */
    public void selectOnPress(Selection selection, ElementId id) {
        if (!resizes || !selection.contains(id)) {
            selectionOnRelease.selectOnPress(selection, id);
        }
    }

    /** Follows the pointer: the delta from the press point, snapped and stopped by locked walls when moving. */
    public void dragged(ToolContext context, CanvasMouseEvent event) {
        if (!active()) {
            return;
        }
        lastDrag = event;
        deltaX = event.snapped().x() - pressPoint.x();
        deltaY = event.snapped().y() - pressPoint.y();
        pointerMoved |= deltaX != 0 || deltaY != 0;
        if (!resizes) {
            Set<ElementId> moved = context.selection().effectiveIds();
            // an element inside a locked frame stops at its border, like any content
            snappedMove = SelectionMoves.limited(context, moved, SnappedMove.of(context, moved, false, deltaX, deltaY));
            deltaX = snappedMove.deltaX();
            deltaY = snappedMove.deltaY();
        }
        followWires = context.selectionMoves().follows(event.shiftDown(), event.altDown());
        context.repaint();
    }

    /** Redoes the drag at the last pointer position with the new keys, so the wires preview follows them. */
    public boolean modifiersChanged(ToolContext context, boolean shiftDown, boolean controlDown, boolean altDown) {
        if (!active() || lastDrag == null) {
            return false;
        }
        dragged(context, lastDrag.withModifiers(shiftDown, controlDown, altDown));
        return true;
    }

    /** Redoes a move at the last pointer position after a snap preference, the zoom or the guides changed. */
    public boolean refreshSnap(ToolContext context) {
        if (!active() || lastDrag == null || resizes) {
            return false;
        }
        dragged(context, lastDrag);
        return true;
    }

    /** The guides the move snapped to; none while resizing or with no drag. */
    public List<Guide> snappedGuides() {
        return !active() || resizes ? List.of() : MatchedGuides.of(snappedMove.matches());
    }

    /**
     * Ends a move grabbed by the element {@code id}: moves what the selection acts on, or, after a release without
     * moving, takes the element out (Shift) or narrows the selection to it.
     */
    public void releaseMove(ToolContext context, ElementId id) {
        if (deltaX != 0 || deltaY != 0) {
            context.selectionMoves().move(context.openDocument(), context.selection(),
                    context.selection().effectiveIds(), deltaX, deltaY, followWires);
        } else if (isClick()) {
            selectionOnRelease.released(context.selection(), id);
        }
    }

    /**
     * Whether the gesture so far is a click: the pointer never left the press point. A drag stopped by a locked
     * frame's wall (or brought back to the press point) moved nothing, and it is no click either.
     */
    public boolean isClick() {
        return deltaX == 0 && deltaY == 0 && !pointerMoved;
    }

    /** The wires the move's preview redraws itself, hidden from the canvas while the wires follow. */
    public Set<ElementId> hiddenWires(ToolContext context) {
        if (!active() || resizes || !followWires) {
            return Set.of();
        }
        return movePreview.hiddenWires(context, context.selection().effectiveIds(), deltaX, deltaY);
    }

    /** The elements the move carries, at their new place; none while resizing or with no drag. */
    public List<DocumentElement> movingElements(ToolContext context) {
        if (!active() || resizes) {
            return List.of();
        }
        return MovePreview.movedElements(context, context.selection().effectiveIds(), deltaX, deltaY);
    }

    /** Paints the move's ghosts, the following wires and the alignment lines. */
    public void paintMove(Graphics2D graphics, ToolContext context) {
        movePreview.paint(graphics, context, context.selection().effectiveIds(), deltaX, deltaY,
                followWires, false, Optional.ofNullable(lastDrag).map(CanvasMouseEvent::gridPosition));
        AlignmentLinesPainter.paint(graphics, context, snappedMove.movedBox(), snappedMove.matches());
    }

    /** Whether the pointer has moved the drag at least a grid unit from the press point. */
    public boolean displaced() {
        return active() && (deltaX != 0 || deltaY != 0);
    }

    /** Whether the pointer left the press point at some time during the drag. */
    public boolean pointerMoved() {
        return pointerMoved;
    }

    public int deltaX() {
        return deltaX;
    }

    public int deltaY() {
        return deltaY;
    }

    /** Forgets the drag. */
    public void reset() {
        pressPoint = null;
        resizes = false;
        selectionOnRelease.reset();
        pointerMoved = false;
        deltaX = 0;
        deltaY = 0;
        followWires = false;
        lastDrag = null;
        snappedMove = SnappedMove.unsnapped(0, 0);
        movePreview.clear();
    }
}
