package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.JMenu;

/** The M4-A features of one editor view. Installed by {@link TemplatesExtension}; {@link #dispose()} undoes it. */
public final class TemplateFeatures {

    private final TemplateActions actions;

    private TemplateFeatures(TemplateActions actions) {
        this.actions = actions;
    }

    public static TemplateFeatures install(EditorView view, LibraryService service, LibraryDialogs dialogs) {
        TemplateActions actions = new TemplateActions(view.openDocument(), view.canvas(), service, dialogs);
        JMenu edit = view.menu(MenuId.EDIT);
        edit.addSeparator();
        edit.add(actions.createComponent());
        view.addContextMenuContributor(actions.contributor());
        return new TemplateFeatures(actions);
    }

    public TemplateActions actions() {
        return actions;
    }

    /** Removes every listener registered on shared objects (document, library, service). */
    public void dispose() {
        actions.dispose();
    }
}
