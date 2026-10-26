package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.export.ExportIcons;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.paint.TextFonts;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.Menus;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;

/**
 * «Exportar a PDF…» of one editor view: the menu item right after «Exportar imagen…» (off for an empty document), the
 * shortcut registration ({@code file.exportPdf}, no default stroke) and the dialog, opened on a frozen copy of the
 * circuit with the selection of this view. {@link #dispose()} takes all of it back.
 */
public final class ExportPdfFeatures {

    static final String SHORTCUT_ID = "file.exportPdf";
    private static final String EXPORT_IMAGE_ID = "file.exportImage";

    private final EditorView view;
    private final PdfExportDialog.Collaborators collaborators;
    private final PdfExportDialog.Presenter presenter;
    private final Clock clock;
    private final Action action;
    private final JMenu fileMenu;
    private final JMenuItem item;
    private final Runnable shortcutRemover;
    private final Runnable changeListener = this::updateEnabled;
    private boolean disposed;

    public ExportPdfFeatures(EditorView view, PdfExportDialog.Collaborators collaborators,
                             PdfExportDialog.Presenter presenter, Clock clock) {
        this.view = view;
        this.collaborators = Objects.requireNonNull(collaborators, "collaborators");
        this.presenter = Objects.requireNonNull(presenter, "presenter");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.action = Actions.of(Messages.text("action.exportPdf"), () -> export());
        Icons.install(action, ExportIcons.EXPORT_PDF);
        this.fileMenu = view.menu(MenuId.FILE);
        this.item = new JMenuItem(action);
        fileMenu.add(item, insertionIndex());
        this.shortcutRemover = view.registerShortcutAction(SHORTCUT_ID, action);
        view.openDocument().addChangeListener(changeListener);
        updateEnabled();
    }

    /** Right after «Exportar imagen…», else after «Guardar como…», else at the end. */
    private int insertionIndex() {
        Action exportImage = view.shortcutActions().get(EXPORT_IMAGE_ID);
        int index = exportImage == null ? -1 : Menus.indexOf(fileMenu, exportImage);
        if (index < 0) {
            index = Menus.indexOf(fileMenu, view.actions().saveAs());
        }
        return index < 0 ? fileMenu.getMenuComponentCount() : index + 1;
    }

    public Action action() {
        return action;
    }

    private void updateEnabled() {
        action.setEnabled(!view.openDocument().document().isEmpty());
    }

    /** Freezes the circuit and opens the dialog on it; the dialog does the rest. */
    void export() {
        if (disposed || !action.isEnabled()) {
            return;
        }
        OpenDocument openDocument = view.openDocument();
        PdfExportDialog dialog = new PdfExportDialog(capture(), this::capture, view.services().preferences(),
                startDirectory(), suggestedName(openDocument), collaborators);
        presenter.present(view, dialog);
    }

    /** The circuit as it is now, with this view's selection, scheme and font; nothing in the document changes. */
    private PrintInput capture() {
        return PrintInput.capture(view.openDocument(), view.selection().ids(),
                view.services().appearance().scheme(), TextFonts.current(), clock);
    }

    /** The last PDF folder, else the document's folder, else the user's home. */
    Path startDirectory() {
        return view.services().preferences().printDirectory()
                .or(() -> view.openDocument().path().map(Path::getParent))
                .orElseGet(() -> Path.of(System.getProperty("user.home")));
    }

    private static String suggestedName(OpenDocument openDocument) {
        String name = openDocument.displayName().replaceAll("[/\\\\:*?\"<>|]", "_");
        return name.toLowerCase(java.util.Locale.ROOT).endsWith(".vsim")
                ? name.substring(0, name.length() - ".vsim".length()) : name;
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        shortcutRemover.run();
        fileMenu.remove(item);
        view.openDocument().removeChangeListener(changeListener);
    }
}
