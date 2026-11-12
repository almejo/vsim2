package cl.almejo.vsim.ui.lens;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/**
 * Adds the accessibility lens («Lupa»: its key, Ver ▸ «Lupa» and the toolbar button turn it on and off) to every
 * editor view.
 */
public final class LensExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        LensFeatures features = new LensFeatures(view);
        return features::dispose;
    }
}
