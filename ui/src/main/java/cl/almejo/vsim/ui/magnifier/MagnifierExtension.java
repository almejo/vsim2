package cl.almejo.vsim.ui.magnifier;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/** Adds the Lupa (spec §8.2) to every editor view. */
public final class MagnifierExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        MagnifierFeatures features = new MagnifierFeatures(view);
        return features::dispose;
    }
}
