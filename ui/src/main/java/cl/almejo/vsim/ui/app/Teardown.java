package cl.almejo.vsim.ui.app;

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

    /** Undoes every step, the last added first, and forgets them; a step added meanwhile waits for the next run. */
    public void run() {
        List<Runnable> undo = new ArrayList<>(steps);
        steps.clear();
        for (int index = undo.size() - 1; index >= 0; index--) {
            undo.get(index).run();
        }
    }
}
