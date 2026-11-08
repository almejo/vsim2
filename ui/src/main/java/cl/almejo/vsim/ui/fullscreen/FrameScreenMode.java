package cl.almejo.vsim.ui.fullscreen;

import javax.swing.JFrame;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;
import java.util.Objects;
import java.util.Optional;

/**
 * The real full screen of a frame: the monitor the frame is on now shows it alone ({@link GraphicsDevice}'s full
 * screen window, which on Linux asks the window manager for its full-screen state, without the title bar). The bounds
 * and the maximized state of the frame are noted on entering and given back on leaving, so the window returns exactly
 * where and how it was. While in full screen, {@link #wasMaximized()} and {@link #boundsBefore()} answer for the
 * window as the session should remember it. EDT only.
 */
public final class FrameScreenMode implements ScreenMode {

    private final JFrame frame;
    private GraphicsDevice device;
    private Rectangle boundsBefore;
    private int stateBefore;

    FrameScreenMode(JFrame frame) {
        this.frame = Objects.requireNonNull(frame, "frame");
    }

    @Override
    public void enter() {
        if (device != null) {
            return;
        }
        boundsBefore = frame.getBounds();
        stateBefore = frame.getExtendedState();
        GraphicsConfiguration configuration = frame.getGraphicsConfiguration();
        device = configuration.getDevice();
        device.setFullScreenWindow(frame);
    }

    @Override
    public void leave() {
        if (device == null) {
            return;
        }
        GraphicsDevice leaving = device;
        device = null;
        if (leaving.getFullScreenWindow() == frame) {
            leaving.setFullScreenWindow(null);
        }
        if ((stateBefore & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
            // the system keeps the bounds to un-maximize to; setting the maximized bounds would replace them
            frame.setExtendedState(stateBefore & ~Frame.ICONIFIED);
        } else {
            frame.setBounds(boundsBefore);
        }
    }

    /** Whether the frame is in full screen now. */
    public boolean isFullScreen() {
        return device != null;
    }

    /** In full screen: whether the frame was maximized when it entered. False outside full screen. */
    public boolean wasMaximized() {
        return device != null && (stateBefore & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
    }

    /** In full screen: the frame's bounds when it entered; empty outside full screen. */
    public Optional<Rectangle> boundsBefore() {
        return device == null ? Optional.empty() : Optional.of(new Rectangle(boundsBefore));
    }
}
