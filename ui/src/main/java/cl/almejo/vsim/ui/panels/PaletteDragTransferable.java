package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.ui.clipboard.FragmentTransferable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Optional;

/**
 * What a drag of a user component out of the palette carries: the fragment (like {@link FragmentTransferable}) plus
 * the id of the type it was dragged from, under a JVM-local flavor. The canvas that takes the drop reads the id to
 * count the component as used («Recientes»); a paste or a duplicate never carries it.
 */
public final class PaletteDragTransferable implements Transferable {

    /** JVM-local flavor whose data is the type id (a {@link String}); only this application's windows read it. */
    public static final DataFlavor TYPE_FLAVOR = typeFlavor();

    private final FragmentTransferable fragment;
    private final String typeId;

    public PaletteDragTransferable(String fragmentJson, String typeId) {
        this.fragment = new FragmentTransferable(fragmentJson);
        this.typeId = typeId;
    }

    /** The id of the palette type {@code transferable} was dragged from; empty when it did not come from there. */
    public static Optional<String> typeIdOf(Transferable transferable) {
        if (transferable == null || !transferable.isDataFlavorSupported(TYPE_FLAVOR)) {
            return Optional.empty();
        }
        try {
            return transferable.getTransferData(TYPE_FLAVOR) instanceof String found
                    ? Optional.of(found) : Optional.empty();
        } catch (UnsupportedFlavorException | IOException | RuntimeException exception) {
            // Some platforms refuse to hand out data before the drop; the drop itself reads it again.
            return Optional.empty();
        }
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{TYPE_FLAVOR, FragmentTransferable.FRAGMENT_FLAVOR, DataFlavor.stringFlavor};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return TYPE_FLAVOR.equals(flavor) || fragment.isDataFlavorSupported(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (TYPE_FLAVOR.equals(flavor)) {
            return typeId;
        }
        return fragment.getTransferData(flavor);
    }

    private static DataFlavor typeFlavor() {
        return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=java.lang.String",
                "VSim palette type");
    }
}
