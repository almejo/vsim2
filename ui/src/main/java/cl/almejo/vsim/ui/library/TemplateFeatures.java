package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JSeparator;

/** The M4-A features of one editor view. Installed by {@link TemplatesExtension}; {@link #dispose()} undoes it. */
public final class TemplateFeatures {

    private final TemplateActions actions;
    private final LibraryPalette palette;
    private final Action importComponent;

    private TemplateFeatures(TemplateActions actions, LibraryPalette palette, Action importComponent) {
        this.actions = actions;
        this.palette = palette;
        this.importComponent = importComponent;
    }

    public static TemplateFeatures install(EditorView view, LibraryService service, LibraryDialogs dialogs) {
        TemplateActions actions = new TemplateActions(view.openDocument(), view.canvas(), service, dialogs);
        JMenu edit = view.menu(MenuId.EDIT);
        edit.addSeparator();
        edit.add(actions.createComponent());
        view.addContextMenuContributor(actions.contributor());

        LibraryImport importer = new LibraryImport(service, dialogs);
        Action importComponent = importer.action(view);
        addAfterSaving(view.menu(MenuId.FILE), importComponent);

        LibraryPalette palette = new LibraryPalette(view, service, dialogs, importer, importComponent);
        palette.install();
        return new TemplateFeatures(actions, palette, importComponent);
    }

    public TemplateActions actions() {
        return actions;
    }

    public LibraryPalette palette() {
        return palette;
    }

    /** Archivo ▸ Importar componente… */
    public Action importComponent() {
        return importComponent;
    }

    /** Removes every listener registered on shared objects (document, library, service). */
    public void dispose() {
        palette.dispose();
        actions.dispose();
    }

    /** Archivo: …, Guardar como…, ---, Importar componente…, ---, Cerrar, Salir. */
    private static void addAfterSaving(JMenu file, Action action) {
        for (int index = 0; index < file.getMenuComponentCount(); index++) {
            if (file.getMenuComponent(index) instanceof JSeparator) {
                file.insertSeparator(index);
                file.insert(action, index + 1);
                return;
            }
        }
        file.addSeparator();
        file.add(action);
    }
}
