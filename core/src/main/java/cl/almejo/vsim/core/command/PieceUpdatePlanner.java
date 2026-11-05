package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.PinPlacement;
import cl.almejo.vsim.core.component.appearance.SymbolDrawing;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.AppearanceReconciler;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBuilder;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.KeptOldShape;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.library.UnsafeDefinitionUpdateException;
import cl.almejo.vsim.core.library.UpdateNote;
import cl.almejo.vsim.core.wiring.FollowPlan;
import cl.almejo.vsim.core.wiring.ForeignContact;
import cl.almejo.vsim.core.wiring.ShapeChangePlan;
import cl.almejo.vsim.core.wiring.ShapeChangePlanner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * How the top-level pieces of a definition follow an update (spec §6.3), in two steps so that
 * {@link ShapeChangePlanner} only ever sees pieces with the same pins before and after:
 * <ol>
 *   <li>Each piece first takes its <em>old shape</em> on the new pinout: the pins that continue (by source, else by
 *   name, direction and width) stay exactly where they were, removed pins disappear (their wires stay) and new pins go
 *   to a free spot on the box edge. Nothing moves, so no wire has to follow; a new pin that would land on a wire or
 *   a pin refuses the whole update, since it would join a net silently.</li>
 *   <li>From there each piece goes to its final shape — the new definition's, or its own shape reconciled — through
 *   the planner: the wires follow, a piece whose pin would touch another net keeps the old shape of step 1 as its
 *   own, and pins whose wires cannot follow are left unconnected.</li>
 * </ol>
 */
final class PieceUpdatePlanner {

    /**
     * The plan for the pieces (empty when there are none), the lines that tell the user what happened and, for an
     * inside edit, the pieces that could not take a new pin where the definition puts it.
     */
    record Result(ShapeChangePlan plan, List<UpdateNote> notes, List<KeptOldShape> keptOldShapes) {
        Result {
            notes = List.copyOf(notes);
            keptOldShapes = List.copyOf(keptOldShapes);
        }

        Result(ShapeChangePlan plan, List<UpdateNote> notes) {
            this(plan, notes, List.of());
        }
    }

    /**
     * How many times the box of a piece that keeps its old shape may grow by one pin spacing while looking for a spot
     * that touches nothing; far more rows than the wires around one piece ever cover.
     */
    private static final int MAXIMUM_GROWTH = 64;

    private PieceUpdatePlanner() {
    }

    /**
     * Plans against {@code document} as it is, which is never changed. With {@code refuseBusyEdges} («Actualizar
     * componente…») a new pin that would land on a wire or a pin refuses the update; without it (an edit inside the
     * component, spec §6.2 a) that piece keeps its old shape, its new pins go to a spot of its edge that touches
     * nothing, and it is listed in {@link Result#keptOldShapes()}.
     */
    static Result plan(CircuitDocument document, ComponentTypeRegistry registry, DefinitionBundle bundle,
                       ComponentDefinition before, boolean refuseBusyEdges) {
        ComponentDefinition replacement = bundle.root();
        List<UpdateNote> notes = new ArrayList<>(definitionNotes(before, replacement));
        List<Component> pieces = document.components().stream()
                .filter(component -> component.typeId().equals(SubcircuitTypes.typeIdOf(replacement.id())))
                .toList();
        if (pieces.isEmpty()) {
            return new Result(emptyPlan(), notes);
        }
        CircuitDocument intermediate = document.copy();
        new PutDefinitionsCommand(bundle.dependencies()).apply(intermediate);
        intermediate.putDefinition(replacement);
        Map<ElementId, Component> targets = new LinkedHashMap<>();
        for (Component piece : pieces) {
            intermediate.replace(piece.withAppearance(Optional.of(oldShape(before, replacement, piece))));
            targets.put(piece.id(), target(before, replacement, piece, notes));
        }
        ComponentTypeRegistry frozen = ChangeAppearanceCommand.registryFor(registry, intermediate);
        List<KeptOldShape> keptOldShapes = new ArrayList<>();
        if (refuseBusyEdges) {
            requireFreeNewPins(intermediate, frozen, before, replacement, pieces);
        } else {
            for (Component piece : pieces) {
                keepOldShapeIfBusy(intermediate, frozen, before, replacement, piece.id()).ifPresent(kept -> {
                    keptOldShapes.add(kept);
                    targets.put(piece.id(), intermediate.component(piece.id()).orElseThrow());
                });
            }
        }
        ShapeChangePlan plan = ShapeChangePlanner.plan(intermediate, frozen, frozen, targets);
        for (ForeignContact contact : plan.keptOldShape()) {
            notes.add(new UpdateNote("appearance.update.keptOldShape",
                    List.of(nameOf(document.component(contact.instance()).orElseThrow()))));
        }
        return new Result(plan, notes, keptOldShapes);
    }

    /**
     * When a new pin of the piece {@code pieceId} (in its old shape, in {@code intermediate}) lands on a wire or a
     * pin, moves each such pin to the first spot of its side that touches nothing, the box growing downwards while
     * none does; the piece keeps that shape as its own (it is its target too) and is reported. Empty when no new pin
     * of it touches anything.
     */
    private static Optional<KeptOldShape> keepOldShapeIfBusy(CircuitDocument intermediate,
                                                             ComponentTypeRegistry registry,
                                                             ComponentDefinition before,
                                                             ComponentDefinition replacement, ElementId pieceId) {
        List<String> moved = new ArrayList<>();
        for (int index : newPinIndexes(before, replacement)) {
            Component piece = intermediate.component(pieceId).orElseThrow();
            if (touchesSomething(intermediate, registry, piece, registry.typeOf(piece).pinLocation(piece, index))) {
                moveToAQuietSpot(intermediate, registry, replacement, pieceId, index);
                moved.add(replacement.pinout().get(index).spec().name());
            }
        }
        return moved.isEmpty() ? Optional.empty()
                : Optional.of(new KeptOldShape(pieceId, replacement.id(), moved));
    }

    /** Puts pin {@code index} of the piece on the first spot of its side that touches nothing (see above). */
    private static void moveToAQuietSpot(CircuitDocument intermediate, ComponentTypeRegistry registry,
                                         ComponentDefinition replacement, ElementId pieceId, int index) {
        Component piece = intermediate.component(pieceId).orElseThrow();
        ComponentAppearance shape = piece.appearance().orElseThrow();
        boolean input = replacement.pinout().get(index).spec().isInput();
        Set<GridPoint> others = new HashSet<>();
        for (int other = 0; other < shape.pins().size(); other++) {
            if (other != index) {
                others.add(shape.pins().get(other).offset());
            }
        }
        GridSize size = shape.size();
        for (int growth = 0; growth <= MAXIMUM_GROWTH; growth++) {
            for (GridPoint spot : AppearanceReconciler.freeEdgeSpots(input, size, others)) {
                Component candidate = piece.withAppearance(Optional.of(withPinAt(shape, size, index, spot)));
                intermediate.replace(candidate);
                if (!touchesSomething(intermediate, registry, candidate,
                        registry.typeOf(candidate).pinLocation(candidate, index))) {
                    return;
                }
            }
            size = new GridSize(size.width(), Math.addExact(size.height(), DefinitionBuilder.PIN_SPACING));
        }
        intermediate.replace(piece);
    }

    /** {@code shape} on a {@code size} box (its drawing too) with pin {@code index} at {@code spot}. */
    private static ComponentAppearance withPinAt(ComponentAppearance shape, GridSize size, int index,
                                                 GridPoint spot) {
        List<PinPlacement> pins = new ArrayList<>(shape.pins());
        pins.set(index, new PinPlacement(pins.get(index).source(), spot));
        Optional<SymbolDrawing> drawing = shape.drawing().map(original ->
                new SymbolDrawing(size, original.elements(), original.showPinNames()));
        return new ComponentAppearance(size, drawing, pins);
    }

    /** The indexes of {@code replacement}'s pins that {@code before} does not have. */
    private static List<Integer> newPinIndexes(ComponentDefinition before, ComponentDefinition replacement) {
        List<Optional<DefinitionPin>> previous = AppearanceReconciler.previousPins(before, replacement);
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < previous.size(); index++) {
            if (previous.get(index).isEmpty()) {
                indexes.add(index);
            }
        }
        return indexes;
    }

    /** The piece's current shape on the new pinout: continuing pins in place, new ones on a free edge spot. */
    private static ComponentAppearance oldShape(ComponentDefinition before, ComponentDefinition replacement,
                                                Component piece) {
        ComponentAppearance current = piece.appearance().orElseGet(() -> ComponentAppearance.of(before));
        return AppearanceReconciler.reconcile(before, replacement, Optional.of(current))
                .orElseThrow(() -> new IllegalStateException("the current shape of " + piece.id().value()
                        + " cannot take the new pins of " + replacement.id()));
    }

    /** The definition's shape, or the piece's own shape reconciled (dropped whole when it is no longer valid). */
    private static Component target(ComponentDefinition before, ComponentDefinition replacement, Component piece,
                                    List<UpdateNote> notes) {
        if (piece.appearance().isEmpty()) {
            return piece;
        }
        Optional<ComponentAppearance> kept = AppearanceReconciler.reconcile(before, replacement,
                piece.appearance());
        if (kept.isEmpty()) {
            notes.add(new UpdateNote("appearance.update.dropped", List.of(nameOf(piece))));
        } else {
            newPins(before, replacement).forEach(pin -> notes.add(new UpdateNote("appearance.update.newPinOwnShape",
                    List.of(nameOf(piece), pin.spec().name()))));
        }
        return piece.withAppearance(kept);
    }

    /** Refuses the update when a new pin of a piece would land on a wire or on another pin (spec §6.4 rule). */
    private static void requireFreeNewPins(CircuitDocument intermediate, ComponentTypeRegistry registry,
                                           ComponentDefinition before, ComponentDefinition replacement,
                                           List<Component> pieces) {
        List<Integer> newPins = newPinIndexes(before, replacement);
        for (Component original : pieces) {
            Component piece = intermediate.component(original.id()).orElseThrow();
            ComponentType type = registry.typeOf(piece);
            for (int index : newPins) {
                if (touchesSomething(intermediate, registry, piece, type.pinLocation(piece, index))) {
                    throw new UnsafeDefinitionUpdateException(replacement.id(), List.of(nameOf(piece) + " · "
                            + replacement.pinout().get(index).spec().name()), "appearance.update.newPinTouches");
                }
            }
        }
    }

    private static boolean touchesSomething(CircuitDocument document, ComponentTypeRegistry registry,
                                            Component piece, GridPoint point) {
        if (document.wires().stream().anyMatch(wire -> wire.contains(point))) {
            return true;
        }
        int pinsThere = 0;
        for (Component component : document.components()) {
            ComponentType type = registry.typeOf(component);
            for (int index = 0; index < type.pinCount(); index++) {
                if (type.pinLocation(component, index).equals(point)) {
                    pinsThere++;
                }
            }
        }
        return pinsThere > 1; // the new pin itself is one of them
    }

    /** «El pin D se ubicó en el borde derecho» for each new pin, «El pin C ya no existe» for each removed one. */
    private static List<UpdateNote> definitionNotes(ComponentDefinition before, ComponentDefinition replacement) {
        List<UpdateNote> notes = new ArrayList<>();
        for (DefinitionPin pin : newPins(before, replacement)) {
            notes.add(new UpdateNote("appearance.update.newPin." + edgeOf(pin.spec().position(), replacement.size()),
                    List.of(pin.spec().name())));
        }
        List<DefinitionPin> continuing = AppearanceReconciler.previousPins(before, replacement).stream()
                .flatMap(Optional::stream).toList();
        before.pinout().stream().filter(pin -> !continuing.contains(pin))
                .forEach(pin -> notes.add(new UpdateNote("appearance.update.removedPin",
                        List.of(pin.spec().name()))));
        return notes;
    }

    private static List<DefinitionPin> newPins(ComponentDefinition before, ComponentDefinition replacement) {
        return newPinIndexes(before, replacement).stream().map(replacement.pinout()::get).toList();
    }

    /** Which side of the box {@code position} is on: left, right, top, bottom, or inside. */
    private static String edgeOf(GridPoint position, GridSize size) {
        if (position.x() == 0) {
            return "left";
        }
        if (position.x() == size.width()) {
            return "right";
        }
        if (position.y() == 0) {
            return "top";
        }
        return position.y() == size.height() ? "bottom" : "inside";
    }

    /** The piece's label, or its id when it has none. */
    private static String nameOf(Component piece) {
        return piece.label().isBlank() ? piece.id().value() : piece.label();
    }

    static ShapeChangePlan emptyPlan() {
        return new ShapeChangePlan(FollowPlan.EMPTY, Map.of(), List.of(), List.of());
    }
}
