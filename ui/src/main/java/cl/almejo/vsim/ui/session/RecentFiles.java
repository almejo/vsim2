package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.FileNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Archivo ▸ Recientes (spec §4.1, §4.2): the circuits opened or saved last, newest first, at most {@link #CAPACITY},
 * one per file in any spelling ({@link FileNames#normalized}), each with the window state it had. Stored as plain
 * keys under {@link AppPreferences#recentNode()}; a broken entry is skipped and a storage failure is logged, never
 * thrown. Stateless: every call reads the preferences, so all windows see the same list. EDT only.
 */
public final class RecentFiles {

    public static final int CAPACITY = 10;
    static final String PATH = "path";
    private static final Logger LOGGER = LoggerFactory.getLogger(RecentFiles.class);

    private record Entry(Path path, Optional<WindowState> state) {
    }

    private final AppPreferences preferences;

    public RecentFiles(AppPreferences preferences) {
        this.preferences = preferences;
    }

    public List<Path> entries() {
        return read().stream().map(Entry::path).toList();
    }

    public Optional<WindowState> stateFor(Path path) {
        Path normalized = FileNames.normalized(path);
        return read().stream().filter(entry -> entry.path().equals(normalized)).findFirst().flatMap(Entry::state);
    }

    /** Puts {@code path} first with {@code state} (it was just saved). */
    public void remember(Path path, WindowState state) {
        putFirst(FileNames.normalized(path), Optional.of(state));
    }

    /** Puts {@code path} first and keeps the state it had (it was just opened). */
    public void touch(Path path) {
        putFirst(FileNames.normalized(path), stateFor(path));
    }

    /** Replaces the state of {@code path} where it is, without moving it; nothing when it is not listed. */
    public void updateState(Path path, WindowState state) {
        Path normalized = FileNames.normalized(path);
        List<Entry> entries = new ArrayList<>(read());
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).path().equals(normalized)) {
                entries.set(index, new Entry(normalized, Optional.of(state)));
                write(entries);
                return;
            }
        }
    }

    public void remove(Path path) {
        Path normalized = FileNames.normalized(path);
        List<Entry> entries = new ArrayList<>(read());
        if (entries.removeIf(entry -> entry.path().equals(normalized))) {
            write(entries);
        }
    }

    public void clear() {
        write(List.of());
    }

    private void putFirst(Path normalized, Optional<WindowState> state) {
        List<Entry> entries = new ArrayList<>(read());
        entries.removeIf(entry -> entry.path().equals(normalized));
        entries.add(0, new Entry(normalized, state));
        write(entries);
    }

    private List<Entry> read() {
        List<Entry> entries = new ArrayList<>();
        try {
            Preferences recentNode = preferences.recentNode();
            for (String name : SessionKeys.numbered(recentNode.childrenNames())) {
                Preferences entryNode = recentNode.node(name);
                String stored = entryNode.get(PATH, "");
                if (stored.isBlank() || entries.size() == CAPACITY) {
                    continue;
                }
                Path path;
                try {
                    path = FileNames.normalized(Path.of(stored));
                } catch (InvalidPathException exception) {
                    continue;
                }
                if (entries.stream().noneMatch(entry -> entry.path().equals(path))) {
                    entries.add(new Entry(path, SessionKeys.readWindowState(entryNode)));
                }
            }
        } catch (BackingStoreException | IllegalStateException exception) {
            LOGGER.warn("Could not read the recent files", exception);
        }
        return entries;
    }

    private void write(List<Entry> entries) {
        try {
            Preferences recentNode = preferences.recentNode();
            for (String name : recentNode.childrenNames()) {
                recentNode.node(name).removeNode();
            }
            for (int index = 0; index < Math.min(entries.size(), CAPACITY); index++) {
                Entry entry = entries.get(index);
                Preferences entryNode = recentNode.node(Integer.toString(index));
                entryNode.put(PATH, entry.path().toString());
                entry.state().ifPresent(state -> SessionKeys.writeWindowState(entryNode, state));
            }
            recentNode.flush();
        } catch (BackingStoreException | IllegalStateException exception) {
            LOGGER.warn("Could not save the recent files", exception);
        }
    }
}
