package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.PropertiesPanel;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.ImportMenu;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.JMenuItem;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Gives every editor view its memories: a double click on a RAM or ROM and the part's context menu open the content
 * editor through {@link MemoryEditorLauncher}; the «Propiedades» tab gets the memory rows; the document's linked
 * files are watched ({@link MemoryLinks}); memory files dropped or opened become new memories, and Archivo ▸ Importar
 * gets «Memoria desde archivo…».
 */
public final class MemoryEditorExtension implements EditorExtension {

    private final MemoryEditorLauncher launcher;
    private final MemoryLinks links;

    public MemoryEditorExtension(MemoryEditorLauncher launcher, MemoryLinks links) {
        this.launcher = launcher;
        this.links = links;
    }

    @Override
    public Runnable install(EditorView view) {
        view.setMemoryEditor(component -> launcher.open(view, component.id(), OptionalLong.empty()));
        Runnable removeContextMenu = view.addContextMenuContributor(new MemoryContextMenu(view, launcher));
        LinkedMemoryWatcher watcher = links.acquireWatcher(view.openDocument());
        PropertiesPanel properties = view.labelFeatures().properties();
        Runnable removeRows = properties.addComponentRows(new MemoryInspectorRows(view, launcher, links, watcher));
        Runnable removeProblems = watcher.addProblemsListener(new ProblemsShown(view, watcher, properties));
        view.setMemoryFileImporter(links.fileImport().importerFor(view));
        JMenuItem importItem = new JMenuItem(Actions.of(Messages.text("action.importMemory"),
                () -> links.fileImport().chooseAndImport(view)));
        Runnable removeImportItem = ImportMenu.add(view.menu(MenuId.FILE), importItem);
        return () -> {
            removeImportItem.run();
            view.setMemoryFileImporter(null);
            removeProblems.run();
            removeRows.run();
            links.releaseWatcher(view.openDocument());
            removeContextMenu.run();
            view.clearMemoryEditor();
        };
    }

    /**
     * When the linked files' problems change: a new problem is told in the status bar, and the inspector shows the
     * memory again when it is the one selected.
     */
    private static final class ProblemsShown implements Runnable {

        private final EditorView view;
        private final LinkedMemoryWatcher watcher;
        private final PropertiesPanel properties;
        private Map<ElementId, String> told = new HashMap<>();

        private ProblemsShown(EditorView view, LinkedMemoryWatcher watcher, PropertiesPanel properties) {
            this.view = view;
            this.watcher = watcher;
            this.properties = properties;
        }

        @Override
        public void run() {
            Map<ElementId, String> now = watcher.problems();
            now.forEach((id, problem) -> {
                if (!problem.equals(told.get(id))) {
                    view.statusBar().showNotice(problem);
                }
            });
            told = now;
            Set<ElementId> selected = view.selection().ids();
            boolean memoryShown = selected.size() == 1 && view.openDocument().document()
                    .component(selected.iterator().next())
                    .filter(component -> MemoryTypes.kindOf(component.typeId()).isPresent()).isPresent();
            if (memoryShown) {
                properties.refresh();
            }
        }
    }
}
