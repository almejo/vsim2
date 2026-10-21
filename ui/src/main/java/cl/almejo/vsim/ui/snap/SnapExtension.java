package cl.almejo.vsim.ui.snap;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/** Adds «Ajustar» (the magnet: parts, guides and grid lines while moving) to every editor view. */
public final class SnapExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        SnapFeatures features = new SnapFeatures(view);
        return features::dispose;
    }
}
