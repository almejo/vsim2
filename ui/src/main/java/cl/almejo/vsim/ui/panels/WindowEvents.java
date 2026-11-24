package cl.almejo.vsim.ui.panels;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

/** One listener for «the window did X» that only forwards to a {@link Runnable} (closing, opened, closed). */
public final class WindowEvents {

    private WindowEvents() {
    }

    /** Runs {@code listener} when the user asks to close the window (the close box, Alt+F4), before it closes. */
    public static WindowListener onClosing(Runnable listener) {
        return new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                listener.run();
            }
        };
    }

    /** Runs {@code listener} the first time the window is shown. */
    public static WindowListener onOpened(Runnable listener) {
        return new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent event) {
                listener.run();
            }
        };
    }

    /** Runs {@code listener} once the window was disposed. */
    public static WindowListener onClosed(Runnable listener) {
        return new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                listener.run();
            }
        };
    }
}
