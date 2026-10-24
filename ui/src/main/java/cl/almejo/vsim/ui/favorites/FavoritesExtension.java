package cl.almejo.vsim.ui.favorites;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/** Adds shared palette favourites and the quick-insert popup before keyboard shortcuts are installed. */
public final class FavoritesExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        QuickInsertFeatures features = new QuickInsertFeatures(view);
        return features::dispose;
    }
}
