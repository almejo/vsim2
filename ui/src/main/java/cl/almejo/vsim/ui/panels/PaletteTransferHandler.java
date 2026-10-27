package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.ui.clipboard.CircuitFileDrop;
import cl.almejo.vsim.ui.panels.PalettePanel.UserComponent;

import javax.swing.JComponent;
import javax.swing.TransferHandler;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.Optional;

/**
 * Drags entries to a canvas (a standard type as text, a user component as a fragment with its definitions) and
 * accepts dropped circuit and component files.
 */
final class PaletteTransferHandler extends TransferHandler {
    private final PalettePanel panel;

    PaletteTransferHandler(PalettePanel panel) {
        this.panel = panel;
    }

    @Override
    public int getSourceActions(JComponent source) {
        return COPY;
    }

    @Override
    public Transferable createTransferable(JComponent source) {
        Optional<UserComponent> user = panel.selectedUserComponent();
        if (user.isPresent()) {
            return new PaletteDragTransferable(panel.fragmentJson(user.get()), user.get().type().id());
        }
        return panel.selectedType().map(type -> new StringSelection(PaletteTransfer.encode(type.id()))).orElse(null);
    }

    @Override
    public boolean canImport(TransferSupport support) {
        return panel.fileDropHandler() != null && CircuitFileDrop.canImport(support);
    }

    @Override
    public boolean importData(TransferSupport support) {
        return panel.fileDropHandler() != null && CircuitFileDrop.importData(support, panel.fileDropHandler());
    }
}
