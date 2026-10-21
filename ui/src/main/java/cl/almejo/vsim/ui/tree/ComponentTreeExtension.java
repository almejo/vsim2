package cl.almejo.vsim.ui.tree;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.panes.EveryPane;

/** Adds the "Componentes" tab to every editor view (overview R9); disposing the view stops the tab listening. */
public final class ComponentTreeExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        // a chosen row travels in the active pane; the values follow the active pane's snapshots
        ComponentTreePanel panel = new ComponentTreePanel(view.openDocument(), view::canvas,
                listener -> EveryPane.addActivePaneSnapshotListener(view, listener), view.services().painters(),
                view.services().appearance(), view.services().preferences());
        view.inspector().addTab(Messages.text("tree.tab"), panel);
        return panel::dispose;
    }
}
