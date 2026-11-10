package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.app.AppPreferences;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * What one window may show when VSim starts, for the whole application run. Today only this release's «Qué hay de
 * nuevo» bubbles; the tip of the day will ask here too, and lose to the bubbles on the same start (the user,
 * 2026-11-09), never showing on a first launch ({@link #isFirstLaunch}).
 */
final class StartupNotices {

    /** What a window may show at the start. */
    enum Notice { NEWS_BUBBLES }

    /** The development switch's system property: «true» shows the bubbles at every launch. */
    static final String ALWAYS_PROPERTY = "vsim.whatsNew.always";

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

    private final AppPreferences preferences;
    private final String runningVersion;
    private final Predicate<String> hasNews;
    private final NewsRule rule;
    private final boolean firstLaunch;
    private boolean claimed;

    StartupNotices(AppPreferences preferences, String runningVersion, Predicate<String> hasNews, NewsRule rule) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.runningVersion = Objects.requireNonNull(runningVersion, "runningVersion");
        this.hasNews = Objects.requireNonNull(hasNews, "hasNews");
        this.rule = Objects.requireNonNull(rule, "rule");
        this.firstLaunch = preferences.newsShownVersion().isEmpty();
    }

    /** Whether VSim had never run before this start (nothing stored yet when it began). */
    boolean isFirstLaunch() {
        return firstLaunch;
    }

    /**
     * What the window asking may show: the first window of the run gets the bubbles when they are due, every later
     * window nothing. The bubbles are due when this version has news and the user has not seen this version's yet
     * (a first install too, the user 2026-11-10: «me gustaria en la primera instalacion»); an unreadable stored
     * version counts as an older one. When nothing is due the running version is stored at once, so the next start
     * knows VSim ran (the tip of the day's «never on a first launch»); claimed bubbles are stored only once shown, so
     * if the window closes first they come again at the next start.
     */
    Optional<Notice> claimForWindow() {
        if (claimed) {
            return Optional.empty();
        }
        claimed = true;
        Optional<ReleaseVersion> running = ReleaseVersion.parse(runningVersion);
        if (running.isEmpty()) {
            return Optional.empty(); // a build outside Gradle: no version to compare or to store
        }
        if (newsDue(running.get())) {
            return Optional.of(Notice.NEWS_BUBBLES);
        }
        if (isNewerThanStored(running.get())) {
            preferences.setNewsShownVersion(runningVersion);
        }
        return Optional.empty();
    }

    private boolean newsDue(ReleaseVersion running) {
        return (rule == NewsRule.EVERY_LAUNCH || isNewerThanStored(running)) && hasNews.test(runningVersion);
    }

    /** True on a first install, with an unreadable stored version, or when the stored one is older. */
    private boolean isNewerThanStored(ReleaseVersion running) {
        return preferences.newsShownVersion().flatMap(ReleaseVersion::parse)
                .map(stored -> stored.compareTo(running) < 0).orElse(true);
    }

    /**
     * {@code notice} was shown and the user is done with it: the bubbles of this version never come again (an older
     * VSim run after a newer one leaves the newer version stored).
     */
    void shown(Notice notice) {
        Objects.requireNonNull(notice, "notice");
        switch (notice) {
            case NEWS_BUBBLES -> ReleaseVersion.parse(runningVersion).filter(this::isNewerThanStored)
                    .ifPresent(running -> preferences.setNewsShownVersion(runningVersion));
        }
    }
}
