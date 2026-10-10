package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.ui.app.AppPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * The circuits open when the application last ended (spec §4.1 «Continuar donde quedaste»), under
 * {@link AppPreferences#sessionNode()}: {@code session/<n>} holds {@code path}, {@code session/<n>/<m>} the state of
 * each view as plain keys. Broken entries are skipped; storage failures are logged, never thrown. EDT only.
 */
public final class SessionStore {

    static final String PATH = "path";
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionStore.class);

    private final AppPreferences preferences;

    public SessionStore(AppPreferences preferences) {
        this.preferences = preferences;
    }

    /** Replaces the stored session with {@code documents}. */
    public void save(List<SessionDocument> documents) {
        try {
            Preferences session = preferences.sessionNode();
            for (String name : session.childrenNames()) {
                session.node(name).removeNode();
            }
            for (int documentIndex = 0; documentIndex < documents.size(); documentIndex++) {
                SessionDocument document = documents.get(documentIndex);
                Preferences documentNode = session.node(Integer.toString(documentIndex));
                documentNode.put(PATH, document.path().toAbsolutePath().toString());
                for (int windowIndex = 0; windowIndex < document.windows().size(); windowIndex++) {
                    SessionKeys.writeWindowState(documentNode.node(Integer.toString(windowIndex)),
                            document.windows().get(windowIndex));
                }
            }
            session.flush();
        } catch (BackingStoreException | IllegalStateException exception) {
            LOGGER.warn("Could not save the session", exception);
        }
    }

    public List<SessionDocument> load() {
        List<SessionDocument> documents = new ArrayList<>();
        try {
            Preferences session = preferences.sessionNode();
            for (String documentName : SessionKeys.numbered(session.childrenNames())) {
                Preferences documentNode = session.node(documentName);
                String stored = documentNode.get(PATH, "");
                if (stored.isBlank()) {
                    continue;
                }
                Path path;
                try {
                    path = Path.of(stored);
                } catch (InvalidPathException exception) {
                    continue;
                }
                List<WindowState> windows = new ArrayList<>();
                for (String windowName : SessionKeys.numbered(documentNode.childrenNames())) {
                    SessionKeys.readWindowState(documentNode.node(windowName)).ifPresent(windows::add);
                }
                documents.add(new SessionDocument(path, windows));
            }
        } catch (BackingStoreException | IllegalStateException exception) {
            LOGGER.warn("Could not read the session", exception);
        }
        return documents;
    }
}
