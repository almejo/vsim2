package cl.almejo.vsim.ui.busvalues;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/** Adds the «Valores de los buses» toggle to every editor view. */
public final class BusValuesExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        BusValuesFeatures features = new BusValuesFeatures(view);
        return features::dispose;
    }
}
