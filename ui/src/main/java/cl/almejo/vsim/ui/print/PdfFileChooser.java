package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.ui.app.FileDialogs;
import cl.almejo.vsim.ui.app.FileNames;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Asks where to save the PDF: starts in {@code directory} with {@code suggestedName}, adds «.pdf» when missing and asks
 * before replacing a file; empty when cancelled. Tests replace it, they never show a dialog.
 */
@FunctionalInterface
public interface PdfFileChooser {

    String EXTENSION = ".pdf";

    Optional<Path> choose(Component parent, Path directory, String suggestedName);

    /** The real save dialog, with a PDF filter and the overwrite confirmation of the other save dialogs. */
    static PdfFileChooser swing() {
        return (parent, directory, suggestedName) -> FileDialogs.chooseSave(parent, directory,
                withPdfExtension(Path.of(suggestedName)).toString(),
                new FileNameExtensionFilter(Messages.text("print.filter.pdf"), "pdf"), PdfFileChooser::withPdfExtension);
    }

    /** {@code path}, with «.pdf» added unless it already ends in it (any case). */
    static Path withPdfExtension(Path path) {
        return FileNames.withExtension(path, EXTENSION);
    }
}
