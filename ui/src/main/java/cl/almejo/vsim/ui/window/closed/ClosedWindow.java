package cl.almejo.vsim.ui.window.closed;

import cl.almejo.vsim.ui.session.WindowState;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A whole closed window (Ctrl+Mayús+W, its close box, or its last group closed): its groups in order, the one that was
 * active and where it was. It comes back as a new window.
 */
public record ClosedWindow(List<ClosedGroup> groups, int activeGroup, WindowState bounds) implements ClosedEntry {

    public ClosedWindow {
        groups = List.copyOf(groups);
        Objects.requireNonNull(bounds, "bounds");
    }

    @Override
    public Stream<DocumentReference> documents() {
        return groups.stream().map(ClosedGroup::document);
    }
}
