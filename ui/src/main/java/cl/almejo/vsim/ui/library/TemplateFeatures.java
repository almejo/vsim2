package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JSeparator;

/** The M4-A features of one editor view. Installed by {@link TemplatesExtension}; {@link #dispose()} undoes it. */
public final class TemplateFeatures {

    private final EditorView view;
    private final LibraryService service;
    private final TemplateActions actions;
    private final LibraryPalette palette;
    private final Action importComponent;
    private final LibraryVersionBadge badge;
    private final Runnable problemsListener;

    private TemplateFeatures(EditorView view, LibraryService service, TemplateActions actions, LibraryPalette palette,
                             Action importComponent, LibraryVersionBadge badge, Runnable problemsListener) {
        this.view = view;
        this.service = service;
        this.actions = actions;
        this.palette = palette;
        this.importComponent = importComponent;
        this.badge = badge;
        this.problemsListener = problemsListener;
    }

    public static TemplateFeatures install(EditorView view, LibraryService service, LibraryDialogs dialogs) {
        TemplateActions actions = new TemplateActions(view.openDocument(), view.canvas(), service, dialogs);
        JMenu edit = view.menu(MenuId.EDIT);
        edit.addSeparator();
        edit.add(actions.createComponent());
        edit.add(actions.updateComponent());
        view.addContextMenuContributor(actions.contributor());

        LibraryImport importer = new LibraryImport(service, dialogs);
        Action importComponent = importer.action(view);
        addAfterSaving(view.menu(MenuId.FILE), importComponent);

        LibraryPalette palette = new LibraryPalette(view, service, dialogs, importer, importComponent);
        palette.install();
        // Ruling: a component file dropped on the canvas or the window is imported, not opened as a circuit.
        view.setComponentFileImporter(files -> importer.importFiles(view, files));

        LibraryVersionBadge badge = new LibraryVersionBadge(view.openDocument(), service.library(),
                view.canvas()::scheme, view.canvas()::repaint);
        badge.install();
        view.canvas().addOverlay(badge);

        // Also on library changes: whether a broken file "can be deleted" depends on what the library holds.
        Runnable problemsListener = () -> view.problems().showLibraryProblems(service.problems().stream()
                .map(problem -> LibraryProblemText.describe(problem, service.library()::find))
                .toList());
        service.addProblemsListener(problemsListener);
        service.library().addListener(problemsListener);
        problemsListener.run();
        return new TemplateFeatures(view, service, actions, palette, importComponent, badge, problemsListener);
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

    public LibraryVersionBadge badge() {
        return badge;
    }

    /** Removes every listener registered on shared objects (document, library, service) and the overlay. */
    public void dispose() {
        view.setComponentFileImporter(null);
        service.removeProblemsListener(problemsListener);
        service.library().removeListener(problemsListener);
        view.canvas().removeOverlay(badge);
        badge.dispose();
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
