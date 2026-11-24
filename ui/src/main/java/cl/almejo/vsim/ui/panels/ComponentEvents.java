package cl.almejo.vsim.ui.panels;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.EnumSet;
import java.util.Set;

/** One listener for «the component changed size, place or visibility» that only forwards to a {@link Runnable}. */
public final class ComponentEvents {

    /** What can happen to a component that a listener here may ask to hear. */
    public enum Change {
        SHOWN, HIDDEN, MOVED, RESIZED
    }

    private ComponentEvents() {
    }

    /** Runs {@code listener} each time the component is resized. */
    public static ComponentListener onResized(Runnable listener) {
        return on(listener, Change.RESIZED);
    }

    /** Runs {@code listener} each time the component is resized or moved (not when shown or hidden). */
    public static ComponentListener onMovedOrResized(Runnable listener) {
        return on(listener, Change.MOVED, Change.RESIZED);
    }

    /** Runs {@code listener} each time the component is hidden. */
    public static ComponentListener onHidden(Runnable listener) {
        return on(listener, Change.HIDDEN);
    }

    /** Runs {@code listener} each time the component is shown or hidden (not when moved or resized). */
    public static ComponentListener onShownOrHidden(Runnable listener) {
        return on(listener, Change.SHOWN, Change.HIDDEN);
    }

    /** Runs {@code listener} each time the component goes through one of {@code changes}, and for no other. */
    public static ComponentListener on(Runnable listener, Change first, Change... others) {
        Set<Change> heard = EnumSet.of(first, others);
        return new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent event) {
                runWhenHeard(Change.SHOWN);
            }

            @Override
            public void componentHidden(ComponentEvent event) {
                runWhenHeard(Change.HIDDEN);
            }

            @Override
            public void componentMoved(ComponentEvent event) {
                runWhenHeard(Change.MOVED);
            }

            @Override
            public void componentResized(ComponentEvent event) {
                runWhenHeard(Change.RESIZED);
            }

            private void runWhenHeard(Change change) {
                if (heard.contains(change)) {
                    listener.run();
                }
            }
        };
    }
}
