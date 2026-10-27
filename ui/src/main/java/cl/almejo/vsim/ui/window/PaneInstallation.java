package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.canvas.CircuitCanvas;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** One {@link EditorView#forEachPane} registration and what undoes it on each pane it was installed on. */
final class PaneInstallation {

    private final Function<CircuitCanvas, Runnable> install;
    private final Map<CircuitCanvas, Runnable> undoByPane = new LinkedHashMap<>();

    PaneInstallation(Function<CircuitCanvas, Runnable> install) {
        this.install = install;
    }

    void installOn(CircuitCanvas pane) {
        if (!undoByPane.containsKey(pane)) {
            undoByPane.put(pane, Objects.requireNonNull(install.apply(pane), "what undoes the installation"));
        }
    }

    void undoExcept(List<CircuitCanvas> kept) {
        for (CircuitCanvas pane : List.copyOf(undoByPane.keySet())) {
            if (!kept.contains(pane)) {
                undoByPane.remove(pane).run();
            }
        }
    }

    void undoAll() {
        undoExcept(List.of());
    }
}
