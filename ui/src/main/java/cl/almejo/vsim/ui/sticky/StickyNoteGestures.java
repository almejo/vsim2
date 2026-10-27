package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.PaintPolicy;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.snap.AlignmentLinesPainter;
import cl.almejo.vsim.ui.snap.MatchedGuides;
import cl.almejo.vsim.ui.snap.SnappedMove;
import cl.almejo.vsim.ui.tools.CanvasCursor;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.SelectToolDelegate;
import cl.almejo.vsim.ui.tools.ToolContext;
import cl.almejo.vsim.ui.wiring.MovePreview;
import cl.almejo.vsim.ui.wiring.SelectionMoves;

import java.awt.Component;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * What the select tool does with sticky notes (spec §3). Notes lie on top of everything, so this delegate is asked
 * before the others. A press on the ✕ asks the confirmation and unsticks the note; a press on a checkbox ticks or
 * unticks its item; neither selects, moves nor starts a rubber band. Dragging the folded corner resizes the note (one
 * undo step at the release); the «–» button at the top-left corner folds the note and a single click on a folded paper
 * unfolds it (a press and release without moving; dragging it moves it instead). Anywhere else on the paper the note
 * is selected and dragging moves what the selection
 * acts on, the way {@code FrameGestures} moves a frame by its title: snapped (Ajustar), stopped by the walls of a
 * locked frame, with the wires following, as one undo step. A double click edits the text.
 * While a locked frame holds the note, the ✕, the «–», the checkboxes, the corner, the unfolding click and the double
 * click do nothing (the note
 * still moves inside the frame).
 */
public final class StickyNoteGestures implements SelectToolDelegate {

    private final Supplier<Component> parent;
    private final StickyCloseConfirmation confirmation;

    private StickyNote grabbed;
    private boolean resizing;
    private GridPoint pressPoint;
    private boolean narrowOnRelease;
    /** A Shift press on a selected note: a release without moving takes it out of the selection. */
    private boolean removeOnRelease;
    private boolean pointerMoved;
    /** A plain press on a folded paper: a release without moving unfolds it (a single click is enough). */
    private boolean unfoldOnRelease;
    /** The note a click has just unfolded: the second press of that double click is swallowed. */
    private ElementId justUnfolded;
    private int deltaX;
    private int deltaY;
    private boolean followWires;
    private CanvasMouseEvent lastDrag;
    private final MovePreview movePreview = new MovePreview();
    private SnappedMove snappedMove = SnappedMove.unsnapped(0, 0);
    /** The note whose ✕ is under the pointer, drawn at full strength. */
    private ElementId closeHovered;

    /** {@code parent} is what the confirmation dialog belongs to (the pane); tests pass a fake confirmation. */
    public StickyNoteGestures(Supplier<Component> parent, StickyCloseConfirmation confirmation) {
        this.parent = Objects.requireNonNull(parent, "parent");
        this.confirmation = Objects.requireNonNull(confirmation, "confirmation");
    }

    private static Optional<StickyNoteHit> hit(ToolContext context, CanvasMouseEvent event) {
        return StickyNoteHitTester.hit(context.openDocument().document(), event.gridPosition(),
                context.viewport().pixelsPerUnit());
    }

    @Override
    public Press pressed(ToolContext context, CanvasMouseEvent event) {
        Optional<StickyNoteHit> hit = hit(context, event);
        if (hit.isEmpty()) {
            return Press.IGNORED;
        }
        CircuitDocument document = context.openDocument().document();
        StickyNote note = document.stickyNote(hit.get().note()).orElseThrow();
        Selection selection = context.selection();
        if (event.button() == CanvasMouseEvent.SECONDARY) {
            if (!selection.contains(note.id())) {
                selection.select(note.id());
            }
            context.showContextMenu(Optional.of(note), event.screenPosition());
            return Press.HANDLED;
        }
        if (event.button() != CanvasMouseEvent.PRIMARY) {
            return Press.IGNORED;
        }
        ElementId unfoldedByTheFirstClick = justUnfolded;
        justUnfolded = null;
        if (event.clickCount() >= 2 && note.id().equals(unfoldedByTheFirstClick)) {
            return Press.HANDLED;
        }
        boolean locked = StickyNoteLock.isLocked(context.openDocument(), note.id());
        StickyNoteEdits edits = new StickyNoteEdits(context.openDocument());
        return switch (hit.get().part()) {
            case CLOSE -> {
                // refused inside a locked frame: StickyNoteEdits does nothing with a locked note
                edits.close(note.id(), confirmation, parent.get());
                yield Press.HANDLED;
            }
            case FOLD -> {
                edits.setFolded(note.id(), true);
                yield Press.HANDLED;
            }
            case CHECKBOX -> {
                edits.toggle(note.id(), hit.get().itemIndex());
                yield Press.HANDLED;
            }
            case RESIZE, BODY -> pressPaper(context, event, note,
                    hit.get().part() == StickyPart.RESIZE && !locked, locked);
        };
    }

    /** A press on the paper or its corner: selection keys, a double click, or the grab of a move or a resize. */
    private Press pressPaper(ToolContext context, CanvasMouseEvent event, StickyNote note, boolean resize,
                             boolean locked) {
        Selection selection = context.selection();
        if (event.shiftDown() && !event.controlDown()) {
            // Shift+click toggles the note; Shift+drag moves the selection with it (the key inverts the mode)
            if (selection.contains(note.id())) {
                removeOnRelease = true;
            } else {
                selection.add(note.id());
            }
            grab(note, false, event);
            return Press.GRABBED;
        }
        if (event.controlDown()) {
            selection.toggle(note.id());
            return Press.HANDLED;
        }
        if (event.clickCount() >= 2) {
            // handled without a grab: the editor opens on this press, so no release is awaited
            selection.select(note.id());
            if (note.folded()) {
                new StickyNoteEdits(context.openDocument()).setFolded(note.id(), false);
            } else if (!locked) {
                context.editLabel(note);
            }
            return Press.HANDLED;
        }
        grab(note, resize, event);
        unfoldOnRelease = note.folded();
        if (!selection.contains(note.id())) {
            selection.select(note.id());
        } else if (!resize) {
            narrowOnRelease = selection.size() > 1;
        }
        return Press.GRABBED;
    }

    private void grab(StickyNote note, boolean resize, CanvasMouseEvent event) {
        grabbed = note;
        resizing = resize;
        pressPoint = event.snapped();
    }

    @Override
    public void dragged(ToolContext context, CanvasMouseEvent event) {
        if (grabbed == null) {
            return;
        }
        lastDrag = event;
        deltaX = event.snapped().x() - pressPoint.x();
        deltaY = event.snapped().y() - pressPoint.y();
        pointerMoved |= deltaX != 0 || deltaY != 0;
        if (!resizing) {
            Set<ElementId> moved = context.selection().effectiveIds();
            snappedMove = SnappedMove.of(context, moved, false, deltaX, deltaY);
            // a note inside a locked frame stops at its border, like any content
            GridPoint limited = SelectionMoves.limited(context.openDocument(), moved, snappedMove.deltaX(),
                    snappedMove.deltaY());
            snappedMove = snappedMove.clampedTo(context, moved, false, limited.x(), limited.y());
            deltaX = limited.x();
            deltaY = limited.y();
        }
        followWires = context.selectionMoves().follows(event.shiftDown(), event.altDown());
        context.repaint();
    }

    @Override
    public boolean modifiersChanged(ToolContext context, boolean shiftDown, boolean controlDown, boolean altDown) {
        if (grabbed == null || lastDrag == null) {
            return false;
        }
        dragged(context, lastDrag.withModifiers(shiftDown, controlDown, altDown));
        return true;
    }

    @Override
    public boolean refreshSnap(ToolContext context) {
        if (grabbed == null || lastDrag == null || resizing) {
            return false;
        }
        dragged(context, lastDrag);
        return true;
    }

    @Override
    public List<Guide> snappedGuides() {
        return grabbed == null || resizing ? List.of() : MatchedGuides.of(snappedMove.matches());
    }

    @Override
    public void released(ToolContext context, CanvasMouseEvent event) {
        if (grabbed == null) {
            return;
        }
        dragged(context, event);
        if (unfoldOnRelease && !pointerMoved) {
            ElementId unfolded = grabbed.id();
            new StickyNoteEdits(context.openDocument()).setFolded(unfolded, false);
            if (context.openDocument().document().stickyNote(unfolded).filter(note -> !note.folded()).isPresent()) {
                justUnfolded = unfolded;
            }
        }
        if (resizing) {
            ElementId id = grabbed.id();
            int width = resizedWidth();
            int height = resizedHeight();
            new StickyNoteEdits(context.openDocument()).change(id, note -> note.withSize(width, height));
        } else if (deltaX != 0 || deltaY != 0) {
            context.selectionMoves().move(context.openDocument(), context.selection(),
                    context.selection().effectiveIds(), deltaX, deltaY, followWires);
        } else if (pointerMoved) {
            // a drag stopped by a locked frame's wall moved nothing, and it was no click either
        } else if (removeOnRelease) {
            context.selection().remove(grabbed.id());
        } else if (narrowOnRelease) {
            context.selection().select(grabbed.id());
        }
        reset();
        context.repaint();
    }

    private int resizedWidth() {
        return Math.max(StickyNote.MINIMUM_WIDTH, grabbed.width() + deltaX);
    }

    private int resizedHeight() {
        return Math.max(StickyNote.MINIMUM_HEIGHT, grabbed.height() + deltaY);
    }

    @Override
    public Set<ElementId> hiddenWires(ToolContext context) {
        if (grabbed == null || resizing || !followWires) {
            return Set.of();
        }
        return movePreview.hiddenWires(context, context.selection().effectiveIds(), deltaX, deltaY);
    }

    @Override
    public void cancelled(ToolContext context) {
        reset();
        context.repaint();
    }

    @Override
    public CanvasCursor pressCursor() {
        return resizing ? CanvasCursor.RESIZE_SOUTH_EAST : CanvasCursor.MOVE;
    }

    /** The cursor over a note: a hand on its controls, the resize arrow on its corner, the move cross elsewhere. */
    @Override
    public Optional<CanvasCursor> moved(ToolContext context, CanvasMouseEvent event) {
        Optional<StickyNoteHit> hit = hit(context, event);
        ElementId hoveredClose = hit.filter(found -> found.part() == StickyPart.CLOSE)
                .map(StickyNoteHit::note).orElse(null);
        if (!Objects.equals(hoveredClose, closeHovered)) {
            closeHovered = hoveredClose;
            context.repaint();
        }
        return hit.map(found -> cursorOver(context, found));
    }

    private static CanvasCursor cursorOver(ToolContext context, StickyNoteHit hit) {
        boolean locked = StickyNoteLock.isLocked(context.openDocument(), hit.note());
        return switch (hit.part()) {
            case CLOSE, FOLD, CHECKBOX -> locked ? CanvasCursor.DEFAULT : CanvasCursor.HAND;
            case RESIZE -> locked ? CanvasCursor.MOVE : CanvasCursor.RESIZE_SOUTH_EAST;
            case BODY -> CanvasCursor.MOVE;
        };
    }

    @Override
    public void exited(ToolContext context) {
        if (closeHovered != null) {
            closeHovered = null;
            context.repaint();
        }
    }

    @Override
    public void paintOverlay(Graphics2D graphics, ToolContext context) {
        if (grabbed != null && (deltaX != 0 || deltaY != 0)) {
            if (resizing) {
                context.paintGhost(graphics, grabbed.withSize(resizedWidth(), resizedHeight()));
            } else {
                movePreview.paint(graphics, context, context.selection().effectiveIds(), deltaX, deltaY,
                        followWires, false, Optional.ofNullable(lastDrag).map(CanvasMouseEvent::gridPosition));
                AlignmentLinesPainter.paint(graphics, context, snappedMove.movedBox(), snappedMove.matches());
            }
        }
        if (closeHovered != null && grabbed == null) {
            context.openDocument().document().stickyNote(closeHovered)
                    .ifPresent(note -> paintHoveredClose(graphics, context, note));
        }
    }

    /**
     * The hovered note painted again inside its ✕ only, with the ✕ at full strength: the renderer draws every ✕ at
     * rest, and the clip keeps the notes lying over the rest of this one untouched.
     */
    private static void paintHoveredClose(Graphics2D graphics, ToolContext context, StickyNote note) {
        double pixelsPerGrid = context.viewport().pixelsPerUnit();
        Graphics2D clipped = (Graphics2D) graphics.create();
        try {
            clipped.clip(StickyNoteLayout.of(note, pixelsPerGrid).closeBox());
            StickyNotePainter.paint(clipped, note, context.scheme(), pixelsPerGrid, PaintPolicy.CANVAS, true);
        } finally {
            clipped.dispose();
        }
    }

    private void reset() {
        grabbed = null;
        resizing = false;
        pressPoint = null;
        narrowOnRelease = false;
        removeOnRelease = false;
        pointerMoved = false;
        unfoldOnRelease = false;
        deltaX = 0;
        deltaY = 0;
        followWires = false;
        lastDrag = null;
        snappedMove = SnappedMove.unsnapped(0, 0);
        movePreview.clear();
    }
}
