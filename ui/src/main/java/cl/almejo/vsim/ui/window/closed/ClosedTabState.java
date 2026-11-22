package cl.almejo.vsim.ui.window.closed;

import cl.almejo.vsim.ui.canvas.ViewState;

import java.util.Objects;
import java.util.Optional;

/**
 * What one closed tab showed, as {@code TabState} keeps it for the session but without the file (an untitled circuit's
 * tabs come back too): the main circuit when {@code definitionId} is empty, else that component's body with the
 * instance whose values it showed (empty: «Sin valores»), and its zoom and center. {@code definitionName} names the
 * component if it is gone when the tab comes back.
 */
public record ClosedTabState(Optional<String> definitionId, Optional<String> definitionName,
                             Optional<String> contextPath, ViewState view) {

    public ClosedTabState {
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(definitionName, "definitionName");
        Objects.requireNonNull(contextPath, "contextPath");
        Objects.requireNonNull(view, "view");
    }

    public static ClosedTabState main(ViewState view) {
        return new ClosedTabState(Optional.empty(), Optional.empty(), Optional.empty(), view);
    }

    public static ClosedTabState component(String definitionId, String definitionName, Optional<String> contextPath,
                                           ViewState view) {
        return new ClosedTabState(Optional.of(definitionId), Optional.of(definitionName), contextPath, view);
    }

    public boolean isMain() {
        return definitionId.isEmpty();
    }
}
