package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.ui.app.AppPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/**
 * The circuit windows open when the application last ended (spec §4.1 «Continuar donde quedaste», §4.7), under
 * {@link AppPreferences#sessionNode()}: {@code session/<n>} is a window, with its bounds ({@code x y width height
 * maximized}), {@code active} (the index of its active tab), {@code folded<k>} (the files whose groups were folded)
 * and {@code foldedPanels} (the left column's folded panels, {@code circuit,palette}; absent: both open) as plain
 * keys, and a child {@code tab<k>} per tab with {@code path}, {@code definition} and {@code name} (a
 * component tab's), {@code context} (the instance it showed) and {@code zoom centerX centerY}. A session written
 * before tabs ({@code session/<n>} holding a file's {@code path}, {@code session/<n>/<m>} the state of each of its
 * windows) still loads, as one window with one main tab per state. Broken entries are skipped; storage failures are
 * logged, never thrown. EDT only.
 */
public final class SessionStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionStore.class);
    private static final String PANEL_SEPARATOR = ",";

    private final AppPreferences preferences;

    public SessionStore(AppPreferences preferences) {
        this.preferences = preferences;
    }

    /** Replaces the stored session with {@code allWindows}; a tab whose path does not fit is left out. */
    public void save(List<WindowSession> allWindows) {
        List<WindowSession> windows = allWindows.stream()
                .flatMap(window -> window.keeping(SessionStore::fits).stream()).toList();
        try {
            Preferences session = preferences.sessionNode();
            SessionKeys.removeChildren(session);
            for (int windowIndex = 0; windowIndex < windows.size(); windowIndex++) {
                write(session.node(Integer.toString(windowIndex)), windows.get(windowIndex));
            }
            session.flush();
        } catch (BackingStoreException | RuntimeException exception) {
            LOGGER.warn("Could not save the session", exception);
        }
    }

    private static boolean fits(Path path) {
        boolean fits = SessionKeys.fitsInPreferences(path.toAbsolutePath());
        if (!fits) {
            LOGGER.warn("Not remembering a path of {} characters in the session",
                    path.toAbsolutePath().toString().length());
        }
        return fits;
    }

    private static void write(Preferences node, WindowSession window) {
        SessionKeys.writeBounds(node, window.bounds());
        node.putInt(SessionKeys.ACTIVE, window.activeTab());
        for (int tabIndex = 0; tabIndex < window.tabs().size(); tabIndex++) {
            SessionKeys.writeTab(node.node(SessionKeys.TAB + tabIndex), window.tabs().get(tabIndex));
        }
        for (int foldedIndex = 0; foldedIndex < window.foldedDocuments().size(); foldedIndex++) {
            node.put(SessionKeys.FOLDED + foldedIndex,
                    window.foldedDocuments().get(foldedIndex).toAbsolutePath().toString());
        }
        if (!window.foldedPanels().isEmpty()) {
            node.put(SessionKeys.FOLDED_PANELS, String.join(PANEL_SEPARATOR, new TreeSet<>(window.foldedPanels())));
        }
    }

    public List<WindowSession> load() {
        List<WindowSession> windows = new ArrayList<>();
        try {
            Preferences session = preferences.sessionNode();
            for (String name : SessionKeys.numbered(session.childrenNames())) {
                Preferences node = session.node(name);
                if (node.get(SessionKeys.PATH, null) != null) {
                    windows.addAll(readBeforeTabs(node));
                } else {
                    read(node).ifPresent(windows::add);
                }
            }
        } catch (BackingStoreException | RuntimeException exception) {
            LOGGER.warn("Could not read the session", exception);
        }
        return windows;
    }

    /** A window of the session; empty when it has no readable tab or no bounds. */
    private static Optional<WindowSession> read(Preferences node) throws BackingStoreException {
        List<TabState> tabs = new ArrayList<>();
        for (String tabName : SessionKeys.numbered(node.childrenNames(), SessionKeys.TAB)) {
            SessionKeys.readTab(node.node(tabName)).ifPresent(tabs::add);
        }
        if (tabs.isEmpty()) {
            return Optional.empty();
        }
        int active = node.getInt(SessionKeys.ACTIVE, 0);
        TabState activeTab = tabs.get(active >= 0 && active < tabs.size() ? active : 0);
        List<Path> folded = new ArrayList<>();
        for (String key : SessionKeys.numbered(node.keys(), SessionKeys.FOLDED)) {
            SessionKeys.readPath(node, key).ifPresent(folded::add);
        }
        Set<String> foldedPanels = Arrays.stream(node.get(SessionKeys.FOLDED_PANELS, "").split(PANEL_SEPARATOR))
                .filter(name -> !name.isBlank()).collect(Collectors.toSet());
        return SessionKeys.readBounds(node, activeTab.view())
                .map(bounds -> new WindowSession(bounds, tabs, active, folded, foldedPanels));
    }

    /** A file of a session written before tabs: one window with one main tab for each of its window states. */
    private static List<WindowSession> readBeforeTabs(Preferences node) throws BackingStoreException {
        Optional<Path> path = SessionKeys.readPath(node);
        if (path.isEmpty()) {
            return List.of();
        }
        List<WindowSession> windows = new ArrayList<>();
        for (String windowName : SessionKeys.numbered(node.childrenNames())) {
            SessionKeys.readWindowState(node.node(windowName)).ifPresent(state -> windows.add(
                    new WindowSession(state, List.of(TabState.main(path.get(), state.view())), 0, List.of())));
        }
        return windows;
    }
}
