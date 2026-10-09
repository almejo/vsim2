package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.PalettePanel;
import cl.almejo.vsim.ui.window.EditorView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keeps one view's palette in step with the library (every window) and with its document's own definitions, and
 * starts placing a user component when it is chosen. Listeners run on the EDT; {@link #dispose()} removes them.
 */
public final class LibraryPalette {

    private static final Logger LOGGER = LoggerFactory.getLogger(LibraryPalette.class);
    private static final Comparator<ComponentDefinition> BY_NAME = Comparator
            .comparing(ComponentDefinition::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ComponentDefinition::id);

    private final EditorView view;
    private final LibraryService service;
    private final LibraryDialogs dialogs;
    private final Runnable refresher = this::refresh;
    private List<DefinitionBundle> pushedLibrary;
    private List<DefinitionBundle> pushedOwn;

    public LibraryPalette(EditorView view, LibraryService service, LibraryDialogs dialogs) {
        this.view = view;
        this.service = service;
        this.dialogs = dialogs;
    }

    public void install() {
        view.palette().setUserComponentChooser(this::place);
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
     * Nothing is pushed when both lists are unchanged, so ordinary edits leave the palette alone (preflight S6).
     */
    public void refresh() {
        List<DefinitionBundle> library = service.library().entries();
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
        if (library.equals(pushedLibrary) && own.equals(pushedOwn)) {
            return;
        }
        pushedLibrary = library;
        pushedOwn = List.copyOf(own);
        view.palette().setUserComponents(pushedLibrary, pushedOwn);
    }

    private void place(PalettePanel.UserComponent component) {
        warnAboutKeptVersions(component.bundle());
        view.canvas().setTool(new PlaceDefinitionTool(component.bundle()));
        view.canvas().requestFocusInWindow();
    }

    /** Placing keeps the circuit's own version of a nested definition (preflight S3): say so once, naming them. */
    private void warnAboutKeptVersions(DefinitionBundle bundle) {
        CircuitDocument document = view.openDocument().document();
        List<String> names = new ArrayList<>();
        for (ComponentDefinition dependency : bundle.dependencies()) {
            Optional<ComponentDefinition> existing = document.definition(dependency.id());
            if (existing.isPresent() && !existing.get().equals(dependency)) {
                names.add(Messages.format("library.place.componentName", dependency.name()));
            }
        }
        if (!names.isEmpty()) {
            dialogs.showMessage(view.canvas(), Messages.format("library.place.keptVersion",
                    names.stream().collect(Collectors.joining(", "))));
        }
    }
}
