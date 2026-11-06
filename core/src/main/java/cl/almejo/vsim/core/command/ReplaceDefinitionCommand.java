package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.AppearanceReconciler;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.DefinitionUpdateProblem;
import cl.almejo.vsim.core.library.DefinitionUpdateSafety;
import cl.almejo.vsim.core.library.KeptOldShape;
import cl.almejo.vsim.core.library.RemovedPinWire;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.library.UnsafeDefinitionUpdateException;
import cl.almejo.vsim.core.library.UpdateNote;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.wiring.ShapeChangePlan;

import java.util.ArrayList;
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
    private final List<KeptOldShape> keptOldShapes;
    private final List<DefinitionUpdateProblem> nestedProblems;
    private final List<RemovedPinWire> removedPinWires;
    private ComponentDefinition previous;
    private boolean wasApplied;

    private ReplaceDefinitionCommand(CircuitDocument document, DefinitionBundle bundle,
                                     Map<String, ComponentDefinition> previousParents,
                                     Map<String, ComponentDefinition> reconciledParents,
                                     PieceUpdatePlanner.Result pieces,
                                     List<DefinitionUpdateProblem> nestedProblems,
                                     List<RemovedPinWire> removedPinWires) {
        this.target = document;
        this.startingRevision = document.revision();
        this.replacement = bundle.root();
        this.dependencies = new PutDefinitionsCommand(bundle.dependencies());
        this.previousParents = previousParents;
        this.reconciledParents = reconciledParents;
        this.pieces = new PlannedShapeChange(document, pieces.plan());
        this.notes = pieces.notes();
        this.keptOldShapes = pieces.keptOldShapes();
        this.nestedProblems = List.copyOf(nestedProblems);
        this.removedPinWires = List.copyOf(removedPinWires);
    }

    /**
     * Plans the update of {@code bundle}'s root in {@code document} without changing it. Throws
     * {@link UnsafeDefinitionUpdateException} (nothing changed) when the definition is used inside another one and its
     * pins or size would change, or when a new pin of a piece would land on a wire or a pin.
     */
    public static ReplaceDefinitionCommand prepare(CircuitDocument document, ComponentTypeRegistry registry,
                                                   DefinitionBundle bundle) {
        return prepare(document, registry, bundle, true);
    }

    /**
     * Plans the write-back of an edit made inside the component (spec §6.2): like {@link #prepare}, but nothing is
     * refused. A new pin whose spot lands on a wire or a pin moves that piece's pin elsewhere on its edge — the piece
     * keeps its old shape and is listed in {@link #keptOldShapes()} —, wires that cannot follow are left loose
     * ({@link #loosenedPins()}), and the bodies of the definitions that contain this one are rewritten with the same
     * planning (spec §6.2 c): their pieces of it take the new pins, their wires follow, and what cannot follow there is
     * listed in {@link #nestedProblems()}. Their own pins never change (only their pieces of this definition and their
     * wires move), so nothing above them has to follow; every definition goes back with {@link #revert}.
     */
    public static ReplaceDefinitionCommand prepareInside(CircuitDocument document, ComponentTypeRegistry registry,
                                                         DefinitionBundle bundle) {
        return prepare(document, registry, bundle, false);
    }

    private static ReplaceDefinitionCommand prepare(CircuitDocument document, ComponentTypeRegistry registry,
                                                    DefinitionBundle bundle, boolean refuseBusyEdges) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(registry, "registry");
        ComponentDefinition replacement = bundle.root();
        if (refuseBusyEdges) {
            DefinitionUpdateSafety.requireSafe(document, replacement);
        }
        Optional<ComponentDefinition> before = document.definition(replacement.id());
        Map<String, ComponentDefinition> previousParents = new LinkedHashMap<>();
        Map<String, ComponentDefinition> reconciledParents = new LinkedHashMap<>();
        List<DefinitionUpdateProblem> nestedProblems = new ArrayList<>();
        List<RemovedPinWire> removedPinWires = new ArrayList<>();
        before.ifPresent(old -> {
            if (refuseBusyEdges) {
                reconcileParents(document, old, replacement, previousParents, reconciledParents);
            } else {
                rewriteContainingBodies(document, registry, bundle, old, previousParents, reconciledParents,
                        nestedProblems, removedPinWires);
                removedPinWires.addAll(PieceUpdatePlanner.removedPinWires(document, registry, old, replacement,
                        Optional.empty()));
            }
        });
        PieceUpdatePlanner.Result pieces = before
                .map(old -> PieceUpdatePlanner.plan(document, registry, bundle, old, refuseBusyEdges))
                .orElseGet(() -> new PieceUpdatePlanner.Result(PieceUpdatePlanner.emptyPlan(), List.of()));
        return new ReplaceDefinitionCommand(document, bundle, previousParents, reconciledParents, pieces,
                nestedProblems, removedPinWires);
    }

    /**
     * Spec §6.2 c: every definition whose body places pieces of {@code before} gets them planned as the circuit's own
     * pieces are ({@link PieceUpdatePlanner}, never refusing), on a copy of its body. A piece drawn as its definition
     * follows through the definition alone; a body whose pieces or wires had to change (a piece with its own shape, a
     * new pin put elsewhere, wires that follow a moved pin) is written back as a new revision of that definition with
     * the same outside and its element ids unchanged (the pieces' own memory contents stay keyed by the same paths). Placed or left over, every containing body follows, so no
     * copy keeps the old pins. Nothing changes when the pins and the size are the same (an edit of the logic only).
     */
    private static void rewriteContainingBodies(CircuitDocument document, ComponentTypeRegistry registry,
                                                DefinitionBundle bundle, ComponentDefinition before,
                                                Map<String, ComponentDefinition> previousParents,
                                                Map<String, ComponentDefinition> rewrittenParents,
                                                List<DefinitionUpdateProblem> nestedProblems,
                                                List<RemovedPinWire> removedPinWires) {
        ComponentDefinition replacement = bundle.root();
        if (before.size().equals(replacement.size()) && before.pinout().equals(replacement.pinout())) {
            return;
        }
        for (ComponentDefinition parent : document.definitions()) {
            if (parent.id().equals(replacement.id()) || !parent.usedDefinitionIds().contains(replacement.id())) {
                continue;
            }
            CircuitDocument body = parent.bodyDocument(document);
            removedPinWires.addAll(PieceUpdatePlanner.removedPinWires(body, registry, before, replacement,
                    Optional.of(parent.id())));
            PieceUpdatePlanner.Result planned = PieceUpdatePlanner.plan(body, registry, bundle, before, false);
            planned.plan().disconnected().forEach(pin -> nestedProblems.add(new DefinitionUpdateProblem(parent.id(),
                    pin, DefinitionUpdateProblem.Kind.LOOSENED)));
            for (KeptOldShape kept : planned.keptOldShapes()) {
                for (String pinName : kept.newPinNames()) {
                    nestedProblems.add(new DefinitionUpdateProblem(parent.id(),
                            new PinRef(kept.instance(), replacement.pinIndexOf(pinName).orElseThrow()),
                            DefinitionUpdateProblem.Kind.PLACED_ELSEWHERE));
                }
            }
            PlannedShapeChange change = new PlannedShapeChange(body, planned.plan());
            if (change.changesNothing()) {
                continue; // its pieces follow through the definition alone: the body itself stays as it is
            }
            change.apply(body);
            CircuitFragment original = parent.body();
            previousParents.put(parent.id(), parent);
            rewrittenParents.put(parent.id(), parent.withBody(new CircuitFragment(body.components(),
                    body.wires(), body.labels(), original.definitions(), body.frames(), body.stickyNotes()))
                    .revisedFrom(parent));
        }
    }

    /**
     * For an inside edit, the placed pieces that could not take a new pin where the definition puts it (spec §6.2 a);
     * always empty for {@link #prepare}, which refuses instead.
     */
    public List<KeptOldShape> keptOldShapes() {
        return keptOldShapes;
    }

    /**
     * For an inside edit, what the rewrite of the bodies that contain the definition could not carry (spec §6.2 c):
     * pins left unconnected and new pins put elsewhere, each with the definition whose body holds the piece. Always
     * empty for {@link #prepare}.
     */
    public List<DefinitionUpdateProblem> nestedProblems() {
        return nestedProblems;
    }

    /**
     * For an inside edit, the wire ends a removed pin left loose (spec §6.1), on the placed pieces and in the bodies
     * that contain the definition. Always empty for {@link #prepare}.
     */
    public List<RemovedPinWire> removedPinWires() {
        return removedPinWires;
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
     * «Actualizar componente…»: the pieces with their own shape inside other definitions' bodies follow renamed pin
     * sources; their pins never move (the body's wires are not rewritten here), so a change that would move them keeps
     * the old copy. An inside edit rewrites those bodies instead ({@code rewriteContainingBodies}).
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
                reconciledParents.put(parent.id(), parent.withBody(new CircuitFragment(components,
                        body.wires(), body.labels(), body.definitions(), body.frames(), body.stickyNotes())));
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
                component.appearance());
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
