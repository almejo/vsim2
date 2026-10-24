package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.AppearanceReconciler;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.DefinitionUpdateSafety;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.library.UnsafeDefinitionUpdateException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Replaces a definition (adding missing dependencies); every instance follows. Revert restores the old one exactly. */
public final class ReplaceDefinitionCommand implements Command {

    private final ComponentDefinition replacement;
    private final PutDefinitionsCommand dependencies;
    private ComponentDefinition previous;
    private final Map<ElementId, Component> previousInstances = new LinkedHashMap<>();
    private final Map<ElementId, Component> reconciledInstances = new LinkedHashMap<>();
    private final Map<String, ComponentDefinition> previousParents = new LinkedHashMap<>();
    private final Map<String, ComponentDefinition> reconciledParents = new LinkedHashMap<>();
    private final List<String> reconciliationMessages = new ArrayList<>();

    public ReplaceDefinitionCommand(DefinitionBundle bundle) {
        this.replacement = bundle.root();
        this.dependencies = new PutDefinitionsCommand(bundle.dependencies());
    }

    @Override
    public void apply(CircuitDocument document) {
        DefinitionUpdateSafety.requireSafeReplacement(document, replacement);
        prepareOverrides(document);
        document.batch(() -> {
            dependencies.apply(document);
            previous = document.definition(replacement.id()).orElse(null);
            try {
                document.putDefinition(replacement);
                reconciledParents.values().forEach(document::putDefinition);
                reconciledInstances.values().forEach(document::replace);
            } catch (RuntimeException exception) {
                if (previous != null) {
                    document.putDefinition(previous);
                }
                previousParents.values().forEach(document::putDefinition);
                previousInstances.values().forEach(document::replace);
                dependencies.revert(document);
                throw exception;
            }
        });
    }

    @Override
    public void revert(CircuitDocument document) {
        document.batch(() -> {
            if (previous == null) {
                document.removeDefinition(replacement.id());
            } else {
                document.putDefinition(previous);
            }
            previousParents.values().forEach(document::putDefinition);
            previousInstances.values().forEach(document::replace);
            dependencies.revert(document);
        });
    }

    @Override
    public String description() {
        return "command.updateDefinition";
    }

    /** Messages about whole-override fallback; source-only reconciliation normally needs no message. */
    public List<String> reconciliationMessages() {
        return List.copyOf(reconciliationMessages);
    }

    private void prepareOverrides(CircuitDocument document) {
        previousInstances.clear();
        reconciledInstances.clear();
        previousParents.clear();
        reconciledParents.clear();
        reconciliationMessages.clear();
        Optional<ComponentDefinition> before = document.definition(replacement.id());
        if (before.isEmpty()) {
            return;
        }
        for (Component component : document.components()) {
            Component reconciled = reconcile(before.get(), component);
            if (!reconciled.equals(component)) {
                previousInstances.put(component.id(), component);
                reconciledInstances.put(component.id(), reconciled);
            }
        }
        for (ComponentDefinition parent : document.definitions()) {
            if (parent.id().equals(replacement.id())) {
                continue;
            }
            List<Component> components = parent.body().components().stream()
                    .map(component -> reconcile(before.get(), component)).toList();
            if (!components.equals(parent.body().components())) {
                CircuitFragment body = parent.body();
                ComponentDefinition reconciled = new ComponentDefinition(parent.id(), parent.name(),
                        parent.description(), parent.size(), parent.pinout(), new CircuitFragment(components,
                        body.wires(), body.labels(), body.definitions(), body.frames()), parent.appearance(),
                        parent.revision());
                previousParents.put(parent.id(), parent);
                reconciledParents.put(parent.id(), reconciled);
            }
        }
    }

    private Component reconcile(ComponentDefinition before, Component component) {
        if (!component.typeId().equals(SubcircuitTypes.typeIdOf(replacement.id()))
                || component.appearance().isEmpty()) {
            return component;
        }
        ComponentAppearance original = component.appearance().orElseThrow().canonical(before.pinout());
        AppearanceReconciler.ReconcileResult result = AppearanceReconciler.reconcile(before, replacement,
                component.appearance());
        ComponentAppearance after = result.kept().orElseGet(() -> ComponentAppearance.of(replacement));
        if (!original.size().equals(after.size()) || original.pins().size() != after.pins().size()) {
            throw unsafeOverride(component);
        }
        for (int index = 0; index < original.pins().size(); index++) {
            if (!original.pins().get(index).offset().equals(after.pins().get(index).offset())) {
                throw unsafeOverride(component);
            }
        }
        reconciliationMessages.addAll(result.messageKeys());
        return component.withAppearance(result.kept());
    }

    private UnsafeDefinitionUpdateException unsafeOverride(Component component) {
        return new UnsafeDefinitionUpdateException(replacement.id(), List.of(component.id().value()),
                "appearance.update.unsafeGeometry");
    }
}
