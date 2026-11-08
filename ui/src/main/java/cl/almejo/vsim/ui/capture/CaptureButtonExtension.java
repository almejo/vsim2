package cl.almejo.vsim.ui.capture;

import cl.almejo.vsim.ui.app.Animations;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import java.util.Objects;
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

    /** The real thing: the system clipboard, Swing timers, the system clock and the global animations setting. */
    public CaptureButtonExtension() {
        this(ImageClipboard.system(), CaptureButtonFeatures::swingTicker, System::currentTimeMillis,
                Animations::enabled);
    }

    /**
     * {@code tickers} gives a fresh ticker for each flash and each notice, {@code clock} is what they read,
     * {@code animations} says whether the flash (and the notice's fade) show.
     */
    public CaptureButtonExtension(ImageClipboard clipboard, Supplier<FrameTicker> tickers, LongSupplier clock,
                                  Predicate<AppPreferences> animations) {
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
        this.tickers = Objects.requireNonNull(tickers, "tickers");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.animations = Objects.requireNonNull(animations, "animations");
    }

    @Override
    public Runnable install(EditorView view) {
        CaptureButtonFeatures features = new CaptureButtonFeatures(view, clipboard, tickers, clock, animations);
        return features::dispose;
    }
}
