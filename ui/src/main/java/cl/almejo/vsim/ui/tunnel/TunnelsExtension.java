package cl.almejo.vsim.ui.tunnel;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/** Adds the hover, status text and context-menu travel of wire tunnels ({@link TunnelFeatures}) to every view. */
public final class TunnelsExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        TunnelFeatures features = new TunnelFeatures(view);
        return features::dispose;
    }
}
