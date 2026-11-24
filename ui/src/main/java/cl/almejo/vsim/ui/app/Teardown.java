package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.Steps;

import java.util.ArrayList;
import java.util.List;

/**
 * What to undo when something that was open goes away (a tour, a tip, the shortcut sheet): the listeners it added, the
 * hooks it installed, the timers it started. Undone the last first, so each step finds what it undoes as it left it.
 */
public final class Teardown {

    private final List<Runnable> steps = new ArrayList<>();

    /** Adds the step that undoes something just done. */
    public void add(Runnable undo) {
        steps.add(undo);
    }

    /**
     * Undoes every step, the last added first, and forgets them; a step added meanwhile waits for the next run. Every
     * step runs even when one throws (a listener left on would outlive what it listened to); the first failure is
     * rethrown at the end, the later ones attached to it as suppressed.
     */
    public void run() {
        List<Runnable> undo = new ArrayList<>(steps);
        steps.clear();
        Steps.runEach(undo.reversed()).ifPresent(failure -> {
            throw failure;
        });
    }
}
