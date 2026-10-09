package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.ui.clipboard.FragmentTransferable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Optional;

/**
 * What a drag out of a canvas carries: the fragment (VSim flavor and plain text, like {@link FragmentTransferable})
 * plus its {@link DragOrigin} under a JVM-local flavor that only windows of this application can read.
 */
final class CanvasDragTransferable implements Transferable {

    static final DataFlavor ORIGIN_FLAVOR = originFlavor();

    private final FragmentTransferable fragment;
    private final DragOrigin origin;

    CanvasDragTransferable(String fragmentJson, DragOrigin origin) {
        this.fragment = new FragmentTransferable(fragmentJson);
        this.origin = origin;
    }

    /** The origin carried by {@code transferable}; empty when it has none or it cannot be read yet. */
    static Optional<DragOrigin> originOf(Transferable transferable) {
        if (transferable == null || !transferable.isDataFlavorSupported(ORIGIN_FLAVOR)) {
            return Optional.empty();
        }
        try {
            return transferable.getTransferData(ORIGIN_FLAVOR) instanceof DragOrigin found
                    ? Optional.of(found) : Optional.empty();
        } catch (UnsupportedFlavorException | IOException | RuntimeException exception) {
            // Some platforms refuse to hand out data before the drop; the drop itself reads it again.
            return Optional.empty();
        }
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{ORIGIN_FLAVOR, FragmentTransferable.FRAGMENT_FLAVOR, DataFlavor.stringFlavor};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return ORIGIN_FLAVOR.equals(flavor) || fragment.isDataFlavorSupported(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (ORIGIN_FLAVOR.equals(flavor)) {
            return origin;
        }
        return fragment.getTransferData(flavor);
    }

    private static DataFlavor originFlavor() {
        try {
            return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + DragOrigin.class.getName(),
                    "VSim drag origin", DragOrigin.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("DragOrigin must be loadable", exception);
        }
    }
}
