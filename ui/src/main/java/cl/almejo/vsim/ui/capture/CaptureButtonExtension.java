package cl.almejo.vsim.ui.capture;

import cl.almejo.vsim.ui.app.Animations;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.KeyDispatchers;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import java.awt.Component;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The screenshot split button in the toolbar of every editor view and Editar ▸ «Copiar captura»
 * ({@link CaptureButtonFeatures}).
 */
public final class CaptureButtonExtension implements EditorExtension {

    private final ImageClipboard clipboard;
    private final Supplier<FrameTicker> tickers;
    private final LongSupplier clock;
    private final Predicate<AppPreferences> animations;
    private final KeyDispatchers dispatchers;
    private final BiPredicate<EditorView, Component> inOwnedWindow;

    /** The real thing: the system clipboard, Swing timers, the system clock and the global animations setting. */
    public CaptureButtonExtension() {
        this(ImageClipboard.system(), CaptureButtonFeatures::swingTicker, System::currentTimeMillis,
                Animations::enabled, KeyDispatchers.focusManager(), CaptureButtonFeatures::inWindowOwnedByTheViewsWindow);
    }

    /**
     * {@code tickers} gives a fresh ticker for each flash and each notice, {@code clock} is what they read,
     * {@code animations} says whether the flash (and the notice's fade) show, {@code dispatchers} takes the key
     * dispatcher of the shortcut for dialogs, {@code inOwnedWindow} says whether a key's source is in a window
     * (a dialog) owned, even through other dialogs, by the view's window.
     */
    public CaptureButtonExtension(ImageClipboard clipboard, Supplier<FrameTicker> tickers, LongSupplier clock,
                                  Predicate<AppPreferences> animations, KeyDispatchers dispatchers,
                                  BiPredicate<EditorView, Component> inOwnedWindow) {
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
        this.tickers = Objects.requireNonNull(tickers, "tickers");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.animations = Objects.requireNonNull(animations, "animations");
        this.dispatchers = Objects.requireNonNull(dispatchers, "dispatchers");
        this.inOwnedWindow = Objects.requireNonNull(inOwnedWindow, "inOwnedWindow");
    }

    @Override
    public Runnable install(EditorView view) {
        CaptureButtonFeatures features = new CaptureButtonFeatures(view, clipboard, tickers, clock, animations,
                dispatchers, source -> inOwnedWindow.test(view, source));
        return features::dispose;
    }
}
