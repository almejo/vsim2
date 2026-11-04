package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.ui.export.ExportIcons;
import cl.almejo.vsim.ui.export.FileExportFeatures;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.sheets.SheetAnchors;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.Menus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.Action;
import javax.swing.event.MenuListener;
import java.time.Clock;
import java.util.Objects;

/**
 * «Imprimir…» of one editor view (spec 2026-10-19 §4): the menu item right after «Exportar a PDF…», the shortcut
 * ({@code file.print}, Ctrl/⌘+P) and the dialog in its {@link PdfExportDialog.Mode#PRINT} mode, on a frozen copy of
 * the circuit like the PDF's.
 *
 * <p>The item is off for an empty document and, with the tooltip «No hay impresoras instaladas», when the system has
 * no printer. The printers are looked up off the EDT when the view installs and each time «Archivo» opens; until the
 * first answer the item stays on (the dialog looks again), and an answer older than the last question is ignored.
 * {@link #dispose()} takes all of it back.
 */
public final class PrintFeatures extends FileExportFeatures {

    static final String SHORTCUT_ID = "file.print";
    private static final Logger LOGGER = LoggerFactory.getLogger(PrintFeatures.class);

    private final PdfExportDialog.Collaborators collaborators;
    private final PdfExportDialog.Presenter presenter;
    private final Clock clock;
    private final SheetAnchors sheetAnchors;
    private final MenuListener fileMenuListener;
    /**
     * Set once a lookup answered that no printer is installed. Left without an initializer on purpose: the super
     * constructor asks {@link #ready()} before this class's fields are set, and false (printers assumed) is right then.
     */
    private boolean noPrinters;
    /** Counts the lookups; only the answer to the latest one counts. */
    private long lookupGeneration;
    private boolean disposed;

    public PrintFeatures(EditorView view, PdfExportDialog.Collaborators collaborators,
                         PdfExportDialog.Presenter presenter, Clock clock, SheetAnchors sheetAnchors) {
        // Right after «Exportar a PDF…», else after «Guardar como…», else at the end.
        super(view, "action.print", ExportIcons.PRINT, SHORTCUT_ID,
                indexAfter(view, view.shortcutActions().get(ExportPdfFeatures.SHORTCUT_ID), view.actions().saveAs()));
        this.collaborators = Objects.requireNonNull(collaborators, "collaborators");
        this.presenter = Objects.requireNonNull(presenter, "presenter");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sheetAnchors = Objects.requireNonNull(sheetAnchors, "sheetAnchors");
        this.fileMenuListener = Menus.onOpen(view.menu(MenuId.FILE), this::lookUpPrinters);
        lookUpPrinters();
    }

    /** Asks the system for printers off the EDT and applies the answer on the EDT. */
    private void lookUpPrinters() {
        if (disposed) {
            return;
        }
        long generation = ++lookupGeneration;
        collaborators.printers().hasPrinters().whenComplete((installed, failure) ->
                collaborators.edt().execute(() -> applyLookup(generation, installed, failure)));
    }

    private void applyLookup(long generation, Boolean installed, Throwable failure) {
        if (disposed || generation != lookupGeneration) {
            return;
        }
        if (failure != null) {
            // the dialog will say what is wrong if printing really fails
            LOGGER.warn("Could not look up the printers", failure);
        }
        noPrinters = failure == null && !installed;
        action().putValue(Action.SHORT_DESCRIPTION, noPrinters ? Messages.text("print.noPrinters") : null);
        refreshEnabled();
    }

    @Override
    protected boolean ready() {
        return !noPrinters;
    }

    /** Freezes the circuit and opens the dialog to print it; the dialog does the rest. */
    @Override
    protected void export() {
        if (!available()) {
            return;
        }
        PdfExportDialog dialog = new PdfExportDialog(PdfExportDialog.Mode.PRINT,
                ExportPdfFeatures.capture(view(), clock, sheetAnchors),
                () -> ExportPdfFeatures.capture(view(), clock, sheetAnchors),
                view().services().preferences(), startDirectory(view().services().preferences().printDirectory()),
                ExportPdfFeatures.suggestedName(view().openDocument()), collaborators);
        presenter.present(view(), dialog);
    }

    @Override
    protected void disposeExtras() {
        disposed = true;
        view().menu(MenuId.FILE).removeMenuListener(fileMenuListener);
    }
}
