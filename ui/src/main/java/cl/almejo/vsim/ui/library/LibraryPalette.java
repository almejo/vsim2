package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.io.DefinitionFormat;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.DefinitionNames;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.PalettePanel;
import cl.almejo.vsim.ui.window.EditorView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps one view's palette in step with the library (every window) and with its document's own definitions, starts
 * placing a user component when it is chosen, and runs the palette's context menu (spec §5.6; file drops go through {@link EditorView#dropFiles}).
 * Listeners run on the EDT; {@link #dispose()} removes them.
 */
public final class LibraryPalette {

    private static final Logger LOGGER = LoggerFactory.getLogger(LibraryPalette.class);
    private static final Comparator<ComponentDefinition> BY_NAME = Comparator
            .comparing(ComponentDefinition::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ComponentDefinition::id);

    private final EditorView view;
    private final LibraryService service;
    private final LibraryDialogs dialogs;
    private final LibraryImport importer;
    private final Action importComponent;
    private final Runnable refresher = this::refresh;

    public LibraryPalette(EditorView view, LibraryService service, LibraryDialogs dialogs, LibraryImport importer,
                          Action importComponent) {
        this.view = view;
        this.service = service;
        this.dialogs = dialogs;
        this.importer = importer;
        this.importComponent = importComponent;
    }

    public void install() {
        PalettePanel palette = view.palette();
        palette.setUserComponentChooser(this::place);
        palette.setUserMenuProvider(this::menuFor);
        service.library().addListener(refresher);
        view.openDocument().addChangeListener(refresher);
        refresh();
    }

    public void dispose() {
        service.library().removeListener(refresher);
        view.openDocument().removeChangeListener(refresher);
    }

    /**
     * "Mis componentes" = the library; "Componentes de este circuito" = the definitions this circuit uses (nested
     * ones included) that the library lacks. Unused definitions are left out: saving drops them (preflight S5).
     * The palette ignores unchanged lists, so ordinary edits leave it alone (preflight S6).
     */
    public void refresh() {
        Set<String> libraryIds = service.library().ids();
        CircuitDocument document = view.openDocument().document();
        List<DefinitionBundle> own = new ArrayList<>();
        for (ComponentDefinition definition : document.usedDefinitions().stream().sorted(BY_NAME).toList()) {
            if (libraryIds.contains(definition.id())) {
                continue;
            }
            try {
                own.add(DefinitionBundle.of(definition, document::definition));
            } catch (IllegalArgumentException exception) {
                LOGGER.warn("Definition {} of this circuit cannot be offered in the palette", definition.id(),
                        exception);
            }
        }
        view.palette().setUserComponents(service.library().entries(), own);
    }

    /** The palette's right-click menu for {@code entry} (none: only "Importar componente…"). */
    public Optional<JPopupMenu> menuFor(Optional<PalettePanel.UserComponent> entry) {
        JPopupMenu menu = new JPopupMenu();
        entry.ifPresent(component -> {
            DefinitionBundle bundle = component.bundle();
            String id = bundle.root().id();
            if (component.inLibrary()) {
                menu.add(item(Messages.text("action.renameComponent"), () -> rename(id)));
                menu.add(item(Messages.text("action.exportComponent"), () -> export(id)));
                menu.add(item(Messages.text("action.showInFolder"),
                        () -> dialogs.showInFolder(view, service.fileOf(id))));
                menu.add(item(Messages.text("action.removeFromLibrary"), () -> remove(bundle)));
            } else {
                menu.add(item(Messages.text("action.addToLibrary"), () -> addToLibrary(bundle)));
            }
            menu.addSeparator();
        });
        menu.add(new JMenuItem(importComponent));
        return Optional.of(menu);
    }

    private void place(PalettePanel.UserComponent component) {
        KeptVersions.warn(view.openDocument().document(), component.bundle(), dialogs, view.canvas());
        view.canvas().setTool(new PlaceDefinitionTool(component.bundle()));
        view.canvas().requestFocusInWindow();
    }

    private void rename(String id) {
        Optional<DefinitionBundle> bundle = service.library().find(id);
        if (bundle.isEmpty()) {
            return;
        }
        dialogs.askNewName(view, bundle.get().root().name()).ifPresent(newName -> {
            Optional<DefinitionNames.NameProblem> problem = DefinitionNames.problem(newName, namesOtherThan(id));
            if (problem.isPresent()) {
                dialogs.showError(view, ComponentForm.describe(problem.get(), newName));
                return;
            }
            try {
                service.rename(id, newName.strip());
            } catch (IOException exception) {
                LOGGER.warn("Could not rename {}", id, exception);
                dialogs.showError(view, service.writeError(id, exception));
            }
        });
    }

    private void export(String id) {
        dialogs.chooseExportFile(view, id + DefinitionFormat.FILE_SUFFIX).ifPresent(target -> {
            try {
                service.export(id, target);
            } catch (IOException exception) {
                LOGGER.warn("Could not export {} to {}", id, target, exception);
                dialogs.showError(view, Messages.format("library.error.write", target,
                        String.valueOf(exception.getMessage())));
            }
        });
    }

    /** Circuits that use it keep their embedded copy, so nothing but the library changes (spec §5.6). */
    private void remove(DefinitionBundle bundle) {
        String id = bundle.root().id();
        if (!dialogs.confirmRemove(view, bundle.root().name())) {
            return;
        }
        try {
            service.remove(id);
        } catch (IOException exception) {
            LOGGER.warn("Could not remove {}", id, exception);
            dialogs.showError(view, Messages.format("library.error.remove", service.fileOf(id),
                    String.valueOf(exception.getMessage())));
        }
    }

    /**
     * "Agregar a mi librería". The id is checked again now: another window may have put it in the library since the
     * menu was built, and then the user chooses as on an import.
     */
    private void addToLibrary(DefinitionBundle bundle) {
        importer.resolveCollision(view, bundle).ifPresent(this::save);
    }

    private void save(DefinitionBundle bundle) {
        try {
            service.save(bundle);
        } catch (IOException exception) {
            LOGGER.warn("Could not write {} to the component library", bundle.root().id(), exception);
            dialogs.showError(view, service.writeError(bundle.root().id(), exception));
        }
    }

    /** Names used by other library entries and by document definitions with another id. */
    private List<String> namesOtherThan(String id) {
        List<String> names = new ArrayList<>();
        service.library().entries().stream()
                .filter(entry -> !entry.root().id().equals(id))
                .forEach(entry -> names.add(entry.root().name()));
        view.openDocument().document().definitions().stream()
                .filter(definition -> !definition.id().equals(id))
                .forEach(definition -> names.add(definition.name()));
        return names;
    }

    private static JMenuItem item(String text, Runnable behavior) {
        return new JMenuItem(new AbstractAction(text) {
            @Override
            public void actionPerformed(ActionEvent event) {
                behavior.run();
            }
        });
    }
}
