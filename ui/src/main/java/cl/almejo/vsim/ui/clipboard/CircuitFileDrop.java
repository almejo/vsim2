package cl.almejo.vsim.ui.clipboard;

import cl.almejo.vsim.ui.app.FileNames;

import javax.swing.TransferHandler;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Circuit files ({@code .vsim}, {@code .json}) dropped from a file manager. */
public final class CircuitFileDrop {

    private CircuitFileDrop() {
    }

    public static boolean canImport(TransferHandler.TransferSupport support) {
        return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor) && acceptAsCopy(support);
    }

    /** Hands the circuit files of the drop to {@code opener}; false (and no call) when it carries none. */
    public static boolean importData(TransferHandler.TransferSupport support, Consumer<List<Path>> opener) {
        return importData(support, CircuitFileDrop::isCircuitFile, opener);
    }

    /** Hands the dropped files {@code accepted} keeps to {@code receiver}; false (and no call) when none are left. */
    public static boolean importData(TransferHandler.TransferSupport support, Predicate<Path> accepted,
                                     Consumer<List<Path>> receiver) {
        if (!canImport(support)) {
            return false;
        }
        List<Path> files = files(support.getTransferable(), accepted);
        if (files.isEmpty()) {
            return false;
        }
        receiver.accept(files);
        return true;
    }

    public static List<Path> circuitFiles(Transferable transferable) {
        return files(transferable, CircuitFileDrop::isCircuitFile);
    }

    /** The files of a file list that {@code accepted} keeps; empty when the transferable is not a file list. */
    public static List<Path> files(Transferable transferable, Predicate<Path> accepted) {
        if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            return List.of();
        }
        try {
            List<?> files = (List<?>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
            return files.stream()
                    .filter(File.class::isInstance)
                    .map(file -> ((File) file).toPath())
                    .filter(accepted)
                    .toList();
        } catch (UnsupportedFlavorException | IOException exception) {
            return List.of();
        }
    }

    /**
     * A drop is always taken as a copy, so a file manager never deletes the dragged file (it would for MOVE).
     * Not a drop (paste): always acceptable.
     */
    public static boolean acceptAsCopy(TransferHandler.TransferSupport support) {
        if (!support.isDrop()) {
            return true;
        }
        if ((support.getSourceDropActions() & TransferHandler.COPY) == 0) {
            return false;
        }
        support.setDropAction(TransferHandler.COPY);
        return true;
    }

    private static boolean isCircuitFile(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }
        String name = fileName.toString().toLowerCase(Locale.ROOT);
        return name.endsWith(FileNames.EXTENSION) || name.endsWith(".json");
    }
}
