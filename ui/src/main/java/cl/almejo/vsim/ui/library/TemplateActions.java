package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.CompositeCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.DefinitionBuilder;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.PinBridges;
import cl.almejo.vsim.core.library.PinOrder;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.actions.ContextMenuContributor;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenuItem;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Template editing for one canvas (spec §5.6): "Crear componente desde selección…" and its context menu entry. */
public final class TemplateActions {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateActions.class);

    private final OpenDocument openDocument;
    private final CircuitCanvas canvas;
    private final LibraryService service;
    private final LibraryDialogs dialogs;
    private final Action createComponent;

    public TemplateActions(OpenDocument openDocument, CircuitCanvas canvas, LibraryService service,
                           LibraryDialogs dialogs) {
        this.openDocument = openDocument;
        this.canvas = canvas;
        this.service = service;
        this.dialogs = dialogs;
        this.createComponent = action(Messages.text("action.createComponent"), this::createFromSelection);
        canvas.selection().addListener(this::refresh);
        refresh();
    }

    public Action createComponent() {
        return createComponent;
    }

    /** Nothing to undo yet: the only listener is on the view's own selection, which dies with the view. */
    public void dispose() {
    }

    /**
     * Without interface pins the dialog does not open and a message says why. Otherwise, once accepted: one undo step
     * that adds the definitions and, if asked, replaces the selection by one instance at its top-left, with
     * {@link PinBridges} keeping what was connected to the interface pins from outside (pins it cannot connect safely
     * are named in a message); then the bundle is written to the library (a write failure is reported, the circuit
     * keeps the component).
     */
    public void createFromSelection() {
        CircuitDocument document = openDocument.document();
        Set<ElementId> ids = canvas.selection().ids();
        if (ids.isEmpty()) {
            return;
        }
        if (!DefinitionBuilder.hasInterfacePins(document, ids)) {
            dialogs.showMessage(canvas, Messages.text("template.noInterfacePins"));
            return;
        }
        PinOrder order = DefinitionBuilder.defaultPinOrder(document, ids);
        ComponentForm form = ComponentForm.create(order, DefinitionBuilder.pinNames(document, order), takenNames(),
                takenIds());
        dialogs.editComponent(canvas, form).ifPresent(result -> create(ids, result));
    }

    /** Context menu: "Crear componente desde selección…" on a multi-selection or a selection with interface pins. */
    public ContextMenuContributor contributor() {
        return (menu, target, selection, gridPosition) -> {
            if (target.isEmpty() || !selection.contains(target.get().id())) {
                return;
            }
            if (selection.size() > 1 || DefinitionBuilder.hasInterfacePins(openDocument.document(), selection.ids())) {
                menu.add(new JMenuItem(createComponent));
            }
        };
    }

    void refresh() {
        createComponent.setEnabled(!canvas.selection().isEmpty());
    }

    private void create(Set<ElementId> ids, ComponentForm.Result result) {
        CircuitDocument document = openDocument.document();
        ComponentTypeRegistry registry = openDocument.registry();
        DefinitionBundle bundle = DefinitionBuilder.fromSelection(document, ids, result.definitionId(), result.name(),
                result.pinOrder(), registry);
        List<Command> steps = new ArrayList<>();
        steps.add(new PutDefinitionsCommand(bundle.all()));
        Component instance = null;
        List<String> unconnectedPins = List.of();
        if (result.replaceSelection()) {
            GridPoint origin = DefinitionBuilder.origin(document, ids, registry);
            instance = new Component(document.newComponentId(), SubcircuitTypes.typeIdOf(bundle.root().id()),
                    origin, Rotation.R0, Map.of(), "");
            PinBridges.Bridging bridging = PinBridges.wires(document, ids, bundle.root(), origin, registry);
            unconnectedPins = bridging.unconnectedPins();
            List<DocumentElement> added = new ArrayList<>(List.of(instance));
            added.addAll(bridging.wires());
            steps.add(new RemoveElementsCommand(ids));
            steps.add(new AddElementsCommand(added));
        }
        openDocument.execute(new CompositeCommand("command.createComponent", steps));
        if (instance != null) {
            canvas.selection().select(instance.id());
        }
        if (!unconnectedPins.isEmpty()) {
            dialogs.showMessage(canvas,
                    Messages.format("template.pinsUnconnected", String.join(", ", unconnectedPins)));
        }
        saveToLibrary(bundle);
    }

    /** Names the library and the document already use: a new component must not repeat them. */
    List<String> takenNames() {
        List<String> names = new ArrayList<>();
        service.library().entries().forEach(entry -> names.add(entry.root().name()));
        openDocument.document().definitions().forEach(definition -> names.add(definition.name()));
        return names;
    }

    Set<String> takenIds() {
        Set<String> ids = new HashSet<>(service.library().ids());
        openDocument.document().definitions().forEach(definition -> ids.add(definition.id()));
        return ids;
    }

    void saveToLibrary(DefinitionBundle bundle) {
        try {
            service.save(bundle);
        } catch (IOException exception) {
            LOGGER.warn("Could not write {} to the component library", bundle.root().id(), exception);
            dialogs.showError(canvas, service.writeError(bundle.root().id(), exception));
        }
    }

    static Action action(String name, Runnable behavior) {
        return new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent event) {
                behavior.run();
            }
        };
    }
}
