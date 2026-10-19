package cl.almejo.vsim.ui.search;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/**
 * Adds «Buscar en el circuito» (the find bar, its highlights and the three Editar items) to every editor view.
 * Installed before ActionIcons and the shortcuts, which then see its actions.
 */
public final class FindExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        FindFeatures features = new FindFeatures(view);
        return features::dispose;
    }
}
