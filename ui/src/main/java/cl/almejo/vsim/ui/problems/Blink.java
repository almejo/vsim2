package cl.almejo.vsim.ui.problems;

import javax.swing.Timer;

/**
 * A short on/off blink: lit at {@link #start()}, then {@link #TOGGLES} timer steps of {@link #INTERVAL_MILLISECONDS}
 * (about 1.5 s), unlit at the end. Calls {@code changed} (a repaint) whenever it switches. EDT only.
 */
public final class Blink {

    public static final int INTERVAL_MILLISECONDS = 250;
    public static final int TOGGLES = 6;

    private final Runnable changed;
    private final Timer timer;
    private int remainingToggles;
    private boolean lit;

    public Blink(Runnable changed) {
        this.changed = changed;
        this.timer = new Timer(INTERVAL_MILLISECONDS, event -> tick());
    }

    /** Starts (or restarts) the blink, lit. */
    public void start() {
        remainingToggles = TOGGLES;
        lit = true;
        timer.restart();
        changed.run();
    }

    /** Ends a running blink at once, with nothing lit. */
    public void stop() {
        dispose();
        changed.run();
    }

    public boolean isBlinking() {
        return remainingToggles > 0;
    }

    public boolean isLit() {
        return lit;
    }

    /** One timer step: switch on/off; after {@link #TOGGLES} steps the blink ends unlit. */
    public void tick() {
        if (remainingToggles <= 0) {
            return;
        }
        remainingToggles--;
        if (remainingToggles == 0) {
            lit = false;
            timer.stop();
        } else {
            lit = !lit;
        }
        changed.run();
    }

    /** Stops the timer and ends the blink without calling {@code changed}. */
    public void dispose() {
        timer.stop();
        remainingToggles = 0;
        lit = false;
    }
}
