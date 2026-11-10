package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.KeyDispatchers;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.window.EditorView;

import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** The real {@link WhatsNewExtension.Tours}: the bubbles one by one over the dimmed window ({@link BubbleTour}). */
final class BubbleTours implements WhatsNewExtension.Tours {

    /**
     * What the bubbles touch outside the view, replaceable in tests.
     *
     * @param dispatchers where the key hook of → ← Enter Esc goes
     * @param tickers     a ticker for the slide between targets
     * @param clock       what the slide reads
     * @param animations  whether the bubble slides (Preferencias ▸ «Animaciones») or jumps
     */
    record Seams(KeyDispatchers dispatchers, Supplier<FrameTicker> tickers, LongSupplier clock,
                 Predicate<AppPreferences> animations) {

        Seams {
            Objects.requireNonNull(dispatchers, "dispatchers");
            Objects.requireNonNull(tickers, "tickers");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(animations, "animations");
        }
    }

    private final Seams seams;

    BubbleTours(Seams seams) {
        this.seams = Objects.requireNonNull(seams, "seams");
    }

    /** Opens the bubbles of {@code news} over {@code view}'s window and returns the tour (not open: nothing shown). */
    BubbleTour start(EditorView view, NewsFile news, Runnable ended) {
        BubbleTour tour = new BubbleTour(view, news, ended, seams);
        tour.open();
        return tour;
    }

    @Override
    public Runnable open(EditorView view, NewsFile news, Runnable ended) {
        return start(view, news, ended)::close;
    }
}
