package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.AppearanceReconciler;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.DefinitionUpdateSafety;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.library.UnsafeDefinitionUpdateException;
import cl.almejo.vsim.core.library.UpdateNote;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.wiring.ShapeChangePlan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * «Actualizar componente» / «Actualizar desde la librería»: replaces a definition (adding missing dependencies) and
 * brings every placed piece along (spec §6.3): pins that continue keep their wires, moved pins take their wires with
 * them, a piece whose pin would touch another net keeps its old shape, pins whose wires cannot follow are left
 * unconnected ({@link #loosenedPins()}), and {@link #notes()} says what happened. A definition used inside other
 * definitions keeps its old copy when its pins or size would change ({@link DefinitionUpdateSafety#requireSafe}).
 *
 * <p>Prepared against one revision of the document, so the user can be asked about {@link #plan()} first; revert
 * restores the definitions, the pieces and the wires exactly.
 */
public final class ReplaceDefinitionCommand implements Command, LoosensPins {

    private final CircuitDocument target;
    private final long startingRevision;
    private final ComponentDefinition replacement;
    private final PutDefinitionsCommand dependencies;
    private final Map<String, ComponentDefinition> previousParents;
    private final Map<String, ComponentDefinition> reconciledParents;
    private final PlannedShapeChange pieces;
    private final List<UpdateNote> notes;
    private ComponentDefinition previous;
    private boolean wasApplied;

    private ReplaceDefinitionCommand(CircuitDocument document, DefinitionBundle bundle,
                                     Map<String, ComponentDefinition> previousParents,
                                     Map<String, ComponentDefinition> reconciledParents,
                                     PieceUpdatePlanner.Result pieces) {
        this.target = document;
        this.startingRevision = document.revision();
        this.replacement = bundle.root();
        this.dependencies = new PutDefinitionsCommand(bundle.dependencies());
        this.previousParents = previousParents;
        this.reconciledParents = reconciledParents;
        this.pieces = new PlannedShapeChange(document, pieces.plan());
        this.notes = pieces.notes();
    }

    /**
     * Plans the update of {@code bundle}'s root in {@code document} without changing it. Throws
     * {@link UnsafeDefinitionUpdateException} (nothing changed) when the definition is used inside another one and its
     * pins or size would change, or when a new pin of a piece would land on a wire or a pin.
     */
    public static ReplaceDefinitionCommand prepare(CircuitDocument document, ComponentTypeRegistry registry,
                                                   DefinitionBundle bundle) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(registry, "registry");
        ComponentDefinition replacement = bundle.root();
        DefinitionUpdateSafety.requireSafe(document, replacement);
        Optional<ComponentDefinition> before = document.definition(replacement.id());
        Map<String, ComponentDefinition> previousParents = new LinkedHashMap<>();
        Map<String, ComponentDefinition> reconciledParents = new LinkedHashMap<>();
        before.ifPresent(old -> reconcileParents(document, old, replacement, previousParents, reconciledParents));
        PieceUpdatePlanner.Result pieces = before
                .map(old -> PieceUpdatePlanner.plan(document, registry, bundle, old))
                .orElseGet(() -> new PieceUpdatePlanner.Result(PieceUpdatePlanner.emptyPlan(), List.of()));
        return new ReplaceDefinitionCommand(document, bundle, previousParents, reconciledParents, pieces);
    }

    /** What happens to the placed pieces: the pins left unconnected and the pieces that keep their old shape. */
    public ShapeChangePlan plan() {
        return pieces.plan();
    }

    /** What the update did, for the message after it; empty when nothing worth saying happened. */
    public List<UpdateNote> notes() {
        return notes;
    }

    @Override
    public List<PinRef> loosenedPins() {
        return pieces.plan().disconnected();
    }

    /** A prepared update is tied to its document and revision until its first apply. */
    public boolean isStale(CircuitDocument document) {
        return document != target || document.revision() != startingRevision;
    }

    @Override
    public void apply(CircuitDocument document) {
        if (document != target || !wasApplied && isStale(document)) {
            throw new IllegalStateException("the circuit changed after preparing the definition update");
        }
        document.batch(() -> {
            dependencies.apply(document);
            previous = document.definition(replacement.id()).orElse(null);
            try {
                document.putDefinition(replacement);
                reconciledParents.values().forEach(document::putDefinition);
            } catch (RuntimeException exception) {
                if (previous != null) {
                    document.putDefinition(previous);
                }
                previousParents.values().forEach(document::putDefinition);
                dependencies.revert(document);
                throw exception;
            }
            pieces.apply(document);
        });
        wasApplied = true;
    }

    @Override
    public void revert(CircuitDocument document) {
        document.batch(() -> {
            pieces.revert(document);
            if (previous == null) {
                document.removeDefinition(replacement.id());
            } else {
                document.putDefinition(previous);
            }
            previousParents.values().forEach(document::putDefinition);
            dependencies.revert(document);
        });
    }

    @Override
    public String description() {
        return "command.updateDefinition";
    }

    /**
     * The pieces with their own shape inside other definitions' bodies follow renamed pin sources; their pins never
     * move (the body's wires are not rewritten), so a change that would move them keeps the old copy.
     */
    private static void reconcileParents(CircuitDocument document, ComponentDefinition before,
                                         ComponentDefinition replacement,
                                         Map<String, ComponentDefinition> previousParents,
                                         Map<String, ComponentDefinition> reconciledParents) {
        for (ComponentDefinition parent : document.definitions()) {
            if (parent.id().equals(replacement.id())) {
                continue;
            }
            List<Component> components = parent.body().components().stream()
                    .map(component -> reconcileNested(before, replacement, parent, component)).toList();
            if (!components.equals(parent.body().components())) {
                CircuitFragment body = parent.body();
                previousParents.put(parent.id(), parent);
                reconciledParents.put(parent.id(), new ComponentDefinition(parent.id(), parent.name(),
                        parent.description(), parent.size(), parent.pinout(), new CircuitFragment(components,
                        body.wires(), body.labels(), body.definitions(), body.frames()), parent.appearance(),
                        parent.revision()));
            }
        }
    }

    private static Component reconcileNested(ComponentDefinition before, ComponentDefinition replacement,
                                             ComponentDefinition parent, Component component) {
        if (!component.typeId().equals(SubcircuitTypes.typeIdOf(replacement.id()))
                || component.appearance().isEmpty()) {
            return component;
        }
        ComponentAppearance original = component.appearance().orElseThrow().canonical(before.pinout());
        Optional<ComponentAppearance> kept = AppearanceReconciler.reconcile(before, replacement,
                component.appearance()).kept();
        ComponentAppearance after = kept.orElseGet(() -> ComponentAppearance.of(replacement));
        if (!sameGeometry(original, after)) {
            throw new UnsafeDefinitionUpdateException(replacement.id(), List.of(parent.name()));
        }
        return component.withAppearance(kept);
    }

    private static boolean sameGeometry(ComponentAppearance first, ComponentAppearance second) {
        if (!first.size().equals(second.size()) || first.pins().size() != second.pins().size()) {
            return false;
        }
        for (int index = 0; index < first.pins().size(); index++) {
            if (!first.pins().get(index).offset().equals(second.pins().get(index).offset())) {
                return false;
            }
        }
        return true;
    }
}
