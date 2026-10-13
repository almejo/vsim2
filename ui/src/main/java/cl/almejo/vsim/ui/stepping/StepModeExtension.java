package cl.almejo.vsim.ui.stepping;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/** Adds «Paso a paso» and «Avanzar al próximo evento» (M6.1-B) to every editor view. */
public final class StepModeExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        StepModeFeatures features = new StepModeFeatures(view, System::nanoTime);
        return features::dispose;
    }
}
