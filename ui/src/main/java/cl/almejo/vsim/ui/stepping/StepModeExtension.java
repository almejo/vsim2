package cl.almejo.vsim.ui.stepping;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/**
 * Adds the configurable fixed step, «Paso a paso» and its advances (M6.1-B) to every editor view; the fixed step's
 * arrow and menu item first, so the step-mode controls go right after them.
 */
public final class StepModeExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        FixedStepFeatures fixedStep = new FixedStepFeatures(view, StepSizeDialog.INSTANCE);
        StepModeFeatures features;
        try {
            features = new StepModeFeatures(view, System::nanoTime);
        } catch (RuntimeException exception) {
            fixedStep.dispose();
            throw exception;
        }
        return () -> {
            features.dispose();
            fixedStep.dispose();
        };
    }
}
