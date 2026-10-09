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
}
