package cl.almejo.vsim.ui.canvas;

import javax.swing.Timer;

/**
 * Calls a frame again and again while an animation runs: a Swing timer in the editor, a hand-driven ticker in tests
 * (which never sleep). The frames run on the EDT and read the time themselves, so a late frame just shows a later
 * moment of the animation.
 */
public interface FrameTicker {

    /** Starts calling {@code frame}, replacing the frame of a run already going. */
    void start(Runnable frame);

    void stop();

    boolean isRunning();

    /** One frame every {@code milliseconds}, on the EDT; late frames coalesce instead of piling up. */
    static FrameTicker swing(int milliseconds) {
        return new FrameTicker() {
            private Runnable current = () -> {
            };
            private final Timer timer = new Timer(milliseconds, event -> current.run());

            {
                timer.setCoalesce(true);
            }

            @Override
            public void start(Runnable frame) {
                current = frame;
                if (!timer.isRunning()) {
                    timer.start();
                }
            }

            @Override
            public void stop() {
                timer.stop();
            }

            @Override
            public boolean isRunning() {
                return timer.isRunning();
            }
        };
    }
}
