package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.command.ChangeAppearanceCommand;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.AppearanceReconciler;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.DefinitionUpdateSafety;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.library.UnsafeDefinitionUpdateException;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.wiring.ShapeChangePlan;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryDialogs;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.window.EditorView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Per-view entry points and application of a completed appearance draft. */
final class AppearanceFeatures {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppearanceFeatures.class);
    private final EditorView view;
    private final LibraryService library;
    private final LibraryDialogs dialogs;
    private final ShapeEditor editor;
    private final AppearancePrompts prompts;
    private final List<Runnable> removals = new ArrayList<>();

    AppearanceFeatures(EditorView view, LibraryService library, LibraryDialogs dialogs, ShapeEditor editor,
                       AppearancePrompts prompts) {
        this.view = view; this.library = library; this.dialogs = dialogs; this.editor = editor; this.prompts = prompts;
    }

    void install() {
        removals.add(view.addContextMenuContributor((menu, target, selection, point) -> {
            if (selection.size() != 1 || target.isEmpty() || !(target.get() instanceof Component instance)
                    || !selection.contains(instance.id()) || SubcircuitTypes.definitionIdOf(instance.typeId()).isEmpty()) {
                return;
            }
            menu.add(item("shape.change", () -> edit(Optional.of(instance.id()),
                    SubcircuitTypes.definitionIdOf(instance.typeId()).orElseThrow(), false)));
            if (instance.appearance().isPresent()) {
                menu.add(item("shape.useDefinition", () -> clear(instance.id())));
            }
        }));
        removals.add(view.palette().addUserMenuContributor((entry, menu) -> entry.ifPresent(component ->
                menu.add(item("shape.change",
                        () -> edit(Optional.empty(), component.bundle().root().id(), component.inLibrary()))))));
        // «Actualizar componente» / «desde la librería» ask with the same connections sheet (spec §6.3 step 4).
        view.templateActions().ifPresent(actions ->
                removals.add(actions.useConnectionsConfirmation(plan -> confirm(plan, false))));
    }

    void dispose() { for (int index = removals.size() - 1; index >= 0; index--) { removals.get(index).run(); } }

    private void edit(Optional<ElementId> instanceId, String definitionId, boolean libraryScope) {
        Optional<ComponentDefinition> source = source(definitionId, libraryScope);
        if (source.isEmpty()) { return; }
        ComponentDefinition starting = source.get();
        Optional<Component> instance = instanceId.flatMap(id -> document().component(id));
        ComponentAppearance draft = instance.flatMap(Component::appearance).orElseGet(() -> ComponentAppearance.of(source.get()));
        ShapeEditScope scope = instanceId.isPresent() ? ShapeEditScope.INSTANCE : libraryScope
                ? ShapeEditScope.LIBRARY_DEFINITION : ShapeEditScope.CIRCUIT_DEFINITION;
        Optional<DefinitionBundle> librarySnapshot = library.library().find(definitionId);
        while (true) {
            boolean appliesToCircuit = !libraryScope || librarySnapshot.filter(this::matchesCircuitBundle).isPresent();
            List<String> parents = appliesToCircuit ? DefinitionUpdateSafety.containingNames(document(), definitionId) : List.of();
            int count = (int) document().components().stream()
                    .filter(piece -> piece.typeId().equals(SubcircuitTypes.typeIdOf(definitionId)))
                    .filter(piece -> piece.appearance().isEmpty()).count();
            String instanceName = instance.map(piece -> piece.label().isBlank() ? piece.id().value() : piece.label()).orElse("");
            ShapeEditRequest request = new ShapeEditRequest(starting, draft, scope, instanceId.isPresent(),
                    !parents.isEmpty(), String.join(", ", parents), count, instanceName);
            Optional<ShapeEditResult> result = editor.edit(view, request);
            if (result.isEmpty()) { return; }
            ShapeEditResult accepted = result.get();
            scope = accepted.scope(); draft = accepted.appearance();
            Optional<ComponentDefinition> latest = source(definitionId, libraryScope);
            Optional<Component> latestInstance = instanceId.flatMap(id -> document().component(id));
            if (latest.isEmpty() || instanceId.isPresent() && latestInstance.isEmpty()) {
                dialogs.showMessage(view, Messages.text("shape.stale.message")); return;
            }
            Optional<DefinitionBundle> latestBundle = library.library().find(definitionId);
            if (!latest.get().equals(starting) || !latestInstance.equals(instance)
                    || libraryScope && !latestBundle.equals(librarySnapshot)) {
                if (!prompts.reopenStale(view)) { return; }
                draft = AppearanceReconciler.reconcile(starting, latest.get(), Optional.of(draft))
                        .kept().orElseGet(() -> ComponentAppearance.of(latest.get()));
                starting = latest.get(); instance = latestInstance; librarySnapshot = latestBundle; continue;
            }
            ComponentAppearance originalEffective = instance.flatMap(Component::appearance)
                    .orElseGet(() -> ComponentAppearance.of(latest.get()));
            if (scope == ShapeEditScope.INSTANCE && draft.equals(originalEffective)
                    && request.initialScope() == ShapeEditScope.INSTANCE
                    || scope != ShapeEditScope.INSTANCE && draft.equals(ComponentAppearance.of(starting))) { return; }
            boolean writesLibrary = scope == ShapeEditScope.LIBRARY_DEFINITION || scope == ShapeEditScope.CIRCUIT_DEFINITION
                    && latestBundle.filter(this::matchesCircuitBundle).isPresent();
            Optional<DefinitionBundle> expectedLibrary = library.library().find(definitionId);
            try {
                if (scope == ShapeEditScope.LIBRARY_DEFINITION && !appliesToCircuit) {
                    ComponentDefinition changed = withAppearance(starting, draft);
                    DefinitionBundle preserved = new DefinitionBundle(changed, expectedLibrary.orElseThrow().dependencies());
                    if (saveLibrary(preserved,
                            false, expectedLibrary)) {
                        return;
                    }
                    continue;
                }
                ChangeAppearanceCommand command = scope == ShapeEditScope.INSTANCE
                        ? ChangeAppearanceCommand.forInstance(document(), view.openDocument().registry(), instanceId.orElseThrow(), Optional.of(draft))
                        : ChangeAppearanceCommand.forDefinition(document(), view.openDocument().registry(), definitionId, draft);
                if (!confirm(command.plan(), scope == ShapeEditScope.INSTANCE)) { continue; }
                if (command.isStale(document())) { continue; }
                if (writesLibrary && !library.library().find(definitionId).equals(expectedLibrary)) { continue; }
                view.openDocument().execute(command);
                if (writesLibrary) {
                    ComponentDefinition changed = document().definition(definitionId).orElseThrow();
                    saveLibrary(DefinitionBundle.of(changed, document()::definition), true, expectedLibrary);
                }
                if (!command.plan().disconnected().isEmpty()) {
                    view.statusBar().showNotice(Messages.format("shape.connections.notice", command.plan().disconnected().size()));
                }
                return;
            } catch (UnsafeDefinitionUpdateException exception) {
                dialogs.showMessage(view, Messages.format(exception.messageKey(),
                        String.join(", ", exception.containingNames())));
            }
        }
    }

    private Optional<ComponentDefinition> source(String id, boolean libraryScope) {
        return libraryScope ? library.library().find(id).map(DefinitionBundle::root) : document().definition(id);
    }

    private boolean matchesCircuitBundle(DefinitionBundle bundle) {
        return document().definition(bundle.root().id()).map(definition ->
                DefinitionBundle.of(definition, document()::definition).equals(bundle)).orElse(false);
    }

    private boolean saveLibrary(DefinitionBundle bundle, boolean circuitApplied, Optional<DefinitionBundle> expected) {
        while (true) {
            if (!library.library().find(bundle.root().id()).equals(expected)) {
                dialogs.showMessage(view, Messages.text("shape.stale.message"));
                return false;
            }
            try { library.save(bundle); return true; }
            catch (IOException exception) {
                LOGGER.warn("Could not save appearance for {}", bundle.root().id(), exception);
                if (!prompts.retryLibraryWrite(view, circuitApplied)) { return false; }
            }
        }
    }

    private void clear(ElementId instance) {
        ChangeAppearanceCommand command = ChangeAppearanceCommand.forInstance(document(), view.openDocument().registry(),
                instance, Optional.empty());
        if (confirm(command.plan(), true) && !command.isStale(document())) { view.openDocument().execute(command); }
    }

    private boolean confirm(ShapeChangePlan plan, boolean instanceScope) {
        if (plan.disconnected().isEmpty() && plan.keptOldShape().isEmpty()) { return true; }
        List<String> disconnected = plan.disconnected().stream().map(this::pinName).toList();
        List<String> kept = plan.keptOldShape().stream().map(contact -> Messages.format("shape.connections.foreign",
                contact.instance().value(), contact.pinName(), contact.touchedNetName())).toList();
        return prompts.confirmConnections(view, disconnected, kept, instanceScope && !kept.isEmpty());
    }

    private String pinName(PinRef pin) {
        return document().component(pin.componentId()).map(component -> component.id().value() + " · "
                + view.openDocument().registry().typeOf(component).pin(pin.pinIndex()).name()).orElse(pin.componentId().value());
    }

    private static ComponentDefinition withAppearance(ComponentDefinition definition, ComponentAppearance appearance) {
        ComponentAppearance canonical = appearance.canonical(definition.pinout());
        List<DefinitionPin> pins = new ArrayList<>();
        for (int index = 0; index < definition.pinout().size(); index++) {
            DefinitionPin pin = definition.pinout().get(index); PinSpec spec = pin.spec();
            pins.add(new DefinitionPin(new PinSpec(spec.name(), canonical.pins().get(index).offset(),
                    spec.direction(), spec.width()), pin.source()));
        }
        return definition.withGeometry(canonical.size(), pins, canonical.drawing());
    }

    private CircuitDocument document() { return view.openDocument().document(); }

    private JMenuItem item(String key, Runnable action) {
        return new JMenuItem(new AbstractAction(Messages.text(key)) {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }
}
