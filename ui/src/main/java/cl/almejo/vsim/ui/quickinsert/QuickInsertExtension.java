package cl.almejo.vsim.ui.quickinsert;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/**
 * The command palette: a double click on empty canvas or Insert opens it on «Componentes» (a search over the palette
 * that places what is chosen), Ctrl+K or Ayuda ▸ «Buscar una acción…» on «Acciones» (everything in the menus).
 * Independent of the palette favourites: it offers whatever the palette shows in «Favoritos» and «Recientes». The
 * application creates this extension once, so its list of recent actions is shared by every window. Installed
 * before the shortcuts extension, which binds its keys.
 */
public final class QuickInsertExtension implements EditorExtension {

    private final RecentActions recentActions = new RecentActions();

    @Override
    public Runnable install(EditorView view) {
        QuickInsertFeatures features = new QuickInsertFeatures(view, recentActions);
        return features::dispose;
    }
}
