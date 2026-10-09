package cl.almejo.vsim.ui.tree;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/** Adds the "Componentes" tab to every editor view (overview R9); disposing the view stops the tab listening. */
public final class ComponentTreeExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        ComponentTreePanel panel = new ComponentTreePanel(view.openDocument(), view.canvas());
        view.inspector().addTab(Messages.text("tree.tab"), panel);
        return panel::dispose;
    }
}
