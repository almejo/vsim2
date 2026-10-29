package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/** Adds «Agregar separador de bus» / «Agregar unión de bus» to the context menu of a component's bus pin. */
public final class BusPinMenuExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        return view.addContextMenuContributor(new BusPinMenuContributor(view.openDocument(), view.selection()));
    }
}
