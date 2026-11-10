package cl.almejo.vsim.ui.whatsnew;

import java.util.Objects;

/**
 * Holds the automatic bubbles back until the window is free for them: never in presentation mode (the screen belongs
 * to the class) nor while the AI works on the circuit; they come as soon as both end, in the same run. Starts them at
 * most once. EDT only.
 */
final class TourGate {

    /** What the window is doing now, and a way to hear when it changes. */
    interface Conditions {

        boolean presenting();

        boolean aiWorking();

        /** {@code listener} hears every change of either; returns what stops it. */
        Runnable addChangeListener(Runnable listener);
    }

    private final Conditions conditions;
    private final Runnable start;
    private Runnable stopListening = () -> {
    };
    private boolean done;

    TourGate(Conditions conditions, Runnable start) {
        this.conditions = Objects.requireNonNull(conditions, "conditions");
        this.start = Objects.requireNonNull(start, "start");
    }

    /** The window is visible and settled: start now when it is free, else as soon as it is. */
    void windowReady() {
        if (done) {
            return;
        }
        if (isFree()) {
            begin();
            return;
        }
        stopListening.run();
        stopListening = conditions.addChangeListener(this::conditionsChanged);
    }

    private void conditionsChanged() {
        if (!done && isFree()) {
            begin();
        }
    }

    private boolean isFree() {
        return !conditions.presenting() && !conditions.aiWorking();
    }

    private void begin() {
        done = true;
        stopListening.run();
        start.run();
    }

    /** The window closes: nothing starts any more. */
    void dispose() {
        done = true;
        stopListening.run();
    }
}
