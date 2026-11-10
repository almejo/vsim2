package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * «Qué hay de nuevo»: Ayuda ▸ «Qué hay de nuevo» in every editor view, and, the first time a version opens, its
 * bubbles in the first window ({@link StartupNotices}, {@link TourGate}). One instance per application: it holds the
 * run's {@link StartupNotices}, shared by every view. Installed right after the sheet of shortcuts, so its Ayuda item
 * lands after «Atajos de teclado».
 */
public final class WhatsNewExtension implements EditorExtension {

    /** Whether the AI works on a circuit now, and a way to hear when that changes (on the EDT). */
    public interface AiActivity {

        boolean working();

        /** {@code listener} hears every change; returns what stops it. */
        Runnable addListener(Runnable listener);

        /** No AI: it never works (tests that install every extension). */
        static AiActivity none() {
            return new AiActivity() {
                @Override
                public boolean working() {
                    return false;
                }

                @Override
                public Runnable addListener(Runnable listener) {
                    return () -> {
                    };
                }
            };
        }
    }

    /** Runs {@code ready} once {@code view}'s window shows and settles; returns what cancels it. */
    interface WindowReadiness {
        Runnable whenReady(EditorView view, Runnable ready);
    }

    /** Opens the bubbles of {@code news} in {@code view}; {@code ended} runs when the user ends them. */
    interface Tours {
        /** Returns what takes the bubbles away without {@code ended} (the window closes). */
        Runnable open(EditorView view, NewsFile news, Runnable ended);
    }

    /**
     * What the extension touches outside the view, replaceable in tests.
     *
     * @param runningVersion this VSim's version
     * @param news           the news of a running version ({@link NewsFiles#forRunning})
     * @param rule           once per version, or at every launch with the development switch
     * @param readiness      when a view's window is shown and settled
     * @param ai             whether the AI works now
     * @param tours          the bubbles themselves
     */
    record Seams(String runningVersion, Function<String, Optional<NewsFile>> news, StartupNotices.NewsRule rule,
                 WindowReadiness readiness, AiActivity ai, Tours tours) {

        Seams {
            Objects.requireNonNull(runningVersion, "runningVersion");
            Objects.requireNonNull(news, "news");
            Objects.requireNonNull(rule, "rule");
            Objects.requireNonNull(readiness, "readiness");
            Objects.requireNonNull(ai, "ai");
            Objects.requireNonNull(tours, "tours");
        }
    }

    private final Seams seams;
    /** Made with the first view's preferences; one for the whole run. */
    private StartupNotices notices;

    WhatsNewExtension(Seams seams) {
        this.seams = Objects.requireNonNull(seams, "seams");
    }

    @Override
    public Runnable install(EditorView view) {
        WhatsNewFeatures features = new WhatsNewFeatures(view, seams, noticesFor(view.services().preferences()));
        return features::dispose;
    }

    private StartupNotices noticesFor(AppPreferences preferences) {
        if (notices == null) {
            notices = new StartupNotices(preferences, seams.runningVersion(),
                    version -> seams.news().apply(version).isPresent(), seams.rule());
        }
        return notices;
    }
}
