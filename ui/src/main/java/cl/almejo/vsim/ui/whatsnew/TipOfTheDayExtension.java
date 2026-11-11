package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.ai.AiServerService;
import cl.almejo.vsim.ui.app.Animations;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.KeyDispatchers;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The tip of the day: Ayuda ▸ «Consejo del día» in every editor view, and, at most once a day, the card in the first
 * window of a run when the bubbles of «Qué hay de nuevo» do not come (it asks the same {@link StartupNotices}). One
 * instance per application, installed right after {@link WhatsNewExtension}, so its item lands after «Qué hay de
 * nuevo».
 */
public final class TipOfTheDayExtension implements EditorExtension {

    /**
     * What the tip touches outside the view, replaceable in tests.
     *
     * @param tips        the tips, in file order ({@link TipsFile#load})
     * @param readiness   when a view's window is shown and settled
     * @param ai          whether the AI works now
     * @param dispatchers where the Esc hook goes
     * @param tickers     a ticker for the card's fade in
     * @param clock       what the fade reads
     * @param animations  whether the card fades in (Preferencias ▸ «Animaciones»)
     * @param later       runs «Probarlo»'s action once the new tab shows
     */
    record Seams(Supplier<List<Tip>> tips, WhatsNewExtension.WindowReadiness readiness,
                 WhatsNewExtension.AiActivity ai, KeyDispatchers dispatchers, Supplier<FrameTicker> tickers,
                 LongSupplier clock, Predicate<AppPreferences> animations, Executor later) {

        Seams {
            Objects.requireNonNull(tips, "tips");
            Objects.requireNonNull(readiness, "readiness");
            Objects.requireNonNull(ai, "ai");
            Objects.requireNonNull(dispatchers, "dispatchers");
            Objects.requireNonNull(tickers, "tickers");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(animations, "animations");
            Objects.requireNonNull(later, "later");
        }
    }

    private static final int FRAME_MILLISECONDS = 16;

    private final WhatsNewExtension whatsNew;
    private final Seams seams;
    /** Read with the first view; one list for the whole run. */
    private List<Tip> tips;

    /**
     * The real thing: the bundled tips, the window settling after it shows, the application's AI server, the focus
     * manager, Swing timers and Preferencias ▸ «Animaciones».
     */
    public TipOfTheDayExtension(WhatsNewExtension whatsNew, AiServerService aiServer) {
        this(whatsNew, new Seams(TipsFile::load, new SettledWindow(), WhatsNewExtension.AiActivity.of(aiServer),
                KeyDispatchers.focusManager(), () -> FrameTicker.swing(FRAME_MILLISECONDS),
                System::currentTimeMillis, Animations::enabled, SwingUtilities::invokeLater));
    }

    TipOfTheDayExtension(WhatsNewExtension whatsNew, Seams seams) {
        this.whatsNew = Objects.requireNonNull(whatsNew, "whatsNew");
        this.seams = Objects.requireNonNull(seams, "seams");
    }

    @Override
    public Runnable install(EditorView view) {
        if (tips == null) {
            tips = List.copyOf(seams.tips().get());
        }
        TipFeatures features = new TipFeatures(view, seams, whatsNew.noticesFor(view.services().preferences()),
                tips);
        return features::dispose;
    }
}
