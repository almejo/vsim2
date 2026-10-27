package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.ui.clipboard.CircuitFileDrop;
import cl.almejo.vsim.ui.clipboard.FragmentTransferable;
import cl.almejo.vsim.ui.panels.PaletteDragTransferable;

import javax.swing.JComponent;
import javax.swing.TransferHandler;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Optional;

/**
 * Drag and drop for one canvas: circuit fragments (moved within a document, copied otherwise or with Ctrl), circuit
 * files and palette entries come in; a drag out carries the selection and its {@link DragOrigin}.
 */
final class CanvasTransferHandler extends TransferHandler {

    private final CircuitCanvas canvas;

    CanvasTransferHandler(CircuitCanvas canvas) {
        this.canvas = canvas;
    }

    @Override
    public boolean canImport(TransferSupport support) {
        if (support.isDataFlavorSupported(FragmentTransferable.FRAGMENT_FLAVOR)) {
            return !support.isDrop() || acceptFragmentDrop(support);
        }
        boolean known = support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                || support.isDataFlavorSupported(DataFlavor.stringFlavor);
        return known && CircuitFileDrop.acceptAsCopy(support);
    }

    /**
     * A fragment from a view of this document moves unless the user asks for a copy (Ctrl); anything else is a copy.
     * While our own drag is over us and would move, the canvas previews the move.
     */
    private boolean acceptFragmentDrop(TransferSupport support) {
        boolean ownDrag = canvas.isDraggingOut();
        boolean sameDocument = ownDrag || CanvasDragTransferable.originOf(support.getTransferable())
                .map(origin -> origin.canvas().openDocument() == canvas.openDocument())
                .orElse(false);
        int action = sameDocument && !copyRequested(support.getUserDropAction(), support.getSourceDropActions())
                ? MOVE : COPY;
        if ((support.getSourceDropActions() & action) == 0) {
            return false;
        }
        support.setDropAction(action);
        if (ownDrag && action == MOVE) {
            canvas.dragOutOver(support.getDropLocation().getDropPoint());
        } else {
            canvas.dragOutLeft();
        }
        return true;
    }

    /**
     * Whether a drop asks for a copy (Ctrl held): the user action is COPY and the source also offered MOVE. AWT
     * reports COPY without any modifier when the source offers only COPY (the palette), which is no request.
     */
    static boolean copyRequested(int userDropAction, int sourceDropActions) {
        return userDropAction == COPY && (sourceDropActions & MOVE) != 0;
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) {
            return false;
        }
        Point point = support.isDrop() ? support.getDropLocation().getDropPoint()
                : new Point(canvas.getWidth() / 2, canvas.getHeight() / 2);
        Transferable transferable = support.getTransferable();
        try {
            if (support.isDataFlavorSupported(FragmentTransferable.FRAGMENT_FLAVOR)) {
                return importFragment(transferable, point, support.isDrop()
                        && copyRequested(support.getUserDropAction(), support.getSourceDropActions()));
            }
            if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                return CircuitFileDrop.importData(support, canvas.fileDropHandler(point));
            }
            String text = (String) transferable.getTransferData(DataFlavor.stringFlavor);
            return canvas.dropComponent(text, point) || canvas.dropFragment(text, point);
        } catch (UnsupportedFlavorException | IOException exception) {
            return false;
        }
    }

    /** MOVE by default (another view of the same document moves); Ctrl asks for COPY. The source never deletes. */
    @Override
    public int getSourceActions(JComponent source) {
        return COPY_OR_MOVE;
    }

    /**
     * Drops the fragment {@code transferable} carries at {@code point} (canvas pixels), with its drag origin if any;
     * {@code copyRequested} is Ctrl held at the drop.
     */
    boolean importFragment(Transferable transferable, Point point, boolean copyRequested) {
        try {
            String fragmentJson = (String) transferable.getTransferData(FragmentTransferable.FRAGMENT_FLAVOR);
            return canvas.dropFragment(fragmentJson, CanvasDragTransferable.originOf(transferable), point,
                    copyRequested, PaletteDragTransferable.typeIdOf(transferable));
        } catch (UnsupportedFlavorException | IOException exception) {
            return false;
        }
    }

    /** The selection as a fragment, with its origin while a drag out is in progress; null (no drag) when empty. */
    @Override
    public Transferable createTransferable(JComponent source) {
        Optional<String> fragmentJson = canvas.transferFragmentJson();
        if (fragmentJson.isEmpty()) {
            return null;
        }
        return canvas.dragOrigin()
                .<Transferable>map(origin -> new CanvasDragTransferable(fragmentJson.get(), origin))
                .orElseGet(() -> new FragmentTransferable(fragmentJson.get()));
    }

    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
        canvas.endDragOut();
    }
}
