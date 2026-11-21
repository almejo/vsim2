package cl.almejo.vsim.ui.window.closed;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A whole closed group (its last main tab closed): every tab in the strip's order, the one that was active, whether it
 * was folded, its window and its place among the window's groups.
 */
public record ClosedGroup(DocumentReference document, List<ClosedTabState> tabs, int activeTab, boolean folded,
                          WindowReference window, int groupIndex) implements ClosedEntry {

    public ClosedGroup {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(window, "window");
        tabs = List.copyOf(tabs);
    }

    /** How its circuit closed: saved, with its changes lost («No guardar»), never saved, or still open somewhere. */
    public Closing closing() {
        return document.closing();
    }

    @Override
    public Stream<DocumentReference> documents() {
        return Stream.of(document);
    }
}
