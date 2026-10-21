package cl.almejo.vsim.ui.window.panes;

import cl.almejo.vsim.ui.app.FileNames;

import javax.swing.Timer;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * Keeps one window's split in the {@link SplitLayoutStore} (Dividir la vista, spec §7): restores it when the window of
 * a file being opened is first shown, saves it a moment after the split changes and at once when the window closes,
 * and follows the file through «Guardar como». Nothing for an unsaved circuit. It never touches the document: the
 * split is not an edit. EDT only.
 */
public final class RememberedSplit {

    /** How long after the last change of the split it is saved. */
    public static final int SAVE_DELAY_MILLISECONDS = 500;
    /** A remembered layout whose panes would be smaller than this (either side) does not fit and is not restored. */
    public static final int MINIMUM_PANE_SIZE = 24;

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

    private final SplitLayoutStore store;
    private final CanvasPanes panes;
    private final Supplier<Optional<Path>> file;
    private final Delay delay;
    private final Runnable removeLayoutListener;
    private Runnable stopPendingSave = () -> {
    };
    private boolean restoreRequested;
    private Optional<PaneLayout> toRestore = Optional.empty();
    private boolean closed;

    /** @param file the circuit's file now (empty while unsaved); read every time, so Save As is followed */
    public RememberedSplit(SplitLayoutStore store, CanvasPanes panes, Supplier<Optional<Path>> file, Delay delay) {
        this.store = store;
        this.panes = panes;
        this.file = file;
        this.delay = delay;
        this.removeLayoutListener = panes.addLayoutListener(this::scheduleSave);
    }

    /**
     * The window shows a file being opened (Abrir, Recientes, the last session, a recovered copy), not another view of
     * an open one: its remembered split is restored when it is shown ({@link #showRemembered}).
     */
    public void restoreWhenShown() {
        restoreRequested = true;
    }

    /**
     * The window shows its initial view (when it opens, and again once a restored maximized window has its size):
     * applies the remembered split, read the first time, when one was requested and it fits the canvas area now;
     * otherwise the window keeps its single pane.
     */
    public void showRemembered() {
        if (!restoreRequested || closed) {
            return;
        }
        if (toRestore.isEmpty()) {
            toRestore = file.get().flatMap(store::layoutFor);
        }
        toRestore.filter(this::fits).ifPresent(panes::apply);
    }

    /**
     * The circuit was just written to {@code target}, before that {@code previous} (empty for its first save): the
     * split is remembered for {@code target} now and, when the file changed (Save As), forgotten for {@code previous}.
     */
    public void saved(Optional<Path> previous, Path target) {
        previous.filter(old -> !FileNames.normalized(old).equals(FileNames.normalized(target)))
                .ifPresent(store::forget);
        stopPendingSave.run();
        store.remember(target, panes.paneLayout());
    }

    /** The window closes: saves the split now, stops the pending save and stops listening. Idempotent. */
    public void close() {
        if (closed) {
            return;
        }
        stopPendingSave.run();
        save();
        removeLayoutListener.run();
        closed = true;
    }

    /** Whether every pane of {@code layout} would be at least {@link #MINIMUM_PANE_SIZE} px on each side. */
    private boolean fits(PaneLayout layout) {
        int thickness = PaneDivider.thickness();
        return fits(layout.horizontalDivider(), panes.getHeight(), thickness)
                && fits(layout.verticalDivider(), panes.getWidth(), thickness);
    }

    private static boolean fits(OptionalDouble divider, int length, int thickness) {
        if (divider.isEmpty()) {
            return length >= MINIMUM_PANE_SIZE;
        }
        double available = length - thickness;
        double before = divider.getAsDouble() * available;
        return before >= MINIMUM_PANE_SIZE && available - before >= MINIMUM_PANE_SIZE;
    }

    private void scheduleSave() {
        if (closed) {
            return;
        }
        stopPendingSave.run();
        stopPendingSave = delay.after(SAVE_DELAY_MILLISECONDS, this::save);
    }

    private void save() {
        stopPendingSave = () -> {
        };
        file.get().ifPresent(path -> store.remember(path, panes.paneLayout()));
    }
}
