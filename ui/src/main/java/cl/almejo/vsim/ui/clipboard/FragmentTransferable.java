package cl.almejo.vsim.ui.clipboard;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.Objects;

/**
 * A circuit fragment ({@code FragmentFormat} JSON) offered under VSim's own flavor and as plain text, so it can be
 * pasted into another VSim window and also read in a text editor. Used by the clipboard and by drag and drop.
 */
public final class FragmentTransferable implements Transferable {

    /** VSim's own flavor; its representation class is {@link String} (the JSON). */
    public static final DataFlavor FRAGMENT_FLAVOR =
            new DataFlavor("application/x-vsim2-fragment; class=java.lang.String", null);

    private final String fragmentJson;

    public FragmentTransferable(String fragmentJson) {
        this.fragmentJson = Objects.requireNonNull(fragmentJson, "fragmentJson");
    }

    public String fragmentJson() {
        return fragmentJson;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{FRAGMENT_FLAVOR, DataFlavor.stringFlavor};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return FRAGMENT_FLAVOR.equals(flavor) || DataFlavor.stringFlavor.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }
        return fragmentJson;
    }
}
