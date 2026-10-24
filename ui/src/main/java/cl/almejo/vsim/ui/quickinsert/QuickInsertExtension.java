package cl.almejo.vsim.ui.quickinsert;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/**
 * Quick insert: a double click on empty canvas or Insert opens a search over the palette and places what is chosen.
 * Independent of the palette favourites: it offers whatever the palette shows in «Favoritos» and «Recientes».
 * Installed before the shortcuts extension, which binds its Insert key.
 */
public final class QuickInsertExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        QuickInsertFeatures features = new QuickInsertFeatures(view);
        return features::dispose;
    }
}
