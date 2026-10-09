package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.CompositeCommand;
import cl.almejo.vsim.core.command.PasteCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.command.ReplaceDefinitionCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBuilder;
import cl.almejo.vsim.core.library.DefinitionGraph;
import cl.almejo.vsim.core.library.Disassembly;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.LibraryVersions;
import cl.almejo.vsim.core.library.PinBridges;
import cl.almejo.vsim.core.library.PinOrder;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.actions.ContextMenuContributor;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenuItem;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Template editing for one canvas (spec §5.6, §6.6): create from the selection, update a definition from the
 * selection, disassemble an instance and take the library's version. Each is one undo step.
 */
public final class TemplateActions {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateActions.class);

    private final OpenDocument openDocument;
    private final CircuitCanvas canvas;
    private final LibraryService service;
    private final LibraryDialogs dialogs;
    private final Action createComponent;
    private final Action updateComponent;
    private final Runnable refresher = this::refresh;

    public TemplateActions(OpenDocument openDocument, CircuitCanvas canvas, LibraryService service,
                           LibraryDialogs dialogs) {
        this.openDocument = openDocument;
        this.canvas = canvas;
        this.service = service;
        this.dialogs = dialogs;
        this.createComponent = action(Messages.text("action.createComponent"), this::createFromSelection);
        this.updateComponent = action(Messages.text("action.updateComponent"), this::updateFromSelection);
        Icons.install(createComponent, TemplateIcons.CREATE_COMPONENT);
        Icons.install(updateComponent, TemplateIcons.UPDATE_COMPONENT);
        canvas.selection().addListener(refresher);
        openDocument.addChangeListener(refresher);
        refresh();
    }

    public Action createComponent() {
        return createComponent;
    }

    /** Enabled when the selection holds interface pins and the document has definitions (overview M4-A). */
    public Action updateComponent() {
        return updateComponent;
    }

    /** Stops following the shared document (the selection dies with the view). */
    public void dispose() {
        openDocument.removeChangeListener(refresher);
    }

    /**
     * Without interface pins the dialog does not open and a message says why. Otherwise, once accepted: one undo step
     * that adds the definitions and, if asked, replaces the selection by one instance at its top-left, with
     * {@link PinBridges} keeping what was connected to the interface pins from outside (pins it cannot connect safely
     * are named in a message). When an instance pin would land on something outside, the selection is kept and only
     * the definitions are added, with a message. Then the bundle is written to the library (a write failure is
     * reported, the circuit keeps the component).
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

    /**
     * "Actualizar componente…": the user picks the definition to replace in the same pinout dialog; every instance,
     * including those of definitions that nest it, follows in one undo step. A selection that would nest the target,
     * directly or through other components, is refused with a message before anything changes. The library file is
     * rewritten when the library has the definition.
     */
    public void updateFromSelection() {
        CircuitDocument document = openDocument.document();
        Set<ElementId> ids = canvas.selection().ids();
        if (document.definitions().isEmpty() || ids.isEmpty()) {
            return;
        }
        if (!DefinitionBuilder.hasInterfacePins(document, ids)) {
            dialogs.showMessage(canvas, Messages.text("template.noInterfacePins"));
            return;
        }
        PinOrder order = DefinitionBuilder.defaultPinOrder(document, ids);
        ComponentForm form = ComponentForm.update(order, DefinitionBuilder.pinNames(document, order), takenNames(),
                document.definitions());
        dialogs.editComponent(canvas, form).ifPresent(result -> update(ids, result));
    }

    /**
     * "Desarmar": one undo step that removes the instance, pastes its body (interface pins included) unrotated with
     * the body's top-left at the instance's top-left, and bridges each instance pin that had an outside connection to
     * its pasted interface pin ({@link Disassembly}); the pasted parts become the selection. When the body would land
     * on a foreign wire or pin nothing changes and a message says so; pins that cannot be bridged are named.
     */
    public void disassemble(Component instance) {
        ComponentTypeRegistry registry = openDocument.registry();
        Optional<ComponentDefinition> definition = SubcircuitTypes.definitionOf(registry.typeOf(instance));
        if (definition.isEmpty()) {
            return;
        }
        CircuitDocument document = openDocument.document();
        Disassembly.Plan plan = Disassembly.plan(document, instance, definition.get(), registry);
        if (plan.blocked()) {
            dialogs.showMessage(canvas, Messages.format("template.notDisassembled",
                    Messages.format("library.place.componentName", definition.get().name())));
            return;
        }
        PasteCommand paste = new PasteCommand(definition.get().body(), plan.offset());
        List<Command> steps = new ArrayList<>(List.of(new RemoveElementsCommand(List.of(instance.id())), paste));
        if (!plan.bridges().isEmpty()) {
            steps.add(new AddElementsCommand(List.copyOf(plan.bridges())));
        }
        openDocument.execute(new CompositeCommand("command.disassemble", steps));
        canvas.selection().set(paste.pastedIds());
        if (!plan.unconnectedPins().isEmpty()) {
            dialogs.showMessage(canvas, Messages.format("template.pinsUnconnected",
                    String.join(", ", plan.unconnectedPins())));
        }
    }

    /**
     * "Actualizar desde la librería": the document's copy becomes the library's version, one undo step. The circuit
     * keeps its own versions of nested definitions (a message names the ones that differ); a library version that
     * would then nest itself is refused with a message.
     */
    public void updateFromLibrary(Component instance) {
        Optional<DefinitionBundle> found = definitionIdOf(instance).flatMap(id -> service.library().find(id));
        if (found.isEmpty()) {
            return;
        }
        DefinitionBundle bundle = found.get();
        CircuitDocument document = openDocument.document();
        if (wouldNestItself(document, bundle)) {
            dialogs.showError(canvas, Messages.text("template.updateFromLibrary.recursive"));
            return;
        }
        KeptVersions.warn(document, bundle, dialogs, canvas);
        openDocument.execute(new ReplaceDefinitionCommand(bundle));
    }

    /** True when the library holds a different version of the instance's definition (the badge shows). */
    public boolean isOutdated(Component instance) {
        return definitionIdOf(instance)
                .flatMap(id -> openDocument.document().definition(id))
                .map(definition -> !LibraryVersions.outdated(List.of(definition), service.library()).isEmpty())
                .orElse(false);
    }

    /**
     * Context menu: "Crear componente desde selección…" (and "Actualizar componente…" when enabled) on a
     * multi-selection or a selection with interface pins; "Desarmar" and, when outdated, "Actualizar desde la
     * librería" on a single instance.
     */
    public ContextMenuContributor contributor() {
        return (menu, target, selection, gridPosition) -> {
            if (target.isEmpty() || !selection.contains(target.get().id())) {
                return;
            }
            boolean multiple = selection.size() > 1;
            if (multiple || DefinitionBuilder.hasInterfacePins(openDocument.document(), selection.ids())) {
                menu.add(new JMenuItem(createComponent));
                if (updateComponent.isEnabled()) {
                    menu.add(new JMenuItem(updateComponent));
                }
            }
            if (!multiple && target.get() instanceof Component instance && definitionIdOf(instance).isPresent()) {
                menu.add(new JMenuItem(disassembleAction(instance)));
                if (isOutdated(instance)) {
                    menu.add(new JMenuItem(updateFromLibraryAction(instance)));
                }
            }
        };
    }

    /** "Desarmar" for {@code instance}, with its icon. */
    Action disassembleAction(Component instance) {
        Action action = action(Messages.text("action.disassemble"), () -> disassemble(instance));
        Icons.install(action, TemplateIcons.DISASSEMBLE);
        return action;
    }

    /** "Actualizar desde la librería" for {@code instance}, with its icon. */
    Action updateFromLibraryAction(Component instance) {
        Action action = action(Messages.text("action.updateFromLibrary"), () -> updateFromLibrary(instance));
        Icons.install(action, TemplateIcons.UPDATE_FROM_LIBRARY);
        return action;
    }

    void refresh() {
        Set<ElementId> ids = canvas.selection().ids();
        CircuitDocument document = openDocument.document();
        createComponent.setEnabled(!ids.isEmpty());
        updateComponent.setEnabled(!document.definitions().isEmpty()
                && DefinitionBuilder.hasInterfacePins(document, ids));
    }

    private void create(Set<ElementId> ids, ComponentForm.Result result) {
        CircuitDocument document = openDocument.document();
        ComponentTypeRegistry registry = openDocument.registry();
        DefinitionBundle bundle = DefinitionBuilder.fromSelection(document, ids, result.definitionId(), result.name(),
                result.pinOrder(), registry);
        List<Command> steps = new ArrayList<>();
        steps.add(new PutDefinitionsCommand(bundle.all()));
        Component instance = null;
        String message = null;
        if (result.replaceSelection()) {
            GridPoint origin = DefinitionBuilder.origin(document, ids, registry);
            PinBridges.Bridging bridging = PinBridges.wires(document, ids, bundle.root(), origin, registry);
            if (!bridging.blockedPins().isEmpty()) {
                message = Messages.format("template.notReplaced", String.join(", ", bridging.blockedPins()));
            } else {
                instance = new Component(document.newComponentId(), SubcircuitTypes.typeIdOf(bundle.root().id()),
                        origin, Rotation.R0, Map.of(), "");
                List<DocumentElement> added = new ArrayList<>(List.of(instance));
                added.addAll(bridging.wires());
                steps.add(new RemoveElementsCommand(ids));
                steps.add(new AddElementsCommand(added));
                if (!bridging.unconnectedPins().isEmpty()) {
                    message = Messages.format("template.pinsUnconnected",
                            String.join(", ", bridging.unconnectedPins()));
                }
            }
        }
        openDocument.execute(new CompositeCommand("command.createComponent", steps));
        if (instance != null) {
            canvas.selection().select(instance.id());
        }
        if (message != null) {
            dialogs.showMessage(canvas, message);
        }
        saveToLibrary(bundle);
    }

    private void update(Set<ElementId> ids, ComponentForm.Result result) {
        CircuitDocument document = openDocument.document();
        if (nests(document, ids, result.definitionId())) {
            dialogs.showError(canvas, Messages.text("template.update.recursive"));
            return;
        }
        DefinitionBundle bundle = DefinitionBuilder.fromSelection(document, ids, result.definitionId(), result.name(),
                result.pinOrder(), openDocument.registry());
        openDocument.execute(new ReplaceDefinitionCommand(bundle));
        if (service.library().find(bundle.root().id()).isPresent()) {
            saveToLibrary(bundle);
        }
    }

    /**
     * True when {@code bundle}'s root, with the document's versions of the definitions it already has and the
     * bundle's for the others, would reach itself.
     */
    private static boolean wouldNestItself(CircuitDocument document, DefinitionBundle bundle) {
        String rootId = bundle.root().id();
        Map<String, ComponentDefinition> shipped = new HashMap<>();
        bundle.dependencies().forEach(dependency -> shipped.put(dependency.id(), dependency));
        List<ComponentDefinition> reached = DefinitionGraph.knownClosure(bundle.root().usedDefinitionIds(),
                id -> id.equals(rootId) ? Optional.empty()
                        : document.definition(id).or(() -> Optional.ofNullable(shipped.get(id))));
        return reached.stream().anyMatch(definition -> definition.usedDefinitionIds().contains(rootId));
    }

    /** True when the selected components instantiate {@code definitionId}, directly or through other definitions. */
    private static boolean nests(CircuitDocument document, Set<ElementId> ids, String definitionId) {
        List<Component> selected = ids.stream().map(document::component).flatMap(Optional::stream).toList();
        return document.definitionsUsedBy(selected).stream()
                .anyMatch(definition -> definition.id().equals(definitionId));
    }

    private Optional<String> definitionIdOf(Component instance) {
        return SubcircuitTypes.definitionIdOf(instance.typeId())
                .filter(id -> openDocument.document().definition(id).isPresent());
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
