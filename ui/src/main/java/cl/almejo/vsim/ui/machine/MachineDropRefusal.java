package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.PaletteTransfer;

import javax.swing.TransferHandler;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * A palette entry dragged onto a machine's diagram: nothing is created (the diagram holds states and arrows, not
 * parts, and a machine inside a machine is not a thing), and the status line says why instead of a silent refusal.
 */
final class MachineDropRefusal extends TransferHandler {

    private final Consumer<String> notice;

    MachineDropRefusal(Consumer<String> notice) {
        this.notice = notice;
    }

    @Override
    public boolean canImport(TransferSupport support) {
        return support.isDataFlavorSupported(DataFlavor.stringFlavor);
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) {
            return false;
        }
        try {
            String text = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
            if (PaletteTransfer.decode(text).isPresent()) {
                notice.accept(Messages.text("machine.dropRefused"));
            }
        } catch (UnsupportedFlavorException | IOException exception) {
            return false;
        }
        return false;
    }
}
