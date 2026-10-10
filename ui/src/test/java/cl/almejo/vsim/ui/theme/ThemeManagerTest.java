package cl.almejo.vsim.ui.theme;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.UiTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    @AfterEach
    void clearTheStartupAnswer() {
        ThemeManager.takeStartupSystemDark();
    }

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
    void everyStoredSchemeIdComesBackAsItsScheme() {
        Map<String, ColorScheme> expected = Map.of("classic", ColorSchemes.CLASSIC, "light", ColorSchemes.LIGHT,
                "dark", ColorSchemes.DARK, "pastel", ColorSchemes.PASTEL, "csim", ColorSchemes.CSIM,
                "twilight", ColorSchemes.TWILIGHT);
        for (Map.Entry<String, ColorScheme> entry : expected.entrySet()) {
            preferences.setColorScheme(Optional.of(entry.getKey()));
            assertThat(ThemeManager.schemeFor(preferences.colorScheme(), false)).isSameAs(entry.getValue());
            assertThat(ThemeManager.schemeFor(preferences.colorScheme(), true)).isSameAs(entry.getValue());
        }
        preferences.setColorScheme(Optional.of("Twilight"));
        assertThat(ThemeManager.schemeFor(preferences.colorScheme(), false)).isSameAs(ColorSchemes.LIGHT);
        assertThat(ThemeManager.schemeFor(preferences.colorScheme(), true)).isSameAs(ColorSchemes.DARK);
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

    @Test
    void choosingTheSystemAsksItOffTheEventDispatchThreadAndAppliesTheAnswerOnIt() throws Exception {
        List<Boolean> probedOnEdt = new CopyOnWriteArrayList<>();
        List<Boolean> installedOnEdt = new CopyOnWriteArrayList<>();
        List<Boolean> installed = new CopyOnWriteArrayList<>();
        DarkModeProbe recording = () -> {
            probedOnEdt.add(SwingUtilities.isEventDispatchThread());
            return true;
        };
        ExecutorService background = Executors.newSingleThreadExecutor();
        ThemeManager offTheEdt = new ThemeManager(preferences, appearance, recording, dark -> {
            installedOnEdt.add(SwingUtilities.isEventDispatchThread());
            installed.add(dark);
            return true;
        }, Optional.of(false), background, SwingUtilities::invokeLater);
        try {
            SwingUtilities.invokeAndWait(offTheEdt::start); // the answer asked before start: no probe at all
            assertThat(probedOnEdt).isEmpty();
            assertThat(installed).containsExactly(false);

            SwingUtilities.invokeAndWait(() -> preferences.setTheme(AppPreferences.Theme.LIGHT));
            SwingUtilities.invokeAndWait(() -> preferences.setTheme(AppPreferences.Theme.SYSTEM));
            UiTestSupport.waitUntil(() -> installed.size() == 2);

            assertThat(probedOnEdt).containsExactly(false);
            assertThat(installed).containsExactly(false, true);
            assertThat(installedOnEdt).containsOnly(true);
            SwingUtilities.invokeAndWait(() -> assertThat(appearance.scheme()).isSameAs(ColorSchemes.DARK));
        } finally {
            SwingUtilities.invokeAndWait(offTheEdt::dispose);
            background.shutdownNow();
        }
    }

    @Test
    void anAnswerAskedBeforeStartServesTheFirstFollowSystemOnly() {
        ThemeManager prepared = new ThemeManager(preferences, appearance, probe, installs::add, Optional.of(true),
                Runnable::run, Runnable::run);
        prepared.start();
        assertThat(probes).hasValue(0);
        assertThat(installs).containsExactly(true);

        preferences.setTheme(AppPreferences.Theme.LIGHT);
        preferences.setTheme(AppPreferences.Theme.SYSTEM);

        assertThat(probes).hasValue(1);
        assertThat(installs).containsExactly(true, false);
        prepared.dispose();
    }

    @Test
    void anAnswerThatArrivesAfterLeavingFollowSystemChangesNothing() {
        List<Runnable> questions = new ArrayList<>();
        ThemeManager queued = new ThemeManager(preferences, appearance, probe, installs::add, Optional.of(false),
                questions::add, Runnable::run);
        queued.start();
        preferences.setTheme(AppPreferences.Theme.DARK);
        preferences.setTheme(AppPreferences.Theme.SYSTEM); // asked in the background: the dark look stays meanwhile
        assertThat(installs).containsExactly(false, true);

        preferences.setTheme(AppPreferences.Theme.LIGHT);
        systemDark.set(true);
        questions.forEach(Runnable::run); // the late answer arrives

        assertThat(probes).hasValue(1);
        assertThat(installs).containsExactly(false, true, false);
        assertThat(appearance.scheme()).isSameAs(ColorSchemes.LIGHT);
        queued.dispose();
    }

    @Test
    void mainAsksBeforeStartOnlyWhenTheThemeFollowsTheSystemAndTheAnswerIsTakenOnce() {
        AtomicInteger asked = new AtomicInteger();
        DarkModeProbe dark = () -> {
            asked.incrementAndGet();
            return true;
        };
        ThemeManager.askSystemBeforeStart(preferences, dark);
        assertThat(asked).hasValue(1);
        assertThat(ThemeManager.takeStartupSystemDark()).contains(true);
        assertThat(ThemeManager.takeStartupSystemDark()).isEmpty();

        preferences.setTheme(AppPreferences.Theme.LIGHT);
        ThemeManager.askSystemBeforeStart(preferences, dark);
        assertThat(asked).hasValue(1);
        assertThat(ThemeManager.takeStartupSystemDark()).isEmpty();

        preferences.setTheme(AppPreferences.Theme.SYSTEM);
        ThemeManager.askSystemBeforeStart(preferences, () -> {
            throw new IllegalStateException("no answer");
        });
        assertThat(ThemeManager.takeStartupSystemDark()).contains(false);
    }
}
