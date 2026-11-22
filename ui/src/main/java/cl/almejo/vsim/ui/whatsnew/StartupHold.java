package cl.almejo.vsim.ui.whatsnew;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Keeps the start's popups (the bubbles of «Qué hay de nuevo», the tip of the day) back while the start asks its own
 * question (the recovery of copies) and opens its documents: they must not compete with it, nor show over a window
 * that is about to close. Never held, it changes nothing. EDT only.
 */
public final class StartupHold {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartupHold.class);

    private boolean held;
    private final List<Runnable> waiting = new ArrayList<>();

    /** From now on, what waits in {@link #whenReleased} waits for {@link #release}. */
    public void hold() {
        held = true;
    }

    /** The start is done: everything that waited runs, in the order it asked. */
    public void release() {
        held = false;
        List<Runnable> toRun = List.copyOf(waiting);
        waiting.clear();
        for (Runnable action : toRun) {
            try {
                action.run();
            } catch (RuntimeException exception) {
                LOGGER.warn("A popup that waited for the start failed", exception);
            }
        }
    }

    /** {@code action} runs now, or at the release when the start is holding. */
    void whenReleased(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (held) {
            waiting.add(action);
        } else {
            action.run();
        }
    }
}
