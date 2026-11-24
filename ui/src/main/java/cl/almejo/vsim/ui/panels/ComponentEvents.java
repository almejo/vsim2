package cl.almejo.vsim.ui.panels;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;

/** One listener for «the component changed size or place» that only forwards to a {@link Runnable}. */
public final class ComponentEvents {

    private ComponentEvents() {
    }

    /** Runs {@code listener} each time the component is resized. */
    public static ComponentListener onResized(Runnable listener) {
        return new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                listener.run();
            }
        };
    }

    /** Runs {@code listener} each time the component is resized or moved (not when shown or hidden). */
    public static ComponentListener onMovedOrResized(Runnable listener) {
        return new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent event) {
                listener.run();
            }

            @Override
            public void componentResized(ComponentEvent event) {
                listener.run();
            }
        };
    }
}
