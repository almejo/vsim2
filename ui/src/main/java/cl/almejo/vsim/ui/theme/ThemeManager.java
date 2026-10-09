package cl.almejo.vsim.ui.theme;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;

import java.time.Duration;
import java.util.Optional;

/**
 * Applies the theme and color scheme preferences (spec §6.7): installs the look and feel and sets the shared
 * {@link Appearance}, at start and whenever a preference changes. The system appearance is asked at start and each
 * time the user chooses "Seguir sistema". EDT only.
 */
public final class ThemeManager {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);

    private final AppPreferences preferences;
    private final Appearance appearance;
    private final DarkModeProbe probe;
    private final LookAndFeelInstaller installer;
    private final Runnable preferencesListener = this::apply;

    private boolean started;
    private AppPreferences.Theme appliedTheme;
    private boolean systemDark;
    private boolean installed;
    private boolean installedDark;

    public ThemeManager(AppPreferences preferences, Appearance appearance, DarkModeProbe probe,
                        LookAndFeelInstaller installer) {
        this.preferences = preferences;
        this.appearance = appearance;
        this.probe = probe;
        this.installer = installer;
    }

    /** FlatLaf and the platform dark-mode checks of the running system. */
    public static ThemeManager forCurrentPlatform(AppPreferences preferences, Appearance appearance) {
        String osName = System.getProperty("os.name", "");
        return new ThemeManager(preferences, appearance,
                new PlatformDarkModeProbe(osName, new ProcessCommandRunner(PROBE_TIMEOUT)),
                new FlatLafInstaller(osName));
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

    private void apply() {
        AppPreferences.Theme theme = preferences.theme();
        if (theme != appliedTheme) {
            appliedTheme = theme;
            if (theme == AppPreferences.Theme.SYSTEM) {
                systemDark = probe.prefersDark();
            }
        }
        boolean dark = switch (theme) {
            case LIGHT -> false;
            case DARK -> true;
            case SYSTEM -> systemDark;
        };
        if (!installed || dark != installedDark) {
            installer.install(dark);
            installed = true;
            installedDark = dark;
        }
        appearance.setScheme(schemeFor(preferences.colorScheme(), dark));
    }
}
