package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.ui.app.FileNames;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.export.ExportIcons;
import cl.almejo.vsim.ui.export.FileExportFeatures;
import cl.almejo.vsim.ui.paint.TextFonts;
import cl.almejo.vsim.ui.sheets.SheetAnchors;
import cl.almejo.vsim.ui.window.EditorView;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;

/**
 * «Exportar a PDF…» of one editor view: its item in Archivo ▸ Exportar after «Imagen…» (off for an empty document), the
 * shortcut registration ({@code file.exportPdf}, no default stroke) and the dialog, opened on a frozen copy of the
 * circuit with the selection of this view. {@link #dispose()} takes all of it back.
 */
public final class ExportPdfFeatures extends FileExportFeatures {

    static final String SHORTCUT_ID = "file.exportPdf";
    private static final String EXPORT_IMAGE_ID = "file.exportImage";

    private final PdfExportDialog.Collaborators collaborators;
    private final PdfExportDialog.Presenter presenter;
    private final Clock clock;
    private final SheetAnchors sheetAnchors;

    public ExportPdfFeatures(EditorView view, PdfExportDialog.Collaborators collaborators,
                             PdfExportDialog.Presenter presenter, Clock clock, SheetAnchors sheetAnchors) {
        // Right after «Imagen…» in Archivo ▸ Exportar, else last.
        super(view, "action.exportPdf", "menu.file.export.pdf", ExportIcons.EXPORT_PDF, SHORTCUT_ID,
                view.shortcutActions().get(EXPORT_IMAGE_ID));
        this.collaborators = Objects.requireNonNull(collaborators, "collaborators");
        this.presenter = Objects.requireNonNull(presenter, "presenter");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sheetAnchors = Objects.requireNonNull(sheetAnchors, "sheetAnchors");
    }

    /** Freezes the circuit and opens the dialog on it; the dialog does the rest. */
    @Override
    protected void export() {
        if (!available()) {
            return;
        }
        PdfExportDialog dialog = new PdfExportDialog(capture(view(), clock, sheetAnchors), () -> capture(view(), clock, sheetAnchors),
                view().services().preferences(), startDirectory(), suggestedName(view().openDocument()),
                collaborators);
        presenter.present(view(), dialog);
    }

    /**
     * The circuit of {@code view} as it is now, with its selection, scheme and font; nothing in the document
     * changes, and the anchor of the sheets shown on the canvas as it is now. Shared with «Imprimir…».
     */
    static PrintInput capture(EditorView view, Clock clock, SheetAnchors sheetAnchors) {
        return PrintInput.capture(view.openDocument(), view.selection().ids(),
                view.services().appearance().scheme(), TextFonts.current(), clock,
                view.services().preferences().showBusValues(),
                sheetAnchors.anchor(view.openDocument().document()));
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
