package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.io.LibraryProblem;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.ImportMenu;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.panes.EveryPane;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import java.util.List;
import java.util.function.Consumer;

/** The M4-A features of one editor view. Installed by {@link TemplatesExtension}; {@link #dispose()} undoes it. */
public final class TemplateFeatures {

    private final EditorView view;
    private final LibraryService service;
    private final TemplateActions actions;
    private final LibraryPalette palette;
    private final Action importComponent;
    private final Runnable importItemRemover;
    private final LibraryVersionBadge badge;
    private final Runnable problemsListener;
    /** Takes the badge overlay and the kept-versions warning back from every pane. */
    private final Runnable removeFromPanes;

    private TemplateFeatures(EditorView view, LibraryService service, TemplateActions actions, LibraryPalette palette,
                             Action importComponent, Runnable importItemRemover, LibraryVersionBadge badge,
                             Runnable problemsListener, Runnable removeFromPanes) {
        this.view = view;
        this.service = service;
        this.actions = actions;
        this.palette = palette;
        this.importComponent = importComponent;
        this.importItemRemover = importItemRemover;
        this.badge = badge;
        this.problemsListener = problemsListener;
        this.removeFromPanes = removeFromPanes;
    }

    public static TemplateFeatures install(EditorView view, LibraryService service, LibraryDialogs dialogs) {
        TemplateActions actions = new TemplateActions(view.openDocument(), view::canvas, service, dialogs);
        JMenu edit = view.menu(MenuId.EDIT);
        edit.addSeparator();
        edit.add(actions.createComponent());
        edit.add(actions.updateComponent());
        view.addContextMenuContributor(actions.contributor());
        view.setTemplateActions(actions);

        LibraryImport importer = new LibraryImport(service, dialogs);
        Action importComponent = importer.action(view);
        // Archivo ▸ Importar ▸ «Componente…» (the palette's menu keeps the action's «Importar componente…»).
        JMenuItem importItem = new JMenuItem(importComponent);
        importItem.setText(Messages.text("menu.file.import.component"));
        Runnable importItemRemover = ImportMenu.add(view.menu(MenuId.FILE), importItem);

        LibraryPalette palette = new LibraryPalette(view, service, dialogs, importer, importComponent);
        palette.install();
        // Ruling: a component file dropped on the canvas or the window is imported, not opened as a circuit.
        view.setComponentFileImporter(files -> importer.importFiles(view, files));

        LibraryVersionBadge badge = new LibraryVersionBadge(view.openDocument(), service.library(),
                () -> view.canvas().scheme(), EveryPane.repaint(view));
        badge.install();
        Runnable removeBadges = EveryPane.addOverlay(view, badge);

        // Also on library changes: whether a broken file "can be deleted" depends on what the library holds.
        Runnable problemsListener = () -> {
            List<LibraryProblem> problems = service.problems();
            int fileCount = (int) problems.stream()
                    .filter(problem -> problem.kind() != LibraryProblem.Kind.DIRECTORY_GONE)
                    .count();
            view.problems().showLibraryProblems(problems.stream()
                    .map(problem -> LibraryProblemText.describe(problem, service.library()::find))
                    .toList(), fileCount);
        };
        service.addProblemsListener(problemsListener);
        service.library().addListener(problemsListener);
        problemsListener.run();

        // A drop or paste keeps this circuit's version of a nested definition (R4); say so as a palette click does.
        // The message is worked out now and shown after the drop finishes (a dialog inside a drop can hang it).
        Consumer<CircuitFragment> keptVersionsWarning = fragment -> KeptVersions
                .message(view.openDocument().document(), fragment)
                .ifPresent(message -> SwingUtilities.invokeLater(() -> dialogs.showMessage(view.canvas(), message)));
        Runnable removeWarnings = view.forEachPane(pane -> {
            pane.addIncomingFragmentListener(keptVersionsWarning);
            return () -> pane.removeIncomingFragmentListener(keptVersionsWarning);
        });
        return new TemplateFeatures(view, service, actions, palette, importComponent, importItemRemover, badge,
                problemsListener, () -> {
                    removeWarnings.run();
                    removeBadges.run();
                });
    }

    public TemplateActions actions() {
        return actions;
    }

    public LibraryPalette palette() {
        return palette;
    }

    /** Archivo ▸ Importar ▸ Componente… (and the palette's «Importar componente…»). */
    public Action importComponent() {
        return importComponent;
    }

    public LibraryVersionBadge badge() {
        return badge;
    }

    /**
     * Removes every listener registered on shared objects (document, library, service), the overlay and the item of
     * Archivo ▸ Importar.
     */
    public void dispose() {
        importItemRemover.run();
        view.setComponentFileImporter(null);
        view.setTemplateActions(null);
        removeFromPanes.run();
        service.removeProblemsListener(problemsListener);
        service.library().removeListener(problemsListener);
        badge.dispose();
        palette.dispose();
        actions.dispose();
    }
}
