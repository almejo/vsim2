package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.window.InspectorPanel;
import cl.almejo.vsim.ui.window.InspectorState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** The last session's windows and tabs in the preferences (spec §4.7, §10.6); old files still load. */
class SessionStoreTest {

    private static final ViewState LEFT_VIEW = new ViewState(1.5, 10, -4);
    private static final ViewState RIGHT_VIEW = new ViewState(2.0, 0, 0);
    private static final ViewState INSIDE_VIEW = new ViewState(3.0, -20, 40);
    private static final WindowState LEFT = new WindowState(0, 0, 960, 1080, false, LEFT_VIEW);
    private static final WindowState RIGHT = new WindowState(960, 0, 960, 1080, true, RIGHT_VIEW);

    @TempDir
    Path directory;

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final SessionStore store = new SessionStore(preferences);

    private Path file(String name) {
        return directory.resolve(name);
    }

    /** One window with one main tab of {@code name}, active. */
    private WindowSession single(String name, WindowState bounds) {
        return new WindowSession(bounds, List.of(TabState.main(file(name), bounds.view())), 0, List.of());
    }

    @Test
    void anEmptyProfileHasNoSession() {
        assertThat(store.load()).isEmpty();
    }

    @Test
    void savesAndLoadsAWindowWithTwoGroupsAndAComponentTab() {
        WindowSession window = new WindowSession(new WindowState(0, 0, 960, 1080, false, INSIDE_VIEW), List.of(
                TabState.main(file("contador.vsim"), LEFT_VIEW),
                TabState.component(file("contador.vsim"), "ram", "Ram", Optional.of("c5/c2"), INSIDE_VIEW),
                TabState.component(file("contador.vsim"), "hoja", "Hoja", Optional.empty(), LEFT_VIEW),
                TabState.main(file("sumador.vsim"), RIGHT_VIEW)), 1, List.of());
        List<WindowSession> session = List.of(window, single("sumador.vsim", RIGHT));

        store.save(session);

        assertThat(store.load()).isEqualTo(session);
    }

    @Test
    void remembersFoldedDocumentsAndTheActiveTab() {
        WindowSession window = new WindowSession(new WindowState(0, 0, 960, 1080, false, RIGHT_VIEW), List.of(
                TabState.main(file("contador.vsim"), LEFT_VIEW),
                TabState.component(file("contador.vsim"), "ram", "Ram", Optional.of("c5"), INSIDE_VIEW),
                TabState.main(file("sumador.vsim"), RIGHT_VIEW)), 2, List.of(file("contador.vsim")));

        store.save(List.of(window));

        WindowSession loaded = store.load().getFirst();
        assertThat(loaded.activeTab()).isEqualTo(2);
        assertThat(loaded.foldedDocuments()).containsExactly(file("contador.vsim"));
        assertThat(loaded.bounds().view()).isEqualTo(RIGHT_VIEW);
    }

    @Test
    void foldedPanelsAreSavedInTheSession() throws Exception {
        WindowSession folded = new WindowSession(RIGHT, List.of(TabState.main(file("contador.vsim"), RIGHT_VIEW)),
                0, List.of(), java.util.Set.of("circuit"));

        store.save(List.of(folded, single("sumador.vsim", LEFT)));

        List<WindowSession> loaded = store.load();
        assertThat(loaded.getFirst().foldedPanels()).containsExactly("circuit");
        assertThat(loaded.get(1).foldedPanels()).as("both panels open").isEmpty();
        assertThat(preferences.sessionNode().node("0").get("foldedPanels", null)).isEqualTo("circuit");
    }

    @Test
    void aSessionWithoutFoldedPanelsLoadsWithBothOpen() throws Exception {
        store.save(List.of(single("contador.vsim", RIGHT)));
        preferences.sessionNode().node("0").remove("foldedPanels"); // written before the circuit tree

        assertThat(store.load().getFirst().foldedPanels()).isEmpty();
    }

    @Test
    void theRightPanelIsSavedPerWindowAndAnOldSessionHasNone() throws Exception {
        WindowSession first = new WindowSession(RIGHT, List.of(TabState.main(file("contador.vsim"), RIGHT_VIEW)), 0,
                List.of(), java.util.Set.of(), Optional.of(new InspectorState(false, InspectorPanel.NOTES, 410)));
        WindowSession second = new WindowSession(LEFT, List.of(TabState.main(file("sumador.vsim"), LEFT_VIEW)), 0,
                List.of(), java.util.Set.of(), Optional.of(new InspectorState(true, InspectorPanel.PROBLEMS, 300)));

        store.save(List.of(first, second, single("viejo.vsim", RIGHT)));

        List<WindowSession> loaded = store.load();
        assertThat(loaded.get(0).inspector()).contains(new InspectorState(false, InspectorPanel.NOTES, 410));
        assertThat(loaded.get(1).inspector()).contains(new InspectorState(true, InspectorPanel.PROBLEMS, 300));
        assertThat(loaded.get(2).inspector()).as("written without a panel").isEmpty();
        Preferences node = preferences.sessionNode().node("0");
        assertThat(node.get("inspectorOpen", null)).isEqualTo("false");
        assertThat(node.get("inspectorTab", null)).isEqualTo("notes");
        assertThat(node.get("inspectorWidth", null)).isEqualTo("410");
    }

    @Test
    void anUnknownPanelSectionOpensOnPropertiesAndTheWidthIsClamped() throws Exception {
        store.save(List.of(single("contador.vsim", RIGHT)));
        Preferences node = preferences.sessionNode().node("0");
        node.put("inspectorOpen", "true");
        node.put("inspectorTab", "nonsense");
        node.put("inspectorWidth", "3");
        assertThat(store.load().getFirst().inspector().orElseThrow().selectedTab()).isEqualTo(InspectorPanel.PROPERTIES);
        assertThat(store.load().getFirst().inspector().orElseThrow().width()).isGreaterThan(100);

        node.put("inspectorWidth", "999999");
        assertThat(store.load().getFirst().inspector().orElseThrow().width()).isLessThan(5000);

        node.put("inspectorWidth", "wide");
        node.put("inspectorOpen", "maybe");
        InspectorState broken = store.load().getFirst().inspector().orElseThrow();
        assertThat(broken.width()).isEqualTo(InspectorState.initial().width());
        assertThat(broken.open()).isEqualTo(InspectorState.initial().open());
    }

    @Test
    void eachWindowIsPlainKeysAndEachTabAChildNode() throws Exception {
        store.save(List.of(new WindowSession(RIGHT, List.of(TabState.main(file("contador.vsim"), RIGHT_VIEW),
                TabState.component(file("contador.vsim"), "ram", "Ram", Optional.of("c5"), INSIDE_VIEW)), 0,
                List.of(file("contador.vsim")))));

        Preferences window = preferences.sessionNode().node("0");
        assertThat(window.keys()).containsExactlyInAnyOrder("x", "y", "width", "height", "maximized", "active",
                "folded0");
        assertThat(window.childrenNames()).containsExactlyInAnyOrder("tab0", "tab1");
        assertThat(window.node("tab0").keys()).containsExactlyInAnyOrder("path", "zoom", "centerX", "centerY");
        assertThat(window.node("tab1").keys()).containsExactlyInAnyOrder("path", "definition", "name", "context",
                "zoom", "centerX", "centerY");
        assertThat(window.node("tab1").get("context", null)).isEqualTo("c5");
        assertThat(window.get("maximized", null)).isEqualTo("true");
    }

    @Test
    void loadsAnOldSessionFileAsOneMainTabPerWindow() {
        // The shape before tabs: session/<n> holds a file's path, session/<n>/<m> the state of each of its windows.
        Preferences session = preferences.sessionNode();
        Preferences counter = session.node("0");
        counter.put("path", file("contador.vsim").toString());
        SessionKeys.writeWindowState(counter.node("0"), LEFT);
        SessionKeys.writeWindowState(counter.node("1"), RIGHT);
        Preferences adder = session.node("1");
        adder.put("path", file("sumador.vsim").toString());
        SessionKeys.writeWindowState(adder.node("0"), RIGHT);

        assertThat(store.load()).containsExactly(single("contador.vsim", LEFT), single("contador.vsim", RIGHT),
                single("sumador.vsim", RIGHT));
    }

    @Test
    void savingReplacesThePreviousSessionCompletely() throws Exception {
        Preferences old = preferences.sessionNode().node("4");
        old.put("path", file("viejo.vsim").toString());
        SessionKeys.writeWindowState(old.node("0"), LEFT);
        store.save(List.of(single("a.vsim", LEFT), single("b.vsim", RIGHT)));
        store.save(List.of(single("c.vsim", RIGHT)));

        assertThat(store.load()).containsExactly(single("c.vsim", RIGHT));
        assertThat(preferences.sessionNode().childrenNames()).containsExactly("0");
    }

    @Test
    void brokenEntriesAreSkipped() {
        Preferences session = preferences.sessionNode();
        session.node("0").put("x", "1"); // no bounds, no tabs
        Preferences window = session.node("1");
        window.putInt("x", 960);
        window.putInt("y", 0);
        window.putInt("width", 960);
        window.putInt("height", 1080);
        window.putBoolean("maximized", true);
        window.node("tab0").put("zoom", "2"); // no path
        Preferences tab = window.node("tab1");
        tab.put("path", file("contador.vsim").toString());
        tab.putDouble("zoom", 2.0);
        tab.putDouble("centerX", 0);
        tab.putDouble("centerY", 0);
        window.node("tabzz").put("path", file("sumador.vsim").toString());
        session.node("zz").put("path", file("sumador.vsim").toString());

        assertThat(store.load()).containsExactly(single("contador.vsim", RIGHT));
    }

    @Test
    void theActiveTabIsTheSameOneWhenABrokenTabBeforeItIsSkipped() {
        Preferences window = preferences.sessionNode().node("0");
        window.putInt("x", 0);
        window.putInt("y", 0);
        window.putInt("width", 960);
        window.putInt("height", 1080);
        window.putBoolean("maximized", false);
        window.putInt("active", 2);
        window.node("tab0").put("zoom", "2"); // no path: skipped
        for (String name : List.of("contador.vsim", "sumador.vsim")) {
            Preferences tab = window.node("tab" + (name.startsWith("c") ? 1 : 2));
            tab.put("path", file(name).toString());
            tab.putDouble("zoom", 2.0);
            tab.putDouble("centerX", 0);
            tab.putDouble("centerY", 0);
        }

        WindowSession loaded = store.load().getFirst();

        assertThat(loaded.tabs()).extracting(TabState::path).containsExactly(file("contador.vsim"),
                file("sumador.vsim"));
        assertThat(loaded.activeTab()).isEqualTo(1);
    }

    @Test
    void aPathTooLongForThePreferencesIsSkippedAndTheOthersAreSaved() {
        Path tooLong = Path.of("/" + "a".repeat(Preferences.MAX_VALUE_LENGTH) + ".vsim");
        store.save(List.of(single("kept.vsim", LEFT)));
        WindowSession mixed = new WindowSession(RIGHT, List.of(TabState.main(tooLong, LEFT_VIEW),
                TabState.main(file("kept.vsim"), RIGHT_VIEW)), 1, List.of());

        assertThatCode(() -> store.save(List.of(mixed, new WindowSession(LEFT,
                List.of(TabState.main(tooLong, LEFT_VIEW)), 0, List.of())))).doesNotThrowAnyException();

        assertThat(store.load()).containsExactly(single("kept.vsim", RIGHT));
    }
}
