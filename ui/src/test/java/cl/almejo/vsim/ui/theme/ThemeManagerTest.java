package cl.almejo.vsim.ui.theme;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ThemeManagerTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final Appearance appearance = new Appearance(ColorSchemes.CLASSIC);
    private final AtomicBoolean systemDark = new AtomicBoolean();
    private final AtomicInteger probes = new AtomicInteger();
    private final List<Boolean> installs = new ArrayList<>();
    private final DarkModeProbe probe = () -> {
        probes.incrementAndGet();
        return systemDark.get();
    };
    private final ThemeManager manager = new ThemeManager(preferences, appearance, probe, installs::add);

    @Test
    void aLightSystemGivesTheLightThemeAndScheme() {
        manager.start();
        assertThat(installs).containsExactly(false);
        assertThat(manager.dark()).isFalse();
        assertThat(appearance.scheme()).isSameAs(ColorSchemes.LIGHT);
        assertThat(probes).hasValue(1);
    }

    @Test
    void aDarkSystemGivesTheDarkThemeAndScheme() {
        systemDark.set(true);
        manager.start();
        assertThat(installs).containsExactly(true);
        assertThat(manager.dark()).isTrue();
        assertThat(appearance.scheme()).isSameAs(ColorSchemes.DARK);
    }

    @Test
    void anExplicitThemeNeverAsksTheSystem() {
        systemDark.set(true);
        preferences.setTheme(AppPreferences.Theme.LIGHT);
        manager.start();
        assertThat(installs).containsExactly(false);
        assertThat(probes).hasValue(0);
    }

    @Test
    void switchingTheThemeReinstallsAndRecolorsLive() {
        manager.start();
        preferences.setTheme(AppPreferences.Theme.DARK);
        assertThat(installs).containsExactly(false, true);
        assertThat(appearance.scheme()).isSameAs(ColorSchemes.DARK);
        preferences.setTheme(AppPreferences.Theme.LIGHT);
        assertThat(installs).containsExactly(false, true, false);
        assertThat(appearance.scheme()).isSameAs(ColorSchemes.LIGHT);
    }

    @Test
    void aChosenSchemeWinsOverTheTheme() {
        preferences.setTheme(AppPreferences.Theme.DARK);
        manager.start();
        preferences.setColorScheme(Optional.of(ColorSchemes.CLASSIC_ID));
        assertThat(appearance.scheme()).isSameAs(ColorSchemes.CLASSIC);
        assertThat(installs).containsExactly(true);

        preferences.setColorScheme(Optional.empty());
        assertThat(appearance.scheme()).isSameAs(ColorSchemes.DARK);
    }

    @Test
    void anUnknownStoredSchemeFallsBackToTheTheme() {
        preferences.setTheme(AppPreferences.Theme.DARK);
        preferences.setColorScheme(Optional.of("neon"));
        manager.start();
        assertThat(appearance.scheme()).isSameAs(ColorSchemes.DARK);
        assertThat(ThemeManager.schemeFor(Optional.of("neon"), false)).isSameAs(ColorSchemes.LIGHT);
        assertThat(ThemeManager.schemeFor(Optional.of("classic"), true)).isSameAs(ColorSchemes.CLASSIC);
    }

    @Test
    void unrelatedPreferencesDoNotReinstallOrProbe() {
        manager.start();
        preferences.setLanguage(Optional.of("en"));
        preferences.setLibraryDirectory(Optional.of(Path.of("/data/components")));
        assertThat(installs).containsExactly(false);
        assertThat(probes).hasValue(1);
    }

    @Test
    void choosingTheSystemAgainAsksTheSystemAgain() {
        manager.start();
        preferences.setTheme(AppPreferences.Theme.LIGHT);
        systemDark.set(true);
        preferences.setTheme(AppPreferences.Theme.SYSTEM);
        assertThat(probes).hasValue(2);
        assertThat(installs).containsExactly(false, true);
        assertThat(appearance.scheme()).isSameAs(ColorSchemes.DARK);
    }

    @Test
    void startingTwiceInstallsOnceAndDisposeStopsFollowing() {
        manager.start();
        manager.start();
        assertThat(installs).containsExactly(false);
        assertThat(preferences.listenerCount()).isEqualTo(1);
        manager.dispose();
        assertThat(preferences.listenerCount()).isZero();
        preferences.setTheme(AppPreferences.Theme.DARK);
        assertThat(installs).containsExactly(false);
        assertThat(appearance.scheme()).isSameAs(ColorSchemes.LIGHT);
    }

    @Test
    void aFailedInstallIsRetriedOnTheNextChange() {
        AtomicBoolean installSucceeds = new AtomicBoolean(false);
        List<Boolean> attempts = new ArrayList<>();
        ThemeManager failing = new ThemeManager(preferences, appearance, probe, dark -> {
            attempts.add(dark);
            return installSucceeds.get();
        });
        failing.start();
        assertThat(attempts).containsExactly(false);
        assertThat(failing.dark()).isFalse();

        installSucceeds.set(true);
        preferences.setColorScheme(Optional.of("classic"));
        assertThat(attempts).containsExactly(false, false);

        preferences.setTheme(AppPreferences.Theme.DARK);
        assertThat(attempts).containsExactly(false, false, true);
        assertThat(failing.dark()).isTrue();
        failing.dispose();
    }

    @Test
    void aFailedInstallKeepsTheSchemeOfTheLookInPlace() {
        AtomicBoolean installSucceeds = new AtomicBoolean(true);
        ThemeManager failing = new ThemeManager(preferences, appearance, probe, dark -> installSucceeds.get());
        failing.start();
        assertThat(appearance.scheme()).isSameAs(ColorSchemes.LIGHT);

        installSucceeds.set(false);
        preferences.setTheme(AppPreferences.Theme.DARK);
        assertThat(failing.dark()).isFalse();
        assertThat(appearance.scheme()).isSameAs(ColorSchemes.LIGHT);
        failing.dispose();
    }
}
