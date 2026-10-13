package cl.almejo.vsim.ui.truthtable;

import cl.almejo.vsim.core.io.AtomicFiles;
import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.core.truthtable.TruthTableText;
import cl.almejo.vsim.ui.app.FileDialogs;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/** «Exportar CSV…»: the suggested file name, the save dialog and the UTF-8 file (spec §5.2). */
public final class TruthTableExport {

    public static final String EXTENSION = ".csv";

    private TruthTableExport() {
    }

    /** {@code <documento>-tabla.csv}, with characters file systems refuse replaced by {@code _}. */
    public static String suggestedName(String documentName) {
        return documentName.replaceAll("[/\\\\:*?\"<>|]", "_") + "-" + Messages.text("truthTable.fileSuffix")
                + EXTENSION;
    }

    public static Path withCsvExtension(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.toLowerCase(Locale.ROOT).endsWith(EXTENSION) ? path
                : path.resolveSibling(fileName + EXTENSION);
    }

    /** Writes {@link TruthTableText#csv} as UTF-8, atomically. */
    public static void write(Path target, TruthTable table) throws IOException {
        AtomicFiles.writeString(target, TruthTableText.csv(table));
    }

    /** The save dialog, showing CSV files; empty when the user cancels or refuses to overwrite. */
    public static Optional<Path> choose(Component parent, Path directory, String documentName) {
        return FileDialogs.chooseSave(parent, directory, suggestedName(documentName),
                new FileNameExtensionFilter(Messages.text("truthTable.csvFilter"), "csv"),
                TruthTableExport::withCsvExtension);
    }
}
