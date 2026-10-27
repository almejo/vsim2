package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.ui.window.EditorView;

/**
 * The «Notas» tab of one editor window: registers the panel, keeps its title («Notas (n)») and follows the scheme.
 * The panel reads the window's active pane at every choice, so with split views it always travels the pane in use.
 */
public final class StickyNotesController {

    private final EditorView view;
    private final StickyNotesPanel panel;
    private final Runnable schemeListener;
    private String title;
    private boolean disposed;

    public StickyNotesController(EditorView view) {
        this.view = view;
        this.title = StickyNotesPanel.titleFor(0);
        this.panel = new StickyNotesPanel(view.openDocument(), view::canvas,
                () -> view.services().appearance().scheme(), this::retitle);
        view.inspector().addTab(title, panel);
        schemeListener = panel::schemeChanged;
        view.services().appearance().addListener(schemeListener);
    }

    private void retitle(String newTitle) {
        if (disposed || newTitle.equals(title)) {
            return;
        }
        title = newTitle;
        if (panel != null) {
            // while the panel is being built (a document that already has notes) addTab uses the title kept here
            view.inspector().setTitle(panel, newTitle);
        }
    }

    public StickyNotesPanel panel() {
        return panel;
    }

    /** Stops listening; the tab itself cannot be removed and stays with its last rows. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        view.services().appearance().removeListener(schemeListener);
        panel.dispose();
    }
}
