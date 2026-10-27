package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/** Adds the «Notas» tab listing the sticky notes to every editor view. */
public final class StickyNotesTabExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        StickyNotesController controller = new StickyNotesController(view);
        return controller::dispose;
    }
}
