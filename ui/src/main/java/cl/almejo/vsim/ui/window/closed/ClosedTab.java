package cl.almejo.vsim.ui.window.closed;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * One closed tab whose group stayed: a component tab, or one of several main tabs. It comes back at
 * {@code indexInGroup} of its circuit's group, in {@code window} if it is still open.
 */
public record ClosedTab(DocumentReference document, ClosedTabState tab, WindowReference window, int indexInGroup)
        implements ClosedEntry {

    public ClosedTab {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(tab, "tab");
        Objects.requireNonNull(window, "window");
    }

    @Override
    public Stream<DocumentReference> documents() {
        return Stream.of(document);
    }
}
