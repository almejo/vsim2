package cl.almejo.vsim.ui.grid;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/** Adds Ver ▸ «Cuadrícula ▸» (dots, lines or nothing behind the circuit) to every editor view. */
public final class GridExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        GridFeatures features = new GridFeatures(view);
        return features::dispose;
    }
}
