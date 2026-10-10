package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.WindowMenu;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Open windows grouped by the {@link OpenDocument} they show, in opening order. A document lives while it has a
 * window: {@link #remove} hands it back when its last window goes, so the caller can close it. A document with more
 * than one window numbers its views («contador (2)», {@link #viewNumber}), renumbered when one closes; change
 * listeners hear of every add and remove so titles follow. EDT only.
 *
 * @param <W> the window type ({@code CircuitWindow} in the application, plain values in tests)
 */
public final class DocumentWindows<W> {

    private final Map<OpenDocument, List<W>> windowsByDocument = new LinkedHashMap<>();
    private final Map<W, OpenDocument> documentByWindow = new HashMap<>();
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    public void add(OpenDocument document, W window) {
        if (documentByWindow.putIfAbsent(window, document) != null) {
            throw new IllegalArgumentException("window already registered: " + window);
        }
        windowsByDocument.computeIfAbsent(document, key -> new ArrayList<>()).add(window);
        changed();
    }

    /** Forgets {@code window}; returns its document when that was the document's last window. */
    public Optional<OpenDocument> remove(W window) {
        OpenDocument document = documentByWindow.remove(window);
        if (document == null) {
            return Optional.empty();
        }
        List<W> group = windowsByDocument.get(document);
        group.remove(window);
        boolean lastWindow = group.isEmpty();
        if (lastWindow) {
            windowsByDocument.remove(document);
        }
        changed();
        return lastWindow ? Optional.of(document) : Optional.empty();
    }

    /** True when closing {@code window} would leave its document without windows (or the window is unknown). */
    public boolean isLastWindow(W window) {
        OpenDocument document = documentByWindow.get(window);
        return document == null || windowsByDocument.get(document).size() == 1;
    }

    public boolean isEmpty() {
        return documentByWindow.isEmpty();
    }

    public List<OpenDocument> documents() {
        return List.copyOf(windowsByDocument.keySet());
    }

    public List<W> windowsOf(OpenDocument document) {
        return List.copyOf(windowsByDocument.getOrDefault(document, List.of()));
    }

    public List<W> all() {
        List<W> result = new ArrayList<>();
        windowsByDocument.values().forEach(result::addAll);
        return result;
    }

    /** The first window of the document backed by {@code file}, if that file is already open. */
    public Optional<W> firstShowing(Path file) {
        for (Map.Entry<OpenDocument, List<W>> group : windowsByDocument.entrySet()) {
            if (group.getKey().isBackedBy(file)) {
                return Optional.of(group.getValue().get(0));
            }
        }
        return Optional.empty();
    }

    /**
     * The 1-based position of {@code window} among its document's windows, or 0 when the document has a single
     * window (or the window is unknown): a lone view carries no number.
     */
    public int viewNumber(W window) {
        OpenDocument document = documentByWindow.get(window);
        if (document == null) {
            return 0;
        }
        List<W> group = windowsByDocument.get(document);
        return group.size() > 1 ? group.indexOf(window) + 1 : 0;
    }

    /** «contador (2)» for view 2, the plain name for 0. */
    public static String viewTitle(String displayName, int viewNumber) {
        return viewNumber > 0 ? Messages.format("window.view", displayName, viewNumber) : displayName;
    }

    /** One entry per window, «nombre (n)» when its document has several; choosing it calls {@code focus}. */
    public List<WindowMenu.Entry> menuEntries(W current, Consumer<W> focus) {
        List<WindowMenu.Entry> entries = new ArrayList<>();
        windowsByDocument.forEach((document, group) -> {
            for (int index = 0; index < group.size(); index++) {
                W window = group.get(index);
                int viewNumber = group.size() > 1 ? index + 1 : 0;
                entries.add(new WindowMenu.Entry(viewTitle(document.displayName(), viewNumber),
                        window.equals(current), () -> focus.accept(window)));
            }
        });
        return entries;
    }

    /** Runs {@code listener} after every {@link #add} and every {@link #remove} of a known window. */
    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    /** For tests: windows must remove their listener when they close. */
    public int changeListenerCount() {
        return changeListeners.size();
    }

    private void changed() {
        changeListeners.forEach(Runnable::run);
    }
}
