package cl.almejo.vsim.ui.theme;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Applies the theme and color scheme preferences (spec §6.7): installs the look and feel and sets the shared
 * {@link Appearance}, at start and whenever a preference changes. Asking the system whether it is dark can take up to
 * 2 s, so it never happens on the EDT: {@code Main} asks before the EDT starts ({@link #askSystemBeforeStart}) and
 * that answer serves the first "Seguir sistema"; choosing "Seguir sistema" later asks on {@code probeExecutor} and
 * applies the answer through {@code uiExecutor}, keeping the current look meanwhile. Everything else runs on the EDT.
 */
public final class ThemeManager {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);
    private static final Logger LOGGER = LoggerFactory.getLogger(ThemeManager.class);

    /** What {@link #askSystemBeforeStart} found; taken once by {@link #forCurrentPlatform}. */
    private static volatile Optional<Boolean> startupSystemDark = Optional.empty();

    private final AppPreferences preferences;
    private final Appearance appearance;
    private final DarkModeProbe probe;
    private final LookAndFeelInstaller installer;
    private final Executor probeExecutor;
    private final Executor uiExecutor;
    private final Runnable preferencesListener = this::apply;

    private Optional<Boolean> knownSystemDark;
    private boolean started;
    private boolean applying;
    private AppPreferences.Theme appliedTheme;
    private boolean systemDark;
    private boolean systemKnown;
    private int probeRequest;
    private boolean installed;
    private boolean installedDark;

    /** Asks the system on the calling thread, at once (tests). */
    public ThemeManager(AppPreferences preferences, Appearance appearance, DarkModeProbe probe,
                        LookAndFeelInstaller installer) {
        this(preferences, appearance, probe, installer, Optional.empty(), Runnable::run, Runnable::run);
    }

    /**
     * @param startupSystemDark the system appearance asked before start; it answers the first "Seguir sistema"
     * @param probeExecutor     where later questions to the system run (never the EDT)
     * @param uiExecutor        where their answers are applied (the EDT)
     */
    public ThemeManager(AppPreferences preferences, Appearance appearance, DarkModeProbe probe,
                        LookAndFeelInstaller installer, Optional<Boolean> startupSystemDark, Executor probeExecutor,
                        Executor uiExecutor) {
        this.preferences = preferences;
        this.appearance = appearance;
        this.probe = probe;
        this.installer = installer;
        this.knownSystemDark = startupSystemDark;
        this.probeExecutor = probeExecutor;
        this.uiExecutor = uiExecutor;
    }

    /** FlatLaf and the platform dark-mode checks of the running system, with the answer {@code Main} asked for. */
    public static ThemeManager forCurrentPlatform(AppPreferences preferences, Appearance appearance) {
        String osName = System.getProperty("os.name", "");
        Executor probeExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread worker = new Thread(runnable, "vsim-dark-mode-probe");
            worker.setDaemon(true);
            return worker;
        });
        return new ThemeManager(preferences, appearance, platformProbe(osName), new FlatLafInstaller(osName),
                takeStartupSystemDark(), probeExecutor, SwingUtilities::invokeLater);
    }

    /**
     * Called by {@code Main} on the main thread before the EDT starts: when the theme follows the system, asks it now
     * so the first window never waits for the answer.
     */
    public static void askSystemBeforeStart(AppPreferences preferences) {
        askSystemBeforeStart(preferences, platformProbe(System.getProperty("os.name", "")));
    }

    static void askSystemBeforeStart(AppPreferences preferences, DarkModeProbe probe) {
        startupSystemDark = preferences.theme() == AppPreferences.Theme.SYSTEM
                ? Optional.of(safelyAsk(probe)) : Optional.empty();
    }

    /** The answer {@link #askSystemBeforeStart} found, once; empty afterwards. */
    static Optional<Boolean> takeStartupSystemDark() {
        Optional<Boolean> answer = startupSystemDark;
        startupSystemDark = Optional.empty();
        return answer;
    }

    /** The user's scheme when it exists, otherwise the one that goes with the theme. */
    public static ColorScheme schemeFor(Optional<String> chosenId, boolean dark) {
        return chosenId.flatMap(ColorSchemes::byId).orElseGet(() -> ColorSchemes.forTheme(dark));
    }

    /** Installs the theme and scheme now and follows preference changes from then on. Idempotent. */
    public void start() {
        if (started) {
            return;
        }
        started = true;
        apply();
        preferences.addListener(preferencesListener);
    }

    public void dispose() {
        preferences.removeListener(preferencesListener);
        started = false;
    }

    /** Whether the installed look and feel is the dark one. */
    public boolean dark() {
        return installedDark;
    }

    private static DarkModeProbe platformProbe(String osName) {
        return new PlatformDarkModeProbe(osName, new ProcessCommandRunner(PROBE_TIMEOUT));
    }

    /** The system's answer; light when asking fails, as when the platform cannot tell. */
    private static boolean safelyAsk(DarkModeProbe probe) {
        try {
            return probe.prefersDark();
        } catch (RuntimeException exception) {
            LOGGER.warn("Cannot tell whether the system is dark; using the light theme", exception);
            return false;
        }
    }

    private void apply() {
        applying = true;
        try {
            AppPreferences.Theme theme = preferences.theme();
            if (theme != appliedTheme) {
                appliedTheme = theme;
                if (theme == AppPreferences.Theme.SYSTEM) {
                    askSystem();
                }
            }
            boolean dark = switch (theme) {
                case LIGHT -> false;
                case DARK -> true;
                case SYSTEM -> systemKnown ? systemDark : installedDark;
            };
            install(dark);
        } finally {
            applying = false;
        }
    }

    private void install(boolean dark) {
        if (!installed || dark != installedDark) {
            if (installer.install(dark)) {
                installed = true;
                installedDark = dark;
            }
        }
        appearance.setScheme(schemeFor(preferences.colorScheme(), installedDark));
    }

    /** Uses the answer asked before start once; afterwards asks on the probe executor. */
    private void askSystem() {
        if (knownSystemDark.isPresent()) {
            systemDark = knownSystemDark.get();
            systemKnown = true;
            knownSystemDark = Optional.empty();
            return;
        }
        systemKnown = false;
        int request = ++probeRequest;
        probeExecutor.execute(() -> {
            boolean answer = safelyAsk(probe);
            uiExecutor.execute(() -> systemAnswered(request, answer));
        });
    }

    /** On the UI executor. A stale answer (a newer question is pending) or one after leaving "Seguir sistema" waits. */
    private void systemAnswered(int request, boolean answer) {
        if (request != probeRequest) {
            return;
        }
        systemDark = answer;
        systemKnown = true;
        if (started && !applying && appliedTheme == AppPreferences.Theme.SYSTEM) {
            install(answer);
        }
    }
}
