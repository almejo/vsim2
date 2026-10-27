package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.wiring.NewContact;
import cl.almejo.vsim.ui.snap.ContactDots;
import cl.almejo.vsim.ui.snap.SnappedMove;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.wiring.MovePreview;

import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Window;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Optional;
import java.util.TooManyListenersException;
import java.util.function.Predicate;

/** A move of a canvas's selection dragged out of it (to another pane or window), and its preview when it comes back. */
final class CanvasDragOut {

    private final CircuitCanvas canvas;
    private DragOrigin dragOut;
    private String dragOutJson;
    private GridPoint dragOutPreview;
    /** The contacts of the move a drag out brought back previews, and where the pointer is (grid units). */
    private final MovePreview dragOutMoves = new MovePreview();
    private Point2D dragOutPointer;
    private Predicate<Point> overAnotherWindow = this::isOverAnotherWindow;

    CanvasDragOut(CircuitCanvas canvas) {
        this.canvas = canvas;
    }

    /**
     * Switches a move of the selection in progress into a drag out of the canvas; false, leaving the move alone, when
     * nothing is moving or the selection gives no fragment to transfer.
     */
    boolean beginDragOut() {
        if (!(canvas.tool() instanceof SelectTool select) || canvas.selection().isEmpty()) {
            return false;
        }
        Optional<GridPoint> pressed = select.movingFrom();
        if (pressed.isEmpty()) {
            return false;
        }
        Optional<String> fragmentJson = canvas.selectionFragmentJson();
        Optional<GridPoint> topLeft = SelectionBounds.topLeft(canvas.openDocument(),
                canvas.selection().effectiveIds());
        if (fragmentJson.isEmpty() || topLeft.isEmpty()) {
            return false;
        }
        select.cancelMove(canvas);
        GridPoint pressPoint = pressed.get();
        dragOut = new DragOrigin(canvas, canvas.selection().effectiveIds(), pressPoint,
                new GridPoint(pressPoint.x() - topLeft.get().x(), pressPoint.y() - topLeft.get().y()));
        dragOutJson = fragmentJson.get();
        return true;
    }

    void endDragOut() {
        dragOut = null;
        dragOutJson = null;
        dragOutLeft();
    }

    Optional<DragOrigin> dragOrigin() {
        return Optional.ofNullable(dragOut);
    }

    /** What a drag out carries: the fragment taken when it began, or the current selection otherwise. */
    Optional<String> transferFragmentJson() {
        return dragOutJson != null ? Optional.of(dragOutJson) : canvas.selectionFragmentJson();
    }

    boolean isDraggingOut() {
        return dragOut != null;
    }

    /** The drag out is over this canvas again at {@code point} (canvas pixels): preview the move it would make. */
    void dragOutOver(Point point) {
        if (dragOut == null) {
            return;
        }
        GridPoint at = canvas.viewport().snap(point.x, point.y);
        dragOutPointer = canvas.viewport().screenToGrid(point.x, point.y);
        SnappedMove snapped = SnappedMove.of(canvas, dragOut.ids(), false, at.x() - dragOut.pressPoint().x(),
                at.y() - dragOut.pressPoint().y());
        GridPoint delta = new GridPoint(snapped.deltaX(), snapped.deltaY());
        if (!delta.equals(dragOutPreview)) {
            dragOutPreview = delta;
            canvas.repaint();
        }
    }

    /** The drag out left this canvas (or ended): no preview. */
    void dragOutLeft() {
        dragOutMoves.clear();
        dragOutPointer = null;
        if (dragOutPreview != null) {
            dragOutPreview = null;
            canvas.repaint();
        }
    }

    /**
     * The new contacts of the move being previewed (spec §3.7), with the wires following as the drop will move them;
     * none when no move is previewed.
     */
    List<NewContact> dragOutContacts() {
        if (dragOut == null || dragOutPreview == null) {
            return List.of();
        }
        return dragOutMoves.contacts(canvas, dragOut.ids(), dragOutPreview.x(), dragOutPreview.y(),
                canvas.selectionMoves().followMode(), false);
    }

    /** The move being previewed, as a delta in grid units. */
    Optional<GridPoint> dragOutPreview() {
        return Optional.ofNullable(dragOutPreview);
    }

    /** True when {@code point} (canvas coordinates) is over another window: see {@link #isOverAnotherWindow}. */
    boolean overAnotherWindow(Point point) {
        return overAnotherWindow.test(point);
    }

    /** Replaces the "this point (canvas coordinates) is over another window" check, for headless tests. */
    void overrideWindowProbe(Predicate<Point> check) {
        overAnotherWindow = check;
    }

    /**
     * The point is over a showing window of this application that is neither this canvas's window nor owned by it
     * (popups, tooltips). Windows of other programs are invisible here, so dragging over them keeps the move. A VSim
     * window overlapping this canvas's own frame is not detected there: AWT gives no z-order between windows.
     */
    private boolean isOverAnotherWindow(Point canvasPoint) {
        if (!canvas.isShowing()) {
            return false;
        }
        Point screenPoint = new Point(canvasPoint);
        SwingUtilities.convertPointToScreen(screenPoint, canvas);
        Window own = SwingUtilities.getWindowAncestor(canvas);
        if (own == null || own.getBounds().contains(screenPoint)) {
            return false;
        }
        for (Window window : Window.getWindows()) {
            if (window.isShowing() && !isOwnedBy(window, own) && window.getBounds().contains(screenPoint)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOwnedBy(Window window, Window own) {
        for (Window current = window; current != null; current = current.getOwner()) {
            if (current == own) {
                return true;
            }
        }
        return false;
    }

    void paintDragOutPreview(Graphics2D graphics) {
        if (dragOut == null || dragOutPreview == null || dragOutPreview.equals(GridPoint.ORIGIN)) {
            return;
        }
        for (ElementId id : dragOut.ids()) {
            canvas.openDocument().document().element(id).ifPresent(element ->
                    canvas.paintGhost(graphics, element.translate(dragOutPreview.x(), dragOutPreview.y())));
        }
        ContactDots.paint(graphics, canvas, dragOutContacts(), Optional.ofNullable(dragOutPointer));
    }

    /** Hides the preview when the drag leaves the canvas; Swing's drop target takes extra listeners. */
    void listenForDragExit() {
        DropTarget dropTarget = canvas.getDropTarget();
        if (dropTarget == null) {
            return; // headless: there is no drop target and no drag ever enters
        }
        try {
            dropTarget.addDropTargetListener(new DropTargetAdapter() {
                @Override
                public void dragExit(DropTargetEvent event) {
                    dragOutLeft();
                }

                @Override
                public void drop(DropTargetDropEvent event) {
                    // The transfer handler takes the drop.
                }
            });
        } catch (TooManyListenersException exception) {
            // A plain DropTarget takes one listener only; the preview then stays until the drag ends.
        }
    }
}
