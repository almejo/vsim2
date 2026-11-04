package cl.almejo.vsim.ui.groups;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.window.EditorView;

/**
 * Opens the «Grupos» window of a bus splitter or joiner over an editor window («Configuración avanzada…» in
 * Propiedades). Tests replace {@link #open} so no window is shown.
 */
public class GroupsWindowLauncher {

    /** Shows the window of part {@code componentId} (modal, on the EDT); nothing when it is gone or has no groups. */
    public void open(EditorView view, ElementId componentId) {
        boolean grouped = view.openDocument().document().component(componentId).filter(GroupsEdit::hasGroups)
                .isPresent();
        if (!grouped) {
            return;
        }
        new GroupsWindow(view.openDocument(), componentId, view.services().painters(), () -> view.canvas().scheme())
                .show(view);
    }
}
