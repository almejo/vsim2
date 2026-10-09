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
import java.util.function.Consumer;

/**
 * Open windows grouped by the {@link OpenDocument} they show, in opening order. A document lives while it has a
 * window: {@link #remove} hands it back when its last window goes, so the caller can close it. EDT only.
 *
 * @param <W> the window type ({@code CircuitWindow} in the application, plain values in tests)
 */
public final class DocumentWindows<W> {

    private final Map<OpenDocument, List<W>> windowsByDocument = new LinkedHashMap<>();
    private final Map<W, OpenDocument> documentByWindow = new HashMap<>();

    public void add(OpenDocument document, W window) {
        if (documentByWindow.putIfAbsent(window, document) != null) {
            throw new IllegalArgumentException("window already registered: " + window);
        }
        windowsByDocument.computeIfAbsent(document, key -> new ArrayList<>()).add(window);
    }

    /** Forgets {@code window}; returns its document when that was the document's last window. */
    public Optional<OpenDocument> remove(W window) {
        OpenDocument document = documentByWindow.remove(window);
        if (document == null) {
            return Optional.empty();
        }
        List<W> group = windowsByDocument.get(document);
        group.remove(window);
        if (!group.isEmpty()) {
            return Optional.empty();
        }
        windowsByDocument.remove(document);
        return Optional.of(document);
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

    /** One entry per window, "name — n" numbered within its document; choosing it calls {@code focus}. */
    public List<WindowMenu.Entry> menuEntries(W current, Consumer<W> focus) {
        List<WindowMenu.Entry> entries = new ArrayList<>();
        windowsByDocument.forEach((document, group) -> {
            for (int index = 0; index < group.size(); index++) {
                W window = group.get(index);
                entries.add(new WindowMenu.Entry(Messages.format("window.entry", document.displayName(), index + 1),
                        window.equals(current), () -> focus.accept(window)));
            }
        });
        return entries;
    }
}
