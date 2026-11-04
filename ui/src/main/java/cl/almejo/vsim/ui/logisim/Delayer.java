package cl.almejo.vsim.ui.logisim;

import javax.swing.Timer;

/** Runs something on the event thread after a while; the test fires it by hand instead of waiting. */
@FunctionalInterface
interface Delayer {

    /** Runs {@code action} once, {@code milliseconds} from now, unless the returned handle is run first. */
    Runnable after(long milliseconds, Runnable action);

    /** The real one: a Swing timer, so {@code action} runs on the event thread. */
    static Delayer swing() {
        return (milliseconds, action) -> {
            Timer timer = new Timer((int) milliseconds, event -> action.run());
            timer.setRepeats(false);
            timer.start();
            return timer::stop;
        };
    }
}
