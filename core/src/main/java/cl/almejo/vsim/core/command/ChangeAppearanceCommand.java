package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.DefinitionUpdateSafety;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.wiring.ShapeChangePlan;
import cl.almejo.vsim.core.wiring.ShapeChangePlanner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One prepared shape edit, including every affected instance and its wires. Undo restores geometry, definition
 * revision, wire ids and paint order; redo replays the same plan rather than routing against a different layout.
 */
public final class ChangeAppearanceCommand implements Command, LoosensPins {
    private final CircuitDocument target;
    private final long startingRevision;
    private final Optional<ComponentDefinition> previousDefinition;
    private final Optional<ComponentDefinition> replacementDefinition;
    private final PlannedShapeChange pieces;
    private boolean wasApplied;

    private ChangeAppearanceCommand(CircuitDocument document, Optional<ComponentDefinition> previousDefinition,
                                     Optional<ComponentDefinition> replacementDefinition, ShapeChangePlan plan) {
        target = document;
        startingRevision = document.revision();
        this.previousDefinition = previousDefinition;
        this.replacementDefinition = replacementDefinition;
        this.pieces = new PlannedShapeChange(document, plan);
    }

    /** A top-level instance's own shape, or empty to restore its definition's appearance. */
    public static ChangeAppearanceCommand forInstance(CircuitDocument document, ComponentTypeRegistry registry,
                                                       ElementId instanceId,
                                                       Optional<ComponentAppearance> appearance) {
        Objects.requireNonNull(appearance, "appearance");
        Component instance = document.component(instanceId).orElseThrow();
        ComponentDefinition definition = SubcircuitTypes.definitionOf(registry.typeOf(instance)).orElseThrow(
                () -> new IllegalArgumentException("only user components have editable appearances"));
        Component replacement = instance.withAppearance(appearance.map(value -> value.canonical(definition.pinout())));
        CircuitDocument snapshot = document.copy();
        ComponentTypeRegistry frozen = registryFor(registry, snapshot);
        ShapeChangePlan plan = ShapeChangePlanner.plan(document, frozen, frozen, Map.of(instanceId, replacement));
        return new ChangeAppearanceCommand(document, Optional.empty(), Optional.empty(), plan);
    }

    /** Changes a circuit definition; pieces with their own shape keep it. Nested interface geometry is locked. */
    public static ChangeAppearanceCommand forDefinition(CircuitDocument document, ComponentTypeRegistry registry,
                                                         String definitionId, ComponentAppearance appearance) {
        ComponentDefinition before = document.definition(definitionId).orElseThrow();
        ComponentAppearance canonical = appearance.canonical(before.pinout());
        List<DefinitionPin> pins = new ArrayList<>();
        for (int index = 0; index < before.pinout().size(); index++) {
            DefinitionPin original = before.pinout().get(index);
            PinSpec spec = original.spec();
            pins.add(new DefinitionPin(new PinSpec(spec.name(), canonical.pins().get(index).offset(),
                    spec.direction(), spec.width()), original.source()));
        }
        ComponentDefinition replacement = before.withGeometry(canonical.size(), pins, canonical.drawing());
        DefinitionUpdateSafety.requireSafe(document, replacement);
        CircuitDocument snapshot = document.copy();
        CircuitDocument after = snapshot.copy();
        after.putDefinition(replacement);
        Map<ElementId, Component> instances = new LinkedHashMap<>();
        document.components().stream()
                .filter(instance -> instance.typeId().equals(SubcircuitTypes.typeIdOf(definitionId)))
                .filter(instance -> instance.appearance().isEmpty())
                .forEach(instance -> instances.put(instance.id(), instance));
        ShapeChangePlan plan = ShapeChangePlanner.plan(document, registryFor(registry, snapshot),
                registryFor(registry, after), instances);
        return new ChangeAppearanceCommand(document, Optional.of(before), Optional.of(replacement), plan);
    }

    /** {@code base} resolving the user components of {@code document} (a frozen copy, never the live one). */
    static ComponentTypeRegistry registryFor(ComponentTypeRegistry base, CircuitDocument document) {
        ComponentTypeRegistry registry = base.copy();
        registry.addResolver(SubcircuitTypes.resolverFor(document));
        return registry;
    }

    public ShapeChangePlan plan() { return pieces.plan(); }

    @Override
    public List<PinRef> loosenedPins() { return pieces.plan().disconnected(); }

    /** A prepared plan is tied to its source document and wiring revision until its first apply. */
    public boolean isStale(CircuitDocument document) {
        return document != target || document.revision() != startingRevision;
    }

    @Override
    public boolean changesNothing() {
        return previousDefinition.equals(replacementDefinition) && pieces.changesNothing();
    }

    @Override
    public void apply(CircuitDocument document) {
        if (document != target || !wasApplied && isStale(document)) {
            throw new IllegalStateException("the circuit changed after preparing its appearance edit");
        }
        document.batch(() -> {
            replacementDefinition.ifPresent(document::putDefinition);
            pieces.apply(document);
        });
        wasApplied = true;
    }

    @Override
    public void revert(CircuitDocument document) {
        if (document != target) {
            throw new IllegalArgumentException("appearance edit belongs to another circuit");
        }
        document.batch(() -> {
            previousDefinition.ifPresent(document::putDefinition);
            pieces.revert(document);
        });
    }

    @Override
    public String description() { return "command.changeAppearance"; }
}
