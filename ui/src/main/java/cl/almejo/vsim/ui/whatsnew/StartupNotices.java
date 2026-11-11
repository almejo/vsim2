package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.app.AppPreferences;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * What one window may show when VSim starts, for the whole application run: this release's «Qué hay de nuevo» bubbles
 * or, when they are not due, the tip of the day. Never both on the same start: the bubbles win (the user,
 * 2026-11-09); the tip never shows on a first launch ({@link #isFirstLaunch}) and at most once a calendar day.
 */
final class StartupNotices {

    /** What a window may show at the start. */
    enum Notice { NEWS_BUBBLES, TIP_OF_THE_DAY }

    /** The development switch's system property: «true» shows the bubbles at every launch. */
    static final String ALWAYS_PROPERTY = "vsim.whatsNew.always";

    /** The tip's development switch: «true» shows the tip of the day at every launch. */
    static final String TIP_ALWAYS_PROPERTY = "vsim.tip.always";

    /**
     * When the bubbles come: once per version, or, with the development switch {@value #ALWAYS_PROPERTY} set to
     * «true» (Gradle's run task with -PvsimWhatsNewAlways), at every launch, to review them (the user, 2026-11-10:
     * «por ahora, deben abrir siempre para que las podamos ver y corregir»). The shipped jar never sets it.
     */
    enum NewsRule {
        ONCE_PER_VERSION, EVERY_LAUNCH;

        /** The rule {@code propertyValue} (the switch's system property, null when unset) asks for. */
        static NewsRule of(String propertyValue) {
            return Boolean.parseBoolean(propertyValue) ? EVERY_LAUNCH : ONCE_PER_VERSION;
        }

        /** The rule of this run, from the system property. */
        static NewsRule current() {
            return of(System.getProperty(ALWAYS_PROPERTY));
        }
    }

    /**
     * When the tip of the day comes by itself: at most once a calendar day and never on a first launch, or, with the
     * development switch {@value #TIP_ALWAYS_PROPERTY} set to «true» (Gradle's run task with -PvsimTipAlways), at
     * every launch, a first one included, to review the tips. «Mostrar al iniciar» off still keeps it away, and the
     * bubbles still win. The shipped jar never sets it.
     */
    enum TipRule {
        ONCE_PER_DAY, EVERY_LAUNCH;

        /** The rule {@code propertyValue} (the switch's system property, null when unset) asks for. */
        static TipRule of(String propertyValue) {
            return Boolean.parseBoolean(propertyValue) ? EVERY_LAUNCH : ONCE_PER_DAY;
        }

        /** The rule of this run, from the system property. */
        static TipRule current() {
            return of(System.getProperty(TIP_ALWAYS_PROPERTY));
        }
    }

    private final AppPreferences preferences;
    private final String runningVersion;
    private final Predicate<String> hasNews;
    private final NewsRule rule;
    private final TipRule tipRule;
    private final Supplier<LocalDate> today;
    private final boolean firstLaunch;
    /** The window that asked first, and what it got; null until a window asks. */
    private Object claimingWindow;
    private Optional<Notice> claimed = Optional.empty();

    /**
     * @param today the local calendar day now (a fixed one in tests)
     */
    StartupNotices(AppPreferences preferences, String runningVersion, Predicate<String> hasNews, NewsRule rule,
                   TipRule tipRule, Supplier<LocalDate> today) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.runningVersion = Objects.requireNonNull(runningVersion, "runningVersion");
        this.hasNews = Objects.requireNonNull(hasNews, "hasNews");
        this.rule = Objects.requireNonNull(rule, "rule");
        this.tipRule = Objects.requireNonNull(tipRule, "tipRule");
        this.today = Objects.requireNonNull(today, "today");
        this.firstLaunch = preferences.newsShownVersion().isEmpty();
    }

    /** Whether VSim had never run before this start (nothing stored yet when it began). */
    boolean isFirstLaunch() {
        return firstLaunch;
    }

    /** {@link #claimForWindow(Object)} for a window that asks only once. */
    Optional<Notice> claimForWindow() {
        return claimForWindow(new Object());
    }

    /**
     * What {@code window} may show: the first window that asks gets the bubbles when they are due, else the tip when
     * it is due, and gets the same answer every time it asks again (the bubbles and the tip ask separately); every
     * other window gets nothing. The bubbles are due when this version has news the user has not seen yet (a first
     * install too, the user 2026-11-10: «me gustaria en la primera instalacion»); an unreadable stored version counts
     * as an older one. When no bubbles are due the running version is stored at once, so the next start knows VSim
     * ran (the tip's «never on a first launch»); claimed bubbles and tips are stored only once shown, so if the window
     * closes first they come again at the next start.
     */
    Optional<Notice> claimForWindow(Object window) {
        Objects.requireNonNull(window, "window");
        if (claimingWindow != null) {
            return claimingWindow == window ? claimed : Optional.empty();
        }
        claimingWindow = window;
        claimed = decide();
        return claimed;
    }

    private Optional<Notice> decide() {
        Optional<ReleaseVersion> running = ReleaseVersion.parse(runningVersion);
        if (running.isPresent() && newsDue(running.get())) {
            return Optional.of(Notice.NEWS_BUBBLES);
        }
        if (running.isPresent() && isNewerThanStored(running.get())) {
            preferences.setNewsShownVersion(runningVersion);
        }
        return tipDue() ? Optional.of(Notice.TIP_OF_THE_DAY) : Optional.empty();
    }

    private boolean newsDue(ReleaseVersion running) {
        return (rule == NewsRule.EVERY_LAUNCH || isNewerThanStored(running)) && hasNews.test(runningVersion);
    }

    /** «Mostrar al iniciar» on and, but with the development switch, not a first launch and not shown today. */
    private boolean tipDue() {
        if (!preferences.showTipAtStart()) {
            return false;
        }
        if (tipRule == TipRule.EVERY_LAUNCH) {
            return true;
        }
        return !firstLaunch && !preferences.tipShownDay().filter(today.get()::equals).isPresent();
    }

    /** True on a first install, with an unreadable stored version, or when the stored one is older. */
    private boolean isNewerThanStored(ReleaseVersion running) {
        return preferences.newsShownVersion().flatMap(ReleaseVersion::parse)
                .map(stored -> stored.compareTo(running) < 0).orElse(true);
    }

    /**
     * {@code notice} was shown: the bubbles of this version never come again (once the user is done with them; an
     * older VSim run after a newer one leaves the newer version stored), and the tip not again today in both cases.
     */
    void shown(Notice notice) {
        Objects.requireNonNull(notice, "notice");
        switch (notice) {
            case NEWS_BUBBLES -> {
                ReleaseVersion.parse(runningVersion).filter(this::isNewerThanStored)
                        .ifPresent(running -> preferences.setNewsShownVersion(runningVersion));
                preferences.setTipShownDay(today.get()); // never both the same day (mockup: the bubbles win)
            }
            case TIP_OF_THE_DAY -> preferences.setTipShownDay(today.get());
        }
    }
}
