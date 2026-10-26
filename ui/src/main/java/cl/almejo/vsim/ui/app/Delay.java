package cl.almejo.vsim.ui.app;

import javax.swing.Timer;

/** Runs {@code task} once after a delay, on the EDT; the returned runnable stops it if it has not run yet. */
@FunctionalInterface
public interface Delay {

    Runnable after(int milliseconds, Runnable task);

    /** A one-shot Swing {@link Timer}. */
    static Delay swing() {
        return (milliseconds, task) -> {
            Timer timer = new Timer(milliseconds, event -> task.run());
            timer.setRepeats(false);
            timer.start();
            return timer::stop;
        };
    }
}
