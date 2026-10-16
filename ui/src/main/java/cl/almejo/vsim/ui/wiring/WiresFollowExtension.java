package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/** Adds the «Los cables siguen al mover» toggle (M6.7) to every editor view. */
public final class WiresFollowExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        WiresFollowFeatures features = new WiresFollowFeatures(view);
        return features::dispose;
    }
}
