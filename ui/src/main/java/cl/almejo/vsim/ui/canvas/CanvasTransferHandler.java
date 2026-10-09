package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.ui.clipboard.CircuitFileDrop;
import cl.almejo.vsim.ui.clipboard.FragmentTransferable;

import javax.swing.JComponent;
import javax.swing.TransferHandler;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

/** Drag and drop for one canvas: circuit fragments, circuit files and palette entries come in. */
final class CanvasTransferHandler extends TransferHandler {

    private final CircuitCanvas canvas;

    CanvasTransferHandler(CircuitCanvas canvas) {
        this.canvas = canvas;
    }

    @Override
    public boolean canImport(TransferSupport support) {
        boolean known = support.isDataFlavorSupported(FragmentTransferable.FRAGMENT_FLAVOR)
                || support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                || support.isDataFlavorSupported(DataFlavor.stringFlavor);
        return known && CircuitFileDrop.acceptAsCopy(support);
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
                return canvas.dropFragment(
                        (String) transferable.getTransferData(FragmentTransferable.FRAGMENT_FLAVOR), point);
            }
            if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                return CircuitFileDrop.importData(support, canvas.fileDropHandler());
            }
            String text = (String) transferable.getTransferData(DataFlavor.stringFlavor);
            return canvas.dropComponent(text, point) || canvas.dropFragment(text, point);
        } catch (UnsupportedFlavorException | IOException exception) {
            return false;
        }
    }

    @Override
    public int getSourceActions(JComponent source) {
        return COPY;
    }

    /** The selection as a fragment; null (no drag) when nothing is selected. */
    @Override
    public Transferable createTransferable(JComponent source) {
        return canvas.selectionFragmentJson().map(FragmentTransferable::new).orElse(null);
    }

    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
        canvas.endDragOut();
    }
}
