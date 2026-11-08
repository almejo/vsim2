package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.canvas.FrameTicker;

import javax.swing.JFrame;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowStateListener;
import java.util.Objects;
import java.util.Optional;

/**
 * The real full screen of a frame: the monitor the frame is on now shows it alone ({@link GraphicsDevice}'s full
 * screen window, which on Linux asks the window manager for its full-screen state, without the title bar). The bounds
 * and the maximized state of the frame are noted on entering and given back on leaving, so the window returns exactly
 * where and how it was. While in full screen, {@link #wasMaximized()} and {@link #boundsBefore()} answer for the
 * window as the session should remember it. After each transition the bounds are given again once the window manager
 * has taken the title bar away or put it back ({@link FrameResync}): set while it changes, they leave AWT's idea of
 * where the window is off by the title bar's height on GNOME/XWayland, popups open that much below the pointer and
 * menus close at once. EDT only.
 */
public final class FrameScreenMode implements ScreenMode {

    /** How often the resync looks at the frame's insets while it waits for the window manager. */
    private static final int RESYNC_FRAME_MILLISECONDS = 50;

    private final JFrame frame;
    private final FrameResync resync = new FrameResync(FrameTicker.swing(RESYNC_FRAME_MILLISECONDS),
            System::currentTimeMillis);
    private final Remaximize remaximize = new Remaximize(FrameTicker.swing(RESYNC_FRAME_MILLISECONDS),
            System::currentTimeMillis);
    private final ComponentAdapter frameListener = new ComponentAdapter() {
        @Override
        public void componentMoved(ComponentEvent event) {
            windowManagerAnswered();
        }

        @Override
        public void componentResized(ComponentEvent event) {
            windowManagerAnswered();
        }
    };
    private final WindowStateListener stateListener = event -> windowManagerAnswered();
    private GraphicsDevice device;
    private Rectangle boundsBefore;
    private int stateBefore;
    /** Closed while in full screen or coming back from it: what was noted on entering still answers. */
    private boolean closedInTransition;

    FrameScreenMode(JFrame frame) {
        this.frame = Objects.requireNonNull(frame, "frame");
        // the window manager's answers: the resync looks at the insets at once instead of at its next tick
        frame.addComponentListener(frameListener);
        frame.addWindowStateListener(stateListener);
    }

    @Override
    public void enter() {
        if (device != null) {
            return;
        }
        if (!remaximize.isPending()) {
            // entering again before the window was maximized again: it is still the window of the first entry
            boundsBefore = frame.getBounds();
            stateBefore = frame.getExtendedState();
        }
        GraphicsConfiguration configuration = frame.getGraphicsConfiguration();
        device = configuration.getDevice();
        resync.cancel();
        remaximize.cancel();
        device.setFullScreenWindow(frame);
        // the screen bounds were set while the title bar was still there: set them again once it is gone
        resync.start(frame::getInsets, frame::getBounds, device.getDefaultConfiguration().getBounds(),
                frame::setBounds);
    }

    @Override
    public void leave() {
        if (device == null) {
            return;
        }
        GraphicsDevice leaving = device;
        device = null;
        resync.cancel();
        if (leaving.getFullScreenWindow() == frame) {
            leaving.setFullScreenWindow(null);
        }
        int state = stateBefore & ~Frame.ICONIFIED;
        if ((stateBefore & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
            // the system keeps the bounds to un-maximize to (never set the maximized bounds); a maximize asked while
            // the title bar comes back is lost under mutter, so it waits for the decorations, then asks once
            remaximize.start(new FrameWindow(), state);
        } else {
            frame.setBounds(boundsBefore);
            // set while the title bar was coming back: set them again once it is there
            resync.start(frame::getInsets, frame::getBounds, boundsBefore, frame::setBounds);
        }
    }

    @Override
    public void close() {
        closedInTransition = isTransitioning();
        if (device != null) {
            GraphicsDevice leaving = device;
            device = null;
            if (leaving.getFullScreenWindow() == frame) {
                leaving.setFullScreenWindow(null);
            }
        }
        resync.cancel();
        remaximize.cancel();
        frame.removeComponentListener(frameListener);
        frame.removeWindowStateListener(stateListener);
    }

    private void windowManagerAnswered() {
        resync.check();
        remaximize.check();
    }

    /**
     * Whether the frame is in full screen or still coming back from it (being maximized again): its bounds and state
     * then are not the ones the session should keep.
     */
    public boolean isTransitioning() {
        return device != null || remaximize.isPending() || closedInTransition;
    }

    /** The frame as {@link Remaximize} sees it. */
    private final class FrameWindow implements Remaximize.Window {

        @Override
        public int state() {
            return frame.getExtendedState();
        }

        @Override
        public void setState(int state) {
            frame.setExtendedState(state);
        }

        @Override
        public Insets insets() {
            return frame.getInsets();
        }
    }

    /** While {@link #isTransitioning()}: whether the frame was maximized when it entered. False otherwise. */
    public boolean wasMaximized() {
        return isTransitioning() && (stateBefore & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
    }

    /** While {@link #isTransitioning()}: the frame's bounds when it entered; empty otherwise. */
    public Optional<Rectangle> boundsBefore() {
        return isTransitioning() ? Optional.of(new Rectangle(boundsBefore)) : Optional.empty();
    }
}
