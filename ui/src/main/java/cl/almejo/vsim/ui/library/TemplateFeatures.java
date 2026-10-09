package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.JMenu;

/** The M4-A features of one editor view. Installed by {@link TemplatesExtension}; {@link #dispose()} undoes it. */
public final class TemplateFeatures {

    private final TemplateActions actions;
    private final LibraryPalette palette;

    private TemplateFeatures(TemplateActions actions, LibraryPalette palette) {
        this.actions = actions;
        this.palette = palette;
    }

    public static TemplateFeatures install(EditorView view, LibraryService service, LibraryDialogs dialogs) {
        TemplateActions actions = new TemplateActions(view.openDocument(), view.canvas(), service, dialogs);
        JMenu edit = view.menu(MenuId.EDIT);
        edit.addSeparator();
        edit.add(actions.createComponent());
        view.addContextMenuContributor(actions.contributor());
        LibraryPalette palette = new LibraryPalette(view, service, dialogs);
        palette.install();
        return new TemplateFeatures(actions, palette);
    }

    public TemplateActions actions() {
        return actions;
    }

    public LibraryPalette palette() {
        return palette;
    }

    /** Removes every listener registered on shared objects (document, library, service). */
    public void dispose() {
        palette.dispose();
        actions.dispose();
    }
}
