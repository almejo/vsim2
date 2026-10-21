package cl.almejo.vsim.ui.window.panes;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.canvas.ViewState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.OptionalDouble;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;

class SplitLayoutStoreTest {

    private static final ViewState FIRST = new ViewState(2, 10, 20);
    private static final ViewState SECOND = new ViewState(0.5, -30, 40);
    private static final ViewState THIRD = new ViewState(1, 0, 0);
    private static final ViewState FOURTH = new ViewState(4, 100, -100);
    private static final PaneLayout FOUR_PANES = new PaneLayout(OptionalDouble.of(0.25), OptionalDouble.of(0.7),
            List.of(FIRST, SECOND, THIRD, FOURTH));
    private static final PaneLayout SIDE_BY_SIDE = new PaneLayout(OptionalDouble.empty(), OptionalDouble.of(0.4),
            List.of(FIRST, SECOND));

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final SplitLayoutStore store = new SplitLayoutStore(preferences);

    @TempDir
    Path folder;

    @Test
    void aRememberedLayoutComesBackWithItsDividersAndViews() {
        Path file = folder.resolve("contador.vsim");
        store.remember(file, FOUR_PANES);
        store.remember(folder.resolve("otro.vsim"), SIDE_BY_SIDE);

        assertThat(store.layoutFor(file)).contains(FOUR_PANES);
        assertThat(store.layoutFor(folder.resolve("otro.vsim"))).contains(SIDE_BY_SIDE);
        assertThat(store.layoutFor(folder.resolve("nunca.vsim"))).isEmpty();
    }

    @Test
    void theFileIsFoundInAnySpellingOfItsPath() {
        store.remember(folder.resolve("sub/../contador.vsim"), SIDE_BY_SIDE);

        assertThat(store.layoutFor(folder.resolve("contador.vsim"))).contains(SIDE_BY_SIDE);
    }

    @Test
    void rememberingOnePaneForgetsTheFile() {
        Path file = folder.resolve("contador.vsim");
        store.remember(file, FOUR_PANES);
        store.remember(file, PaneLayout.single(FIRST));

        assertThat(store.layoutFor(file)).isEmpty();
        assertThat(entries()).isEmpty();
    }

    @Test
    void rememberingAgainReplacesTheLayout() {
        Path file = folder.resolve("contador.vsim");
        store.remember(file, FOUR_PANES);
        store.remember(file, SIDE_BY_SIDE);

        assertThat(store.layoutFor(file)).contains(SIDE_BY_SIDE);
        assertThat(entries()).hasSize(1);
    }

    @Test
    void onlyTheTwentyMostRecentFilesAreKept() {
        for (int index = 0; index < SplitLayoutStore.CAPACITY + 1; index++) {
            store.remember(folder.resolve("circuito" + index + ".vsim"), SIDE_BY_SIDE);
        }
        assertThat(store.layoutFor(folder.resolve("circuito0.vsim"))).isEmpty();
        assertThat(store.layoutFor(folder.resolve("circuito1.vsim"))).contains(SIDE_BY_SIDE);
        assertThat(entries()).hasSize(SplitLayoutStore.CAPACITY);

        // remembering an old one again makes it the newest: the next new file drops circuito2, not circuito1
        store.remember(folder.resolve("circuito1.vsim"), FOUR_PANES);
        store.remember(folder.resolve("nuevo.vsim"), SIDE_BY_SIDE);
        assertThat(store.layoutFor(folder.resolve("circuito1.vsim"))).contains(FOUR_PANES);
        assertThat(store.layoutFor(folder.resolve("circuito2.vsim"))).isEmpty();
    }

    @Test
    void forgettingAFileRemovesOnlyIt() {
        store.remember(folder.resolve("a.vsim"), SIDE_BY_SIDE);
        store.remember(folder.resolve("b.vsim"), FOUR_PANES);

        store.forget(folder.resolve("a.vsim"));

        assertThat(store.layoutFor(folder.resolve("a.vsim"))).isEmpty();
        assertThat(store.layoutFor(folder.resolve("b.vsim"))).contains(FOUR_PANES);
    }

    @Test
    void malformedOrOutOfRangeEntriesAreIgnoredSilently() throws Exception {
        Path good = folder.resolve("bueno.vsim");
        store.remember(good, SIDE_BY_SIDE);
        Preferences node = preferences.splitLayoutsNode();
        writeEntry(node, "1", folder.resolve("numero.vsim"), "abc", null, 2);
        writeEntry(node, "2", folder.resolve("fuera.vsim"), "1.5", null, 2);
        writeEntry(node, "3", folder.resolve("cuenta.vsim"), "0.5", null, 4); // one divider, four panes
        writeEntry(node, "4", folder.resolve("faltan.vsim"), "0.5", "0.5", 2); // two dividers, two views
        writeEntry(node, "5", folder.resolve("zoom.vsim"), "0.5", null, 2);
        node.node("5").put("zoom.1", "-1");
        node.node("6").put("horizontal", "0.5"); // no path
        node.node("raro").put("path", folder.resolve("raro.vsim").toString());

        assertThat(store.layoutFor(good)).contains(SIDE_BY_SIDE);
        for (String name : List.of("numero", "fuera", "cuenta", "faltan", "zoom", "raro")) {
            assertThat(store.layoutFor(folder.resolve(name + ".vsim"))).as(name).isEmpty();
        }
        // a broken entry does not stop the store from writing the good ones again
        store.remember(folder.resolve("otro.vsim"), FOUR_PANES);
        assertThat(store.layoutFor(good)).contains(SIDE_BY_SIDE);
        assertThat(store.layoutFor(folder.resolve("otro.vsim"))).contains(FOUR_PANES);
    }

    @Test
    void theCircuitFileIsNeverTouched() {
        Path file = folder.resolve("contador.vsim");
        store.remember(file, FOUR_PANES);

        assertThat(file).doesNotExist();
    }

    private static void writeEntry(Preferences node, String name, Path path, String horizontal, String vertical,
                                   int views) {
        Preferences entry = node.node(name);
        entry.put("path", path.toString());
        if (horizontal != null) {
            entry.put("horizontal", horizontal);
        }
        if (vertical != null) {
            entry.put("vertical", vertical);
        }
        for (int index = 0; index < views; index++) {
            entry.put("zoom." + index, "1");
            entry.put("centerX." + index, "0");
            entry.put("centerY." + index, "0");
        }
    }

    private String[] entries() {
        try {
            return preferences.splitLayoutsNode().childrenNames();
        } catch (java.util.prefs.BackingStoreException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
