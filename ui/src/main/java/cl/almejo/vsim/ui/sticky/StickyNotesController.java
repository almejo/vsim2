package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.InspectorPanel;

/**
 * The «Notas» section of one editor window: registers the panel, keeps the quiet badge of its icon on the note count
 * and follows the scheme. The panel reads the window's active pane at every choice, so with split views it always
 * travels the pane in use.
 */
public final class StickyNotesController {

    private final EditorView view;
    private final StickyNotesPanel panel;
    private final Runnable schemeListener;
    /** The last count heard, kept while the panel is being built (a document that already has notes). */
    private int count;
    private boolean registered;
    private boolean disposed;

    public StickyNotesController(EditorView view) {
        this.view = view;
        this.panel = new StickyNotesPanel(view.openDocument(), view::canvas,
                () -> view.services().appearance().scheme(), this::countChanged);
        view.inspector().addTab(InspectorPanel.NOTES, Messages.text("stickyNotes.tab"), InspectorPanel.NOTES_ICON,
                panel);
        registered = true;
        showCount();
        schemeListener = panel::schemeChanged;
        view.services().appearance().addListener(schemeListener);
    }

    private void countChanged(int newCount) {
        count = newCount;
        if (registered && !disposed) {
            showCount();
        }
    }

    private void showCount() {
        view.inspector().setBadge(InspectorPanel.NOTES, count, InspectorPanel.Badge.QUIET);
    }

    public StickyNotesPanel panel() {
        return panel;
    }

    /** Stops listening; the section itself cannot be removed and stays with its last rows. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        view.services().appearance().removeListener(schemeListener);
        panel.dispose();
    }
}
