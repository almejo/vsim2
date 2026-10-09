package cl.almejo.vsim.ui.clipboard;

import javax.swing.TransferHandler;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Drop target for a whole window: accepts circuit files only and hands them to {@code opener}. */
public final class FileDropHandler extends TransferHandler {

    private final Consumer<List<Path>> opener;

    public FileDropHandler(Consumer<List<Path>> opener) {
        this.opener = opener;
    }

    @Override
    public boolean canImport(TransferSupport support) {
        return CircuitFileDrop.canImport(support);
    }

    @Override
    public boolean importData(TransferSupport support) {
        return CircuitFileDrop.importData(support, opener);
    }
}
