package cl.almejo.vsim.ui.clipboard;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.TransferHandler;
import javax.swing.text.JTextComponent;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.Arrays;
import java.util.Objects;

/**
 * Wraps a text field's own transfer handler so it refuses circuit fragments. A fragment also travels as plain text
 * (§6.8), so without this a drag out released over an editor field, or Ctrl+V into it, would insert the fragment
 * JSON there and, through the Properties tab, into a label. Imports of anything else, and copy and cut, go to the
 * wrapped handler. Dragging text out of the field is not supported (the editor fields never enable it).
 */
public final class FragmentRefusingTransferHandler extends TransferHandler {

    private final TransferHandler delegate;

    private FragmentRefusingTransferHandler(TransferHandler delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /** Makes {@code field} refuse fragments while keeping its usual text drag, drop, cut, copy and paste. */
    public static <T extends JTextComponent> T install(T field) {
        TransferHandler current = field.getTransferHandler();
        if (current != null && !(current instanceof FragmentRefusingTransferHandler)) {
            field.setTransferHandler(new FragmentRefusingTransferHandler(current));
        }
        return field;
    }

    private static boolean carriesFragment(DataFlavor[] flavors) {
        return flavors != null && Arrays.asList(flavors).contains(FragmentTransferable.FRAGMENT_FLAVOR);
    }

    @Override
    public boolean canImport(TransferSupport support) {
        return !support.isDataFlavorSupported(FragmentTransferable.FRAGMENT_FLAVOR) && delegate.canImport(support);
    }

    @Override
    public boolean importData(TransferSupport support) {
        return !support.isDataFlavorSupported(FragmentTransferable.FRAGMENT_FLAVOR) && delegate.importData(support);
    }

    @Override
    public boolean canImport(JComponent component, DataFlavor[] flavors) {
        return !carriesFragment(flavors) && delegate.canImport(component, flavors);
    }

    @Override
    public boolean importData(JComponent component, Transferable transferable) {
        return !transferable.isDataFlavorSupported(FragmentTransferable.FRAGMENT_FLAVOR)
                && delegate.importData(component, transferable);
    }

    @Override
    public int getSourceActions(JComponent component) {
        return delegate.getSourceActions(component);
    }

    @Override
    public Icon getVisualRepresentation(Transferable transferable) {
        return delegate.getVisualRepresentation(transferable);
    }

    @Override
    public void exportToClipboard(JComponent component, Clipboard clipboard, int action) {
        delegate.exportToClipboard(component, clipboard, action);
    }
}
