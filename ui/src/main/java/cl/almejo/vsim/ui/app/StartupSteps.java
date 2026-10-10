package cl.almejo.vsim.ui.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The fault-tolerant shape of the application start (M6-B): one circuit that fails to open never stops the others,
 * and whatever happens the start ends with at least one window and autosave running.
 */
final class StartupSteps {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartupSteps.class);

    private StartupSteps() {
    }

    /** Opens each of {@code openings}; a failure is logged and the next one still opens. */
    static <O> void openEach(List<O> openings, Consumer<O> open) {
        for (O opening : openings) {
            guarded("open " + opening, () -> open.accept(opening));
        }
    }

    /**
     * Runs {@code openings} and then, even if it threw (the exception is rethrown afterwards), {@link #finish}.
     */
    static void run(Runnable openings, BooleanSupplier hasWindows, Runnable newDocument, Runnable importComponents,
                    Runnable startAutosave) {
        try {
            openings.run();
        } finally {
            finish(hasWindows, newDocument, importComponents, startAutosave);
        }
    }

    /** An empty document when nothing opened, the command-line components, then autosave, which always starts. */
    static void finish(BooleanSupplier hasWindows, Runnable newDocument, Runnable importComponents,
                       Runnable startAutosave) {
        try {
            guarded("open an empty document", () -> {
                if (!hasWindows.getAsBoolean()) {
                    newDocument.run();
                }
            });
            guarded("import the command-line components", importComponents);
        } finally {
            startAutosave.run();
        }
    }

    private static void guarded(String step, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            LOGGER.error("Could not {} at start", step, exception);
        }
    }
}
