package cl.almejo.vsim.ui.groups;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/**
 * Gives every editor view the group rows of bus splitters and joiners in «Propiedades» ({@link GroupsRows}), whose
 * «Configuración avanzada…» opens the «Grupos» window through {@link GroupsWindowLauncher}.
 */
public final class GroupsExtension implements EditorExtension {

    private final GroupsWindowLauncher launcher;

    public GroupsExtension() {
        this(new GroupsWindowLauncher());
    }

    public GroupsExtension(GroupsWindowLauncher launcher) {
        this.launcher = launcher;
    }

    @Override
    public Runnable install(EditorView view) {
        return view.labelFeatures().properties().addComponentRows(new GroupsRows(view, launcher));
    }
}
