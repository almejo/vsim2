package cl.almejo.vsim.ui.fullscreen;

import javax.swing.JFrame;

/**
 * Puts a window on the whole of its monitor, without the system's title bar, and back: the seam between presentation
 * mode and the real screen, so tests never go full screen. One per window ({@link #of}); leaving restores the window's
 * bounds and maximized state exactly as they were on entering.
 */
public interface ScreenMode {

    /** The window takes the whole monitor it is on. */
    void enter();

    /** The window goes back to where and how it was before {@link #enter()}. */
    void leave();

    /**
     * The window is closing (in the mode or not): it leaves the full screen if it is in it, without giving back its
     * bounds or maximizing it again, and stops every timer and listener this object put on it. What it noted on
     * entering still answers for the session. Nothing works after it.
     */
    void close();

    /** The real full screen of {@code frame} on the monitor it is on. */
    static FrameScreenMode of(JFrame frame) {
        return new FrameScreenMode(frame);
    }
}
