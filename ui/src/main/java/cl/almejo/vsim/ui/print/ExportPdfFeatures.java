package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.ui.app.FileNames;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.export.ExportIcons;
import cl.almejo.vsim.ui.export.FileExportFeatures;
import cl.almejo.vsim.ui.paint.TextFonts;
import cl.almejo.vsim.ui.window.EditorView;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;

/**
 * «Exportar a PDF…» of one editor view: the menu item right after «Exportar imagen…» (off for an empty document), the
 * shortcut registration ({@code file.exportPdf}, no default stroke) and the dialog, opened on a frozen copy of the
 * circuit with the selection of this view. {@link #dispose()} takes all of it back.
 */
public final class ExportPdfFeatures extends FileExportFeatures {

    static final String SHORTCUT_ID = "file.exportPdf";
    private static final String EXPORT_IMAGE_ID = "file.exportImage";

    private final PdfExportDialog.Collaborators collaborators;
    private final PdfExportDialog.Presenter presenter;
    private final Clock clock;

    public ExportPdfFeatures(EditorView view, PdfExportDialog.Collaborators collaborators,
                             PdfExportDialog.Presenter presenter, Clock clock) {
        // Right after «Exportar imagen…», else after «Guardar como…», else at the end.
        super(view, "action.exportPdf", ExportIcons.EXPORT_PDF, SHORTCUT_ID,
                indexAfter(view, view.shortcutActions().get(EXPORT_IMAGE_ID), view.actions().saveAs()));
        this.collaborators = Objects.requireNonNull(collaborators, "collaborators");
        this.presenter = Objects.requireNonNull(presenter, "presenter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Freezes the circuit and opens the dialog on it; the dialog does the rest. */
    @Override
    protected void export() {
        if (!available()) {
            return;
        }
        PdfExportDialog dialog = new PdfExportDialog(capture(view(), clock), () -> capture(view(), clock),
                view().services().preferences(), startDirectory(), suggestedName(view().openDocument()),
                collaborators);
        presenter.present(view(), dialog);
    }

    /**
     * The circuit of {@code view} as it is now, with its selection, scheme and font; nothing in the document
     * changes. Shared with «Imprimir…».
     */
    static PrintInput capture(EditorView view, Clock clock) {
        return PrintInput.capture(view.openDocument(), view.selection().ids(),
                view.services().appearance().scheme(), TextFonts.current(), clock,
                view.services().preferences().showBusValues());
    }

    /** The last PDF folder, else the document's folder, else the user's home. */
    Path startDirectory() {
        return startDirectory(view().services().preferences().printDirectory());
    }

    /** The document's name without «.vsim», offered for the PDF. */
    static String suggestedName(OpenDocument openDocument) {
        String name = FileNames.safeName(openDocument.displayName());
        return name.toLowerCase(Locale.ROOT).endsWith(".vsim")
                ? name.substring(0, name.length() - ".vsim".length()) : name;
    }
}
