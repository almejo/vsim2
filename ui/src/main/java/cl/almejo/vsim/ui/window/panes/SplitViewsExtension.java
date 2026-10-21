package cl.almejo.vsim.ui.window.panes;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/** Adds the split grips and Ventana ▸ «Dividir ▸» / «Siguiente panel» to every editor view. */
public final class SplitViewsExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        SplitViewsFeatures features = new SplitViewsFeatures(view);
        return features::dispose;
    }
}
