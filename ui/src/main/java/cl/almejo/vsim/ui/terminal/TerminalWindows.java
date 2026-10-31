package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentChange;
import cl.almejo.vsim.core.document.DocumentListener;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.app.OpenDocument;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The Terminal windows of one document (spec §9): at most one per part, owned by the editor window that opened it.
 * Opening it again brings it to the front. It closes when the user closes it, when its part leaves the circuit, when
 * the editor window that opened it closes and when the document closes; its position and size are remembered per part
 * ({@link TerminalWindowPreferences}). Closing it never changes the part. EDT only.
 */
public final class TerminalWindows {

    /** One open window: what it shows, where, who opened it and its frames. */
    private record Open(TerminalWindowContent content, TerminalWindowHandle handle, Object openerKey,
                        TerminalFeeds.Subscription subscription) {
    }

    private final OpenDocument document;
    private final TerminalFeeds feeds;
    private final TerminalWindowServices services;
    private final TerminalWindowPreferences preferences;
    private final Map<String, Open> windows = new LinkedHashMap<>();
    private final DocumentListener documentListener = this::documentChanged;

    public TerminalWindows(OpenDocument document, TerminalFeeds feeds, TerminalWindowServices services,
                           TerminalWindowPreferences preferences) {
        this.document = Objects.requireNonNull(document, "document");
        this.feeds = Objects.requireNonNull(feeds, "feeds");
        this.services = Objects.requireNonNull(services, "services");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        document.document().addListener(documentListener);
    }

    /** Opens the window of the Terminal {@code componentId}, or brings it to the front when it is open. */
    public void open(String componentId, TerminalWindowOwner owner) {
        Open open = windows.get(componentId);
        if (open != null) {
            open.handle().toFront();
            return;
        }
        boolean terminal = document.document().component(new ElementId(componentId))
                .filter(component -> component.typeId().equals(StandardComponentTypes.TERMINAL)).isPresent();
        if (!terminal) {
            return;
        }
        TerminalWindowContent content = new TerminalWindowContent(document, componentId, services, owner);
        TerminalFeeds.Subscription subscription = feeds.subscribe(componentId, content.historyLines(),
                content::update);
        TerminalWindowHandle handle = services.factory().show(owner.window(), content.title(), content,
                preferences.bounds(documentKey(), componentId), () -> closedByUser(componentId));
        windows.put(componentId, new Open(content, handle, owner.key(), subscription));
    }

    /** Whether the window of {@code componentId} is open. */
    public boolean isOpen(String componentId) {
        return windows.containsKey(componentId);
    }

    /** What the window of {@code componentId} shows, while it is open. */
    Optional<TerminalWindowContent> content(String componentId) {
        return Optional.ofNullable(windows.get(componentId)).map(Open::content);
    }

    /** «Destello» in the window of {@code componentId}, if it is open. */
    public void flash(String componentId) {
        content(componentId).ifPresent(TerminalWindowContent::flash);
    }

    /** Once per frame. */
    public void tick() {
        windows.values().forEach(open -> open.content().tick());
    }

    /** The editor window {@code openerKey} closes: the windows it opened close with it. */
    public void closeOpenedBy(Object openerKey) {
        List.copyOf(windows.entrySet()).stream().filter(entry -> entry.getValue().openerKey() == openerKey)
                .forEach(entry -> close(entry.getKey()));
    }

    /** The document closes: every window closes and the document is no longer watched. */
    public void dispose() {
        document.document().removeListener(documentListener);
        List.copyOf(windows.keySet()).forEach(this::close);
    }

    private void closedByUser(String componentId) {
        Open open = windows.remove(componentId);
        if (open != null) {
            forget(componentId, open);
        }
    }

    private void close(String componentId) {
        Open open = windows.remove(componentId);
        if (open != null) {
            forget(componentId, open);
            open.handle().close();
        }
    }

    private void forget(String componentId, Open open) {
        preferences.saveBounds(documentKey(), componentId, open.handle().bounds());
        open.subscription().cancel();
        open.content().dispose();
    }

    private void documentChanged(CircuitDocument changed, DocumentChange change) {
        for (String componentId : List.copyOf(windows.keySet())) {
            if (changed.component(new ElementId(componentId)).isEmpty()) {
                close(componentId);
            } else {
                Open open = windows.get(componentId);
                open.content().parametersChanged();
                open.subscription().historyLines(open.content().historyLines());
                open.handle().setTitle(open.content().title());
            }
        }
    }

    /** The document as the preferences know it: its file, or its untitled name for this session. */
    private String documentKey() {
        return document.path().map(path -> path.toAbsolutePath().normalize().toString())
                .orElseGet(() -> "untitled:" + document.displayName());
    }
}
