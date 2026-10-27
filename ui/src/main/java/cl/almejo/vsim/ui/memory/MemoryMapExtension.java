package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.panes.EveryPane;

import java.util.Objects;
import java.util.function.Function;

/**
 * Adds the «Mapa de memoria» tab (spec §6) to every editor view: it follows the view's selection, the document's
 * changes and the active pane's snapshots; disposing the view removes every listener and stops the frames.
 */
public final class MemoryMapExtension implements EditorExtension {

    private final Function<EditorView, MemoryMapActions> actions;

    /** Cells that open nothing and no «Volcar…» (until the content editor is wired in). */
    public MemoryMapExtension() {
        this(view -> MemoryMapActions.NONE);
    }

    /** {@code actions} gives each view what a click on a cell and «Volcar…» do. */
    public MemoryMapExtension(Function<EditorView, MemoryMapActions> actions) {
        this.actions = Objects.requireNonNull(actions, "actions");
    }

    @Override
    public Runnable install(EditorView view) {
        MemoryMapPanel panel = new MemoryMapPanel(view.openDocument(), actions.apply(view));
        view.inspector().addTab(Messages.text("memory.map.tab"), panel);
        Runnable selectionListener = () -> panel.follow(view.selection().ids());
        view.selection().addListener(selectionListener);
        Runnable documentListener = panel::documentChanged;
        view.openDocument().addChangeListener(documentListener);
        Runnable stopSnapshots = EveryPane.addActivePaneSnapshotListener(view, snapshot -> panel.refresh());
        return () -> {
            stopSnapshots.run();
            view.openDocument().removeChangeListener(documentListener);
            view.selection().removeListener(selectionListener);
            panel.dispose();
        };
    }
}
