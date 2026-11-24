package cl.almejo.vsim.ui.panels;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

/** One listener for «the focus came or went» that only forwards to a {@link Runnable}. */
public final class FocusEvents {

    private FocusEvents() {
    }

    /** Runs {@code listener} when the component loses the focus (a temporary loss too; the event is not passed). */
    public static FocusListener onLost(Runnable listener) {
        return new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                listener.run();
            }
        };
    }

    /** Runs {@code listener} when the component gains the focus. */
    public static FocusListener onGained(Runnable listener) {
        return new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                listener.run();
            }
        };
    }
}
