package cl.almejo.vsim.ui.app;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.AbstractPreferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppPreferencesTest {

    private final MemoryPreferences node = new MemoryPreferences();
    private final AppPreferences preferences = new AppPreferences(node);
    private final AtomicInteger changes = new AtomicInteger();
    private final Path home = Path.of("/home/ana");

    @Test
    void defaultsFollowTheSystem() {
        assertThat(preferences.theme()).isEqualTo(AppPreferences.Theme.SYSTEM);
        assertThat(preferences.colorScheme()).isEmpty();
        assertThat(preferences.language()).isEmpty();
        assertThat(preferences.usesDefaultLibraryDirectory()).isTrue();
    }

    @Test
    void aStoredLibraryDirectoryThatIsNotAPathFallsBackToTheDefault() {
        AppPreferences unparseable = new AppPreferences(new FixedPreferences(
                Map.of(AppPreferences.LIBRARY_DIRECTORY, "components\u0000broken")));
        AppPreferences unset = new AppPreferences(new MemoryPreferences());
        assertThat(unparseable.libraryDirectory()).isEqualTo(unset.libraryDirectory());
    }

    @Test
    void restoringTheSessionIsOnByDefaultAndOnlyRealChangesNotify() {
        preferences.addListener(changes::incrementAndGet);
        assertThat(preferences.restoreSession()).isTrue();
        preferences.setRestoreSession(true);
        assertThat(changes).hasValue(0);

        preferences.setRestoreSession(false);
        assertThat(preferences.restoreSession()).isFalse();
        assertThat(node.get("restoreSession", null)).isEqualTo("false");
        assertThat(changes).hasValue(1);

        preferences.setRestoreSession(true);
        assertThat(preferences.restoreSession()).isTrue();
        assertThat(changes).hasValue(2);
    }

    @Test
    void theStepSizeIsTenByDefaultAndTakesOneToABillion() {
        preferences.addListener(changes::incrementAndGet);
        assertThat(preferences.stepUnits()).isEqualTo(AppPreferences.DEFAULT_STEP_UNITS).isEqualTo(10);

        preferences.setStepUnits(1_000);
        assertThat(preferences.stepUnits()).isEqualTo(1_000);
        assertThat(node.get("simulation.stepUnits", null)).isEqualTo("1000");
        assertThat(changes).hasValue(1);
        preferences.setStepUnits(1_000);
        assertThat(changes).as("only real changes notify").hasValue(1);

        assertThatThrownBy(() -> preferences.setStepUnits(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> preferences.setStepUnits(AppPreferences.MAXIMUM_STEP_UNITS + 1))
                .isInstanceOf(IllegalArgumentException.class);
        node.put("simulation.stepUnits", "many");
        assertThat(preferences.stepUnits()).as("unreadable").isEqualTo(10);
        node.put("simulation.stepUnits", "0");
        assertThat(preferences.stepUnits()).as("out of range").isEqualTo(10);
    }

    @Test
    void theDataDirectoryIsTheParentOfTheDefaultLibraryOnEveryPlatform() {
        assertThat(AppPreferences.dataDirectory("Linux", Map.of(), home)).isEqualTo(home.resolve(".local/share/vsim2"));
        assertThat(AppPreferences.dataDirectory("Linux", Map.of("XDG_DATA_HOME", "/data"), home))
                .isEqualTo(Path.of("/data/vsim2"));
        assertThat(AppPreferences.dataDirectory("Mac OS X", Map.of(), home))
                .isEqualTo(home.resolve("Library/Application Support/VSim2"));
        assertThat(AppPreferences.dataDirectory("Windows 11", Map.of("APPDATA", "/appdata"), home))
                .isEqualTo(Path.of("/appdata/VSim2"));
    }

    @Test
    void theDataDirectoryIgnoresACustomLibraryFolder() {
        Path before = preferences.dataDirectory();
        preferences.setLibraryDirectory(Optional.of(Path.of("/elsewhere/components")));
        assertThat(preferences.dataDirectory()).isEqualTo(before);
        assertThat(before).isEqualTo(AppPreferences.dataDirectory(System.getProperty("os.name", ""), System.getenv(),
                Path.of(System.getProperty("user.home"))));
    }

    @Test
    void recentFilesAndTheSessionLiveInChildNodes() throws Exception {
        preferences.recentNode().node("0").put("path", "/a.vsim");
        preferences.sessionNode().node("0").node("0").put("x", "1");
        assertThat(node.childrenNames()).contains("recent", "session");
        assertThat(node.node("session").node("0").node("0").get("x", null)).isEqualTo("1");
    }

    @Test
    void settersRejectNull() {
        preferences.addListener(changes::incrementAndGet);
        assertThatThrownBy(() -> preferences.setTheme(null)).isInstanceOf(NullPointerException.class)
                .hasMessage("theme");
        assertThatThrownBy(() -> preferences.setColorScheme(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> preferences.setLanguage(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> preferences.setLibraryDirectory(null)).isInstanceOf(NullPointerException.class);
        assertThat(preferences.theme()).isEqualTo(AppPreferences.Theme.SYSTEM);
        assertThat(changes).hasValue(0);
    }

    /** Read-only values that {@link java.util.prefs.Preferences#put} would refuse to store (it rejects U+0000). */
    private static final class FixedPreferences extends AbstractPreferences {
        private final Map<String, String> values;

        FixedPreferences(Map<String, String> values) {
            super(null, "");
            this.values = values;
        }

        @Override protected void putSpi(String key, String value) { throw new UnsupportedOperationException(); }
        @Override protected String getSpi(String key) { return values.get(key); }
        @Override protected void removeSpi(String key) { throw new UnsupportedOperationException(); }
        @Override protected void removeNodeSpi() { throw new UnsupportedOperationException(); }
        @Override protected String[] keysSpi() { return values.keySet().toArray(String[]::new); }
        @Override protected String[] childrenNamesSpi() { return new String[0]; }
        @Override protected AbstractPreferences childSpi(String name) { throw new UnsupportedOperationException(); }
        @Override protected void syncSpi() { }
        @Override protected void flushSpi() { }
    }

    @Test
    void removedListenersAreCountedOutAndHearNothing() {
        Runnable listener = changes::incrementAndGet;
        preferences.addListener(listener);
        assertThat(preferences.listenerCount()).isEqualTo(1);
        preferences.removeListener(listener);
        assertThat(preferences.listenerCount()).isZero();
        preferences.setTheme(AppPreferences.Theme.DARK);
        assertThat(changes).hasValue(0);
    }

    @Test
    void aFailingListenerDoesNotKeepTheOthersFromHearingTheChange() {
        preferences.addListener(() -> {
            throw new IllegalStateException("broken listener");
        });
        preferences.addListener(changes::incrementAndGet);

        preferences.setTheme(AppPreferences.Theme.DARK);

        assertThat(changes).hasValue(1);
        assertThat(new AppPreferences(node).theme()).isEqualTo(AppPreferences.Theme.DARK);
    }

    @Test
    void valuesAreStoredAndListenersHearOnlyRealChanges() {
        preferences.addListener(changes::incrementAndGet);
        preferences.setTheme(AppPreferences.Theme.DARK);
        preferences.setTheme(AppPreferences.Theme.DARK);
        preferences.setColorScheme(Optional.of("classic"));
        preferences.setLanguage(Optional.of("en"));
        preferences.setLibraryDirectory(Optional.of(Path.of("/data/components")));

        AppPreferences reread = new AppPreferences(node);
        assertThat(reread.theme()).isEqualTo(AppPreferences.Theme.DARK);
        assertThat(reread.colorScheme()).contains("classic");
        assertThat(reread.language()).contains("en");
        assertThat(reread.libraryDirectory()).isEqualTo(Path.of("/data/components"));
        assertThat(reread.usesDefaultLibraryDirectory()).isFalse();
        assertThat(changes).hasValue(4);

        preferences.setColorScheme(Optional.empty());
        preferences.setLibraryDirectory(Optional.empty());
        assertThat(preferences.colorScheme()).isEmpty();
        assertThat(preferences.usesDefaultLibraryDirectory()).isTrue();
        assertThat(changes).hasValue(6);
    }

    @Test
    void settingTheCurrentThemeStoresNothingAndNotifiesNoListener() {
        preferences.addListener(changes::incrementAndGet);
        preferences.setTheme(AppPreferences.Theme.SYSTEM);
        assertThat(node.get(AppPreferences.THEME, null)).isNull();
        assertThat(changes).hasValue(0);
    }

    @Test
    void anUnknownStoredThemeFallsBackToTheSystemOne() {
        node.put(AppPreferences.THEME, "NEON");
        assertThat(preferences.theme()).isEqualTo(AppPreferences.Theme.SYSTEM);
    }

    @Test
    void defaultLibraryDirectoryFollowsEachPlatform() {
        assertThat(AppPreferences.defaultLibraryDirectory("Linux", Map.of(), home))
                .isEqualTo(Path.of("/home/ana/.local/share/vsim2/components"));
        assertThat(AppPreferences.defaultLibraryDirectory("Linux", Map.of("XDG_DATA_HOME", "/xdg"), home))
                .isEqualTo(Path.of("/xdg/vsim2/components"));
        assertThat(AppPreferences.defaultLibraryDirectory("Mac OS X", Map.of(), home))
                .isEqualTo(Path.of("/home/ana/Library/Application Support/VSim2/components"));
        assertThat(AppPreferences.defaultLibraryDirectory("Windows 11", Map.of("APPDATA", "/appdata"), home))
                .isEqualTo(Path.of("/appdata/VSim2/components"));
        assertThat(AppPreferences.defaultLibraryDirectory("Windows 11", Map.of(), home))
                .isEqualTo(Path.of("/home/ana/AppData/Roaming/VSim2/components"));
        assertThat(AppPreferences.defaultLibraryDirectory("Darwin", Map.of("APPDATA", "/appdata"), home))
                .isEqualTo(Path.of("/home/ana/.local/share/vsim2/components"));
    }

    @Test
    void shortcutOverridesLiveInTheirOwnNodeAndAChangeNotifiesOnce() throws Exception {
        preferences.addListener(changes::incrementAndGet);
        preferences.shortcutsNode().put("file.save", "ctrl pressed G");
        assertThat(changes).hasValue(0);
        preferences.shortcutsChanged();
        assertThat(changes).hasValue(1);
        assertThat(node.nodeExists("shortcuts")).isTrue();
        assertThat(node.node("shortcuts").get("file.save", null)).isEqualTo("ctrl pressed G");
        assertThat(preferences.theme()).isEqualTo(AppPreferences.Theme.SYSTEM);
    }

    @Test
    void wiresFollowIsOnAndTheInvertKeyIsShiftByDefaultAndBothRoundTripAndNotify() {
        preferences.addListener(changes::incrementAndGet);
        assertThat(preferences.wiresFollow()).isTrue();
        assertThat(preferences.invertModifier()).isEqualTo(AppPreferences.InvertModifier.SHIFT);

        preferences.setWiresFollow(false);
        preferences.setWiresFollow(false);
        preferences.setInvertModifier(AppPreferences.InvertModifier.ALT);
        assertThat(changes).hasValue(2);
        assertThat(new AppPreferences(node).wiresFollow()).isFalse();
        assertThat(new AppPreferences(node).invertModifier()).isEqualTo(AppPreferences.InvertModifier.ALT);

        preferences.setInvertModifier(AppPreferences.InvertModifier.NONE);
        assertThat(preferences.invertModifier()).isEqualTo(AppPreferences.InvertModifier.NONE);
    }

    @Test
    void anUnknownStoredInvertKeyFallsBackToShift() {
        node.put("invertModifier", "HYPER");
        assertThat(preferences.invertModifier()).isEqualTo(AppPreferences.InvertModifier.SHIFT);
    }

    @Test
    void theInvertKeyIsHeldOnlyWhenItsOwnKeyIsDown() {
        AppPreferences.InvertModifier shift = AppPreferences.InvertModifier.SHIFT;
        AppPreferences.InvertModifier alt = AppPreferences.InvertModifier.ALT;
        AppPreferences.InvertModifier none = AppPreferences.InvertModifier.NONE;
        assertThat(shift.isHeld(true, false)).isTrue();
        assertThat(shift.isHeld(false, true)).isFalse();
        assertThat(shift.isHeld(false, false)).isFalse();
        assertThat(alt.isHeld(false, true)).isTrue();
        assertThat(alt.isHeld(true, false)).isFalse();
        assertThat(none.isHeld(true, true)).isFalse();
    }
}
