package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.WindowMenu;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;

/**
 * Which windows hold a tab of which {@link OpenDocument} (spec §10.2), both in opening order. A window holds a document
 * while it has a group of tabs of it; a document lives while some window holds it: {@link #remove} says when its last
 * holder lets it go, so the caller can close it. Main tabs of a document across windows are numbered («contador (2)»,
 * {@link #viewNumber}); change listeners hear of every add and remove so titles follow. EDT only.
 *
 * @param <W> the window type ({@code CircuitWindow} in the application, plain values in tests)
 */
public final class DocumentWindows<W> {

    private final Map<OpenDocument, List<W>> windowsByDocument = new LinkedHashMap<>();
    private final Map<W, List<OpenDocument>> documentsByWindow = new LinkedHashMap<>();
    private final Listeners<Runnable> changeListeners = new Listeners<>();
    /** Windows in order of their last activation, the latest first; a window leaves it when it is forgotten. */
    private final List<W> recentlyActivated = new ArrayList<>();

    /**
     * {@code window} holds a tab of {@code document} now. Idempotent for a window that already holds it; the listeners
     * hear every call, since a tab added to a group may renumber the document's main tabs.
     */
    public void add(OpenDocument document, W window) {
        List<W> holders = windowsByDocument.computeIfAbsent(document, key -> new ArrayList<>());
        if (!holders.contains(window)) {
            holders.add(window);
            documentsByWindow.computeIfAbsent(window, key -> new ArrayList<>()).add(document);
        }
        changed();
    }

    /**
     * {@code window} no longer holds {@code document} (its group closed or moved away). True when no window holds the
     * document any more, so the caller closes it; false for a window that did not hold it.
     */
    public boolean remove(OpenDocument document, W window) {
        List<W> holders = windowsByDocument.get(document);
        if (holders == null || !holders.remove(window)) {
            return false;
        }
        List<OpenDocument> held = documentsByWindow.get(window);
        held.remove(document);
        if (held.isEmpty()) {
            documentsByWindow.remove(window);
        }
        boolean lastHolder = holders.isEmpty();
        if (lastHolder) {
            windowsByDocument.remove(document);
        }
        changed();
        return lastHolder;
    }

    /** Forgets {@code window}; returns the documents it was the last holder of, in its order. */
    public List<OpenDocument> removeWindow(W window) {
        recentlyActivated.remove(window);
        List<OpenDocument> released = new ArrayList<>();
        for (OpenDocument document : documentsOf(window)) {
            if (remove(document, window)) {
                released.add(document);
            }
        }
        return released;
    }

    /** True when {@code window} is the only window holding {@code document} (or no window holds it). */
    public boolean isLastHolder(OpenDocument document, W window) {
        List<W> holders = windowsByDocument.getOrDefault(document, List.of());
        return holders.isEmpty() || holders.equals(List.of(window));
    }

    public boolean isEmpty() {
        return windowsByDocument.isEmpty();
    }

    public List<OpenDocument> documents() {
        return List.copyOf(windowsByDocument.keySet());
    }

    public List<W> windowsOf(OpenDocument document) {
        return List.copyOf(windowsByDocument.getOrDefault(document, List.of()));
    }

    public List<OpenDocument> documentsOf(W window) {
        return List.copyOf(documentsByWindow.getOrDefault(window, List.of()));
    }

    /** Every window holding something, in order of their first tab. */
    public List<W> all() {
        return List.copyOf(documentsByWindow.keySet());
    }

    /**
     * The documents that something newly opened from {@code requester} replaces (Abrir, Ejemplos): the pristine
     * documents it holds, whose groups close; none for an unknown or null requester.
     */
    public List<OpenDocument> replacedBy(W requester) {
        if (requester == null) {
            return List.of();
        }
        return documentsOf(requester).stream().filter(OpenDocument::isPristine).toList();
    }

    /** The first window holding the document backed by {@code file}, if that file is already open. */
    public Optional<W> firstShowing(Path file) {
        return showing(file).map(document -> windowsByDocument.get(document).get(0));
    }

    /**
     * Where opening {@code file} goes: the window already showing it, else a new group in {@code requester}, else
     * (nothing asked: start-up, command line, the system's open-file events) a new group in the {@link #active}
     * window, and a new window only when there is none.
     */
    public OpeningTarget<W> targetFor(Path file, Optional<W> requester) {
        Optional<OpenDocument> open = showing(file);
        if (open.isPresent()) {
            return new OpeningTarget.FocusTab<>(windowsByDocument.get(open.get()).get(0), open.get());
        }
        return requester.or(this::active).<OpeningTarget<W>>map(OpeningTarget.NewGroup::new)
                .orElseGet(OpeningTarget.NewWindow::new);
    }

    /** {@code window} became the window the user works in (it was activated). */
    public void activated(W window) {
        recentlyActivated.remove(window);
        recentlyActivated.addFirst(window);
    }

    /**
     * The window the user works in: the last activated one still holding something, else the last opened (at start
     * no window was activated yet, and the last restored one is on top). Empty when no window holds anything.
     */
    public Optional<W> active() {
        return recentlyActivated.stream().filter(documentsByWindow::containsKey).findFirst()
                .or(() -> all().stream().reduce((earlier, later) -> later));
    }

    private Optional<OpenDocument> showing(Path file) {
        return windowsByDocument.keySet().stream().filter(document -> document.isBackedBy(file)).findFirst();
    }

    /**
     * The 1-based number of main tab {@code mainTabIndex} of {@code document} in {@code window}, counting the main tabs
     * of the windows before it ({@code mainTabCount}); 0 when the document has a single main tab in all: a lone view
     * carries no number.
     */
    public int viewNumber(OpenDocument document, W window, int mainTabIndex,
                          ToIntBiFunction<W, OpenDocument> mainTabCount) {
        int before = 0;
        int total = 0;
        boolean reached = false;
        for (W holder : windowsOf(document)) {
            int count = mainTabCount.applyAsInt(holder, document);
            if (holder.equals(window)) {
                reached = true;
            } else if (!reached) {
                before += count;
            }
            total += count;
        }
        return reached && total > 1 ? before + mainTabIndex + 1 : 0;
    }

    /** «contador (2)» for view 2, the plain name for 0. */
    public static String viewTitle(String displayName, int viewNumber) {
        return viewNumber > 0 ? Messages.format("window.view", displayName, viewNumber) : displayName;
    }

    /**
     * The Ventana list and the «▾» list (spec §4.2, §4.8): the tabs of every window ({@code tabsOf}), each window
     * under a «Ventana n» header when there are several.
     */
    public List<WindowMenu.Entry> menuEntries(Function<W, List<WindowMenu.Entry>> tabsOf) {
        List<W> windows = all();
        if (windows.size() == 1) {
            return List.copyOf(tabsOf.apply(windows.get(0)));
        }
        List<WindowMenu.Entry> entries = new ArrayList<>();
        for (int index = 0; index < windows.size(); index++) {
            entries.add(WindowMenu.Entry.header(Messages.format("window.list.header", index + 1)));
            entries.addAll(tabsOf.apply(windows.get(index)));
        }
        return entries;
    }

    /** Runs {@code listener} after every {@link #add} and every {@link #remove} that changed something. */
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
        changeListeners.fire(Runnable::run);
    }
}
