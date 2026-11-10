package cl.almejo.vsim.ui.shortcuts.sheet;

import cl.almejo.vsim.ui.app.Animations;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.KeyDispatchers;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.SwingUtilities;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The sheet of shortcuts in every editor view ({@link ShortcutSheetFeatures}): «?» or Ayuda ▸ «Atajos de teclado».
 * Installed after the command palette, so its Ayuda item lands right after «Buscar una acción…».
 */
public final class ShortcutSheetExtension implements EditorExtension {

    private static final int FRAME_MILLISECONDS = 16;

    private final Seams seams;

    /**
     * What the sheet touches outside the view, replaceable in tests.
     *
     * @param dispatchers where the key hook of «?» and Esc is installed
     * @param tickers     a fresh ticker for each fade-in
     * @param clock       what the fade reads
     * @param animations  whether the sheet fades in
     * @param later       runs what follows the sheet's closing (opening Preferencias) after the current event
     */
    public record Seams(KeyDispatchers dispatchers, Supplier<FrameTicker> tickers, LongSupplier clock,
                        Predicate<AppPreferences> animations, Consumer<Runnable> later) {

        public Seams {
            Objects.requireNonNull(dispatchers, "dispatchers");
            Objects.requireNonNull(tickers, "tickers");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(animations, "animations");
            Objects.requireNonNull(later, "later");
        }
    }

    /** The real thing: the focus manager, Swing timers, the system clock, Preferencias ▸ «Animaciones». */
    public ShortcutSheetExtension() {
        this(new Seams(KeyDispatchers.focusManager(), () -> FrameTicker.swing(FRAME_MILLISECONDS),
                System::currentTimeMillis, Animations::enabled, SwingUtilities::invokeLater));
    }

    public ShortcutSheetExtension(Seams seams) {
        this.seams = Objects.requireNonNull(seams, "seams");
    }

    @Override
    public Runnable install(EditorView view) {
        ShortcutSheetFeatures features = new ShortcutSheetFeatures(view, seams);
        return features::dispose;
    }
}
