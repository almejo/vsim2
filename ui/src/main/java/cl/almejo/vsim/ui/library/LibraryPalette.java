package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.io.DefinitionFormat;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.DefinitionGraph;
import cl.almejo.vsim.core.library.DefinitionNames;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.PaletteEntry;
import cl.almejo.vsim.ui.panels.PalettePanel;
import cl.almejo.vsim.ui.window.PlaceBar;
import cl.almejo.vsim.ui.window.SeePiece;
import cl.almejo.vsim.ui.window.EditorView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    private PlaceBar placeBar;
    private Runnable removePlaceBar = () -> { };
    /** The palette entries this view disabled last (inside a component tab). */
    private Set<String> disabledIds = Set.of();

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
        installPlaceBar();
    }

    /** A component tab shows the place bar over its canvas (spec §5.1); it needs the library, so it lives here. */
    private void installPlaceBar() {
        OpenDocument place = view.openDocument();
        if (!place.isBody()) {
            return;
        }
        OpenDocument parent = place.parent().orElseThrow();
        placeBar = new PlaceBar(place, service.library(), new PlaceBar.Callbacks() {
            @Override
            public void showMain() {
                view.windowCommands().showPlace(parent, Optional.empty(), Optional.empty());
            }

            @Override
            public void enter(String definitionId, Optional<String> contextPath) {
                view.windowCommands().enter(parent, definitionId, contextPath);
            }

            @Override
            public void seePiece() {
                SeePiece.show(view);
            }

            @Override
            public void saveToLibrary(DefinitionBundle bundle) {
                save(bundle);
            }

            @Override
            public void addToLibrary(DefinitionBundle bundle) {
                LibraryPalette.this.addToLibrary(bundle);
            }
        });
        removePlaceBar = view.addAboveCanvas(placeBar);
    }

    public void dispose() {
        service.library().removeListener(refresher);
        view.openDocument().removeChangeListener(refresher);
        if (placeBar != null) {
            removePlaceBar.run();
            placeBar.dispose();
            placeBar = null;
        }
    }

    /**
     * "Mis componentes" = the library; "Componentes de este circuito" = the definitions this circuit uses (nested
     * ones included) that the library lacks. Unused definitions are left out: saving drops them (preflight S5).
     * The palette ignores unchanged lists, so ordinary edits leave it alone (preflight S6).
     */
    public void refresh() {
        Set<String> libraryIds = service.library().ids();
        // inside a component tab the circuit's components are offered, not only those its body already uses
        CircuitDocument document = circuitOf(view.openDocument()).document();
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
        disableWhatCannotGoInside();
    }

    private static OpenDocument circuitOf(OpenDocument place) {
        return place.parent().orElse(place);
    }

    /**
     * Inside a component tab (spec §6.5): the component itself and every component that contains it cannot be placed
     * in its body; each says why. The interface pin parts are always offered (spec §6.2 c: the bodies that contain the
     * component follow its new pins). The circuit's own tab disables nothing.
     */
    private void disableWhatCannotGoInside() {
        Map<String, String> reasons = new HashMap<>();
        OpenDocument place = view.openDocument();
        place.definitionId().ifPresent(definitionId -> {
            CircuitDocument circuit = circuitOf(place).document();
            for (PaletteEntry entry : view.palette().entries()) {
                entry.userComponent().flatMap(component -> reasonInside(component.bundle(), definitionId, circuit))
                        .ifPresent(reason -> reasons.put(entry.id(), reason));
            }
        });
        disabledIds.stream().filter(id -> !reasons.containsKey(id))
                .forEach(id -> view.palette().setDisabledReason(id, Optional.empty()));
        reasons.forEach((id, reason) -> view.palette().setDisabledReason(id, Optional.of(reason)));
        disabledIds = Set.copyOf(reasons.keySet());
    }

    /**
     * Why {@code bundle} cannot be placed inside {@code definitionId}: it is that component, or it contains it (in its
     * own bundle or in the circuit's version of its definitions), which would make a component contain itself.
     */
    private static Optional<String> reasonInside(DefinitionBundle bundle, String definitionId,
                                                 CircuitDocument circuit) {
        String rootId = bundle.root().id();
        if (rootId.equals(definitionId)) {
            return Optional.of(Messages.text("inside.cannotNestItself"));
        }
        boolean contains = bundle.all().stream().anyMatch(definition -> definition.id().equals(definitionId))
                || circuitClosureOf(rootId, circuit).contains(definitionId);
        return contains ? Optional.of(Messages.text("inside.containsThisComponent")) : Optional.empty();
    }

    /** The ids the circuit's version of {@code rootId} uses, at any depth (none when the circuit lacks it). */
    private static Set<String> circuitClosureOf(String rootId, CircuitDocument circuit) {
        try {
            return DefinitionGraph.knownClosure(List.of(rootId), circuit::definition).stream()
                    .map(ComponentDefinition::id).collect(Collectors.toSet());
        } catch (IllegalArgumentException recursive) {
            LOGGER.warn("The circuit's definition {} is recursive", rootId, recursive);
            return Set.of(rootId);
        }
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
        view.setToolOnEveryPane(() -> new PlaceDefinitionTool(component.bundle()));
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
        return new JMenuItem(Actions.of(text, behavior));
    }
}
