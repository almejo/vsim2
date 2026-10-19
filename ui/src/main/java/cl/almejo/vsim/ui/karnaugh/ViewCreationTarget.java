package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.command.PasteCommand;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.logic.TableCircuits;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.tools.FloatingSelectionTool;
import cl.almejo.vsim.ui.window.EditorView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The application's {@link CreationTarget}: a new window through the view's window commands, the floating paste of
 * the view's canvas, its status bar, and the library service (the same «Mis componentes» as «Crear componente»).
 */
final class ViewCreationTarget implements CreationTarget {

    private static final Logger LOGGER = LoggerFactory.getLogger(ViewCreationTarget.class);

    private final EditorView view;
    private final LibraryService service;

    ViewCreationTarget(EditorView view, LibraryService service) {
        this.view = view;
        this.service = service;
    }

    @Override
    public void openNewCircuit(String name, CircuitFragment fragment, String notice) {
        view.windowCommands().openNewCircuit(name, new PasteCommand(fragment, TableCircuits.NEW_CIRCUIT_ORIGIN),
                notice);
    }

    @Override
    public boolean canInsert() {
        return true; // the view the dialog came from: it outlives the modal dialog
    }

    @Override
    public void insertFloating(CircuitFragment fragment) {
        CircuitCanvas canvas = view.canvas();
        // The fragment follows the pointer once it moves over the canvas; until then it waits at the view's center.
        GridPoint center = canvas.viewport().snap(canvas.getWidth() / 2.0, canvas.getHeight() / 2.0);
        canvas.setTool(new FloatingSelectionTool(fragment, center));
        // The dialog still blocks the window now; it closes in this same event, so ask for the focus after it, or
        // Escape (cancel the paste) would not reach the canvas until a click.
        SwingUtilities.invokeLater(canvas::requestFocusInWindow);
    }

    @Override
    public void notice(String text) {
        view.statusBar().showNotice(text);
    }

    @Override
    public List<String> takenComponentNames() {
        List<String> names = new ArrayList<>();
        service.library().entries().forEach(entry -> names.add(entry.root().name()));
        view.openDocument().document().definitions().forEach(definition -> names.add(definition.name()));
        return names;
    }

    @Override
    public Set<String> takenComponentIds() {
        Set<String> ids = new HashSet<>(service.library().ids());
        view.openDocument().document().definitions().forEach(definition -> ids.add(definition.id()));
        return ids;
    }

    @Override
    public Optional<String> saveComponent(DefinitionBundle bundle) {
        try {
            service.save(bundle);
            return Optional.empty();
        } catch (IOException exception) {
            LOGGER.warn("Could not write {} to the component library", bundle.root().id(), exception);
            return Optional.of(service.writeError(bundle.root().id(), exception));
        }
    }
}
