package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.ui.canvas.ViewState;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * One tab of a window in the last session (spec §4.7, §10.6): the circuit file, its place (the main circuit when
 * {@code definitionId} is empty, else that component's body), the instance whose values a component tab showed (empty:
 * «Sin valores») and its zoom and center. {@code definitionName} is the component's name when the session was saved,
 * only to name it if it is gone at the next start. The split is not here: {@code RememberedSplit} keeps it per place.
 */
public record TabState(Path path, Optional<String> definitionId, Optional<String> definitionName,
                       Optional<String> contextPath, ViewState view) {

    public TabState {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(definitionName, "definitionName");
        Objects.requireNonNull(contextPath, "contextPath");
        Objects.requireNonNull(view, "view");
    }

    /** A main tab of {@code path}. */
    public static TabState main(Path path, ViewState view) {
        return new TabState(path, Optional.empty(), Optional.empty(), Optional.empty(), view);
    }

    /** A component tab of {@code path}'s definition {@code definitionId}, showing {@code contextPath}'s values. */
    public static TabState component(Path path, String definitionId, String definitionName,
                                     Optional<String> contextPath, ViewState view) {
        return new TabState(path, Optional.of(definitionId), Optional.of(definitionName), contextPath, view);
    }

    public boolean isMain() {
        return definitionId.isEmpty();
    }
}
