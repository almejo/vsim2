package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.FileNames;
import cl.almejo.vsim.ui.canvas.ViewState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;

class RecentFilesTest {

    private static final WindowState STATE = new WindowState(100, 50, 1280, 820, false, new ViewState(1.5, 10, -4));
    private static final WindowState MOVED = new WindowState(300, 200, 800, 600, true, new ViewState(2, 0, 0));

    @TempDir
    Path directory;

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final RecentFiles recentFiles = new RecentFiles(preferences);

    private Path create(String name) throws IOException {
        return FileNames.normalized(Files.createFile(directory.resolve(name + ".vsim")));
    }

    @Test
    void theNewestComesFirstAndAFileIsListedOnceInAnySpelling() throws IOException {
        Path first = create("a");
        Path second = create("b");
        Files.createDirectories(directory.resolve("sub"));
        recentFiles.remember(first, STATE);
        recentFiles.touch(second);
        recentFiles.touch(directory.resolve("sub").resolve("..").resolve("a.vsim"));

        assertThat(recentFiles.entries()).containsExactly(first, second);
        assertThat(recentFiles.stateFor(first)).as("opening keeps the stored state").contains(STATE);
        assertThat(recentFiles.stateFor(second)).isEmpty();
    }

    @Test
    void onlyTheTenNewestAreKept() throws Exception {
        for (int index = 0; index < 12; index++) {
            recentFiles.remember(create("c" + index), STATE);
        }
        assertThat(recentFiles.entries()).hasSize(RecentFiles.CAPACITY);
        assertThat(recentFiles.entries().get(0).getFileName().toString()).isEqualTo("c11.vsim");
        assertThat(recentFiles.entries().get(9).getFileName().toString()).isEqualTo("c2.vsim");
        assertThat(preferences.recentNode().childrenNames()).hasSize(RecentFiles.CAPACITY);
    }

    @Test
    void updatingAStateKeepsThePlaceAndAnUnknownFileIsIgnored() throws IOException {
        Path first = create("a");
        Path second = create("b");
        recentFiles.remember(first, STATE);
        recentFiles.remember(second, STATE);

        recentFiles.updateState(first, MOVED);
        assertThat(recentFiles.entries()).containsExactly(second, first);
        assertThat(recentFiles.stateFor(first)).contains(MOVED);

        recentFiles.updateState(create("other"), MOVED);
        assertThat(recentFiles.entries()).containsExactly(second, first);
    }

    @Test
    void removeAndClear() throws IOException {
        Path first = create("a");
        Path second = create("b");
        recentFiles.remember(first, STATE);
        recentFiles.remember(second, STATE);
        recentFiles.remove(first);
        assertThat(recentFiles.entries()).containsExactly(second);
        recentFiles.clear();
        assertThat(recentFiles.entries()).isEmpty();
    }

    @Test
    void theStateIsStoredAsPlainKeys() throws Exception {
        Path first = create("a");
        recentFiles.remember(first, STATE);
        Preferences entry = preferences.recentNode().node("0");
        assertThat(entry.keys()).containsExactlyInAnyOrder("path", "x", "y", "width", "height", "maximized", "zoom",
                "centerX", "centerY");
        assertThat(entry.get("path", null)).isEqualTo(first.toString());
        assertThat(entry.get("zoom", null)).isEqualTo("1.5");
        assertThat(entry.get("maximized", null)).isEqualTo("false");
    }

    @Test
    void aBrokenEntryIsSkippedAndABrokenStateMeansNoState() throws IOException {
        Path first = create("a");
        Path second = create("b");
        Path third = create("c");
        Preferences recent = preferences.recentNode();
        recent.node("0").put("path", "");
        recent.node("1").put("path", first.toString());
        recent.node("1").put("x", "10");
        recent.node("1").put("zoom", "NaN");
        recent.node("x").put("path", second.toString());
        Preferences valid = recent.node("2");
        valid.put("path", third.toString());
        valid.put("x", "1");
        valid.put("y", "2");
        valid.put("width", "300");
        valid.put("height", "200");
        valid.put("maximized", "true");
        valid.put("zoom", "1.0");
        valid.put("centerX", "0.5");
        valid.put("centerY", "-2");

        assertThat(recentFiles.entries()).containsExactly(first, third);
        assertThat(recentFiles.stateFor(first)).isEmpty();
        assertThat(recentFiles.stateFor(third)).contains(new WindowState(1, 2, 300, 200, true, new ViewState(1.0, 0.5, -2)));
    }

    @Test
    void aRootPathIsSkippedLikeABrokenEntry() throws IOException {
        Path file = create("a");
        Path root = directory.getRoot();
        preferences.recentNode().node("0").put("path", root.toString());
        preferences.recentNode().node("1").put("path", file.toString());
        assertThat(recentFiles.entries()).containsExactly(file);
    }

    @Test
    void aPathTooLongForThePreferencesIsSkippedAndTheListIsKept() throws IOException {
        Path kept = create("kept");
        recentFiles.remember(kept, STATE);
        Path tooLong = Path.of("/" + "a".repeat(Preferences.MAX_VALUE_LENGTH) + ".vsim");
        org.assertj.core.api.Assertions.assertThatCode(() -> recentFiles.touch(tooLong)).doesNotThrowAnyException();
        assertThat(recentFiles.entries()).containsExactly(kept);
        assertThat(recentFiles.stateFor(kept)).contains(STATE);
    }
}
