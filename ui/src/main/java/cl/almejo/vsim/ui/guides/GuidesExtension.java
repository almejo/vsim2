package cl.almejo.vsim.ui.guides;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/** Adds the rulers, the guides and their Ver and context-menu items to every editor view. */
public final class GuidesExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        GuidesFeatures features = new GuidesFeatures(view);
        return features::dispose;
    }
}
