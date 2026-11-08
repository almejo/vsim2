package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.canvas.FrameTicker;

import javax.swing.JFrame;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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
    private GraphicsDevice device;
    private Rectangle boundsBefore;
    private int stateBefore;

    FrameScreenMode(JFrame frame) {
        this.frame = Objects.requireNonNull(frame, "frame");
        // the window manager's answers: the resync looks at the insets at once instead of at its next tick
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent event) {
                resync.check();
            }

            @Override
            public void componentResized(ComponentEvent event) {
                resync.check();
            }
        });
        frame.addWindowStateListener(event -> resync.check());
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
        resync.cancel();
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
            // the system keeps the bounds to un-maximize to; setting the maximized bounds would replace them.
            // Once the title bar is back, a round trip through the normal state places the window again.
            frame.setExtendedState(state);
            resync.start(frame::getInsets, frame::getBounds, new Rectangle(), ignored -> { // never nudged: one round trip
                frame.setExtendedState(state & ~Frame.MAXIMIZED_BOTH);
                frame.setExtendedState(state);
            });
        } else {
            frame.setBounds(boundsBefore);
            // set while the title bar was coming back: set them again once it is there
            resync.start(frame::getInsets, frame::getBounds, boundsBefore, frame::setBounds);
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
