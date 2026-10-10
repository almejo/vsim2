package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.canvas.ViewState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;

class SessionStoreTest {

    private static final WindowState LEFT = new WindowState(0, 0, 960, 1080, false, new ViewState(1.5, 10, -4));
    private static final WindowState RIGHT = new WindowState(960, 0, 960, 1080, true, new ViewState(2.0, 0, 0));

    @TempDir
    Path directory;

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final SessionStore store = new SessionStore(preferences);

    @Test
    void anEmptyProfileHasNoSession() {
        assertThat(store.load()).isEmpty();
    }

    @Test
    void aSessionRoundTripsWithEveryViewInOrder() {
        List<SessionDocument> session = List.of(
                new SessionDocument(directory.resolve("contador.vsim"), List.of(LEFT, RIGHT)),
                new SessionDocument(directory.resolve("sumador.vsim"), List.of(RIGHT)));
        store.save(session);
        assertThat(store.load()).isEqualTo(session);
    }

    @Test
    void savingReplacesThePreviousSessionCompletely() throws Exception {
        store.save(List.of(new SessionDocument(directory.resolve("a.vsim"), List.of(LEFT, RIGHT)),
                new SessionDocument(directory.resolve("b.vsim"), List.of(LEFT))));
        store.save(List.of(new SessionDocument(directory.resolve("c.vsim"), List.of(RIGHT))));
        assertThat(store.load()).containsExactly(new SessionDocument(directory.resolve("c.vsim"), List.of(RIGHT)));
        assertThat(preferences.sessionNode().childrenNames()).containsExactly("0");
        assertThat(preferences.sessionNode().node("0").childrenNames()).containsExactly("0");
    }

    @Test
    void thePathLivesOnTheDocumentNodeAndEachViewIsPlainKeys() throws Exception {
        store.save(List.of(new SessionDocument(directory.resolve("contador.vsim"), List.of(LEFT, RIGHT))));
        Preferences document = preferences.sessionNode().node("0");
        assertThat(document.get("path", null)).isEqualTo(directory.resolve("contador.vsim").toString());
        assertThat(document.node("1").keys()).containsExactlyInAnyOrder("x", "y", "width", "height", "maximized",
                "zoom", "centerX", "centerY");
        assertThat(document.node("1").get("maximized", null)).isEqualTo("true");
    }

    @Test
    void brokenEntriesAreSkipped() {
        Preferences session = preferences.sessionNode();
        session.node("0").node("0").put("x", "1"); // no path
        Preferences document = session.node("1");
        document.put("path", directory.resolve("contador.vsim").toString());
        document.node("0").put("zoom", "-1");
        SessionKeys.writeWindowState(document.node("1"), RIGHT);
        session.node("zz").put("path", directory.resolve("sumador.vsim").toString());

        assertThat(store.load()).containsExactly(new SessionDocument(directory.resolve("contador.vsim"), List.of(RIGHT)));
    }
}
