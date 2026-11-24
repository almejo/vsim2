package cl.almejo.vsim.ui.panels;

import javax.swing.SwingUtilities;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/** Listeners for «the mouse clicked or pressed here» that only forward to a {@link Runnable}, or only consume it. */
public final class MouseClicks {

    private MouseClicks() {
    }

    /** Runs {@code listener} on every click, whatever the button. */
    public static MouseListener onClick(Runnable listener) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                listener.run();
            }
        };
    }

    /** Runs {@code listener} each time a mouse button is pressed on the component, whichever it is. */
    public static MouseListener onPress(Runnable listener) {
        return new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                listener.run();
            }
        };
    }

    /** Runs {@code listener} on a click of the left button (the other buttons do nothing). */
    public static MouseListener onLeftClick(Runnable listener) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    listener.run();
                }
            }
        };
    }

    /**
     * A listener that does nothing: a component that has a mouse listener consumes the mouse events instead of
     * passing them to its parent's listeners (a card over a backdrop that closes on click, a bubble's body).
     */
    public static MouseListener swallowing() {
        return new MouseAdapter() {
        };
    }
}
