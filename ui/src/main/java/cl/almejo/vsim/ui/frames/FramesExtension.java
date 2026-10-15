package cl.almejo.vsim.ui.frames;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/** Adds the frame features (M6.3) to every editor view. */
public final class FramesExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        FramesFeatures features = new FramesFeatures(view);
        return features::dispose;
    }
}
