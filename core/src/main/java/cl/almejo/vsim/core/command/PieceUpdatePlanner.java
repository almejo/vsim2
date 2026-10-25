package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.AppearanceReconciler;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.library.UnsafeDefinitionUpdateException;
import cl.almejo.vsim.core.library.UpdateNote;
import cl.almejo.vsim.core.wiring.FollowPlan;
import cl.almejo.vsim.core.wiring.ForeignContact;
import cl.almejo.vsim.core.wiring.ShapeChangePlan;
import cl.almejo.vsim.core.wiring.ShapeChangePlanner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    /** The plan for the pieces (empty when there are none) and the lines that tell the user what happened. */
    record Result(ShapeChangePlan plan, List<UpdateNote> notes) {
        Result {
            notes = List.copyOf(notes);
        }
    }

    private PieceUpdatePlanner() {
    }

    /** Plans against {@code document} as it is, which is never changed. */
    static Result plan(CircuitDocument document, ComponentTypeRegistry registry, DefinitionBundle bundle,
                       ComponentDefinition before) {
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
        requireFreeNewPins(intermediate, frozen, before, replacement, pieces);
        ShapeChangePlan plan = ShapeChangePlanner.plan(intermediate, frozen, frozen, targets);
        for (ForeignContact contact : plan.keptOldShape()) {
            notes.add(new UpdateNote("appearance.update.keptOldShape",
                    List.of(nameOf(document.component(contact.instance()).orElseThrow()))));
        }
        return new Result(plan, notes);
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
        List<Optional<DefinitionPin>> previous = AppearanceReconciler.previousPins(before, replacement);
        for (Component original : pieces) {
            Component piece = intermediate.component(original.id()).orElseThrow();
            ComponentType type = registry.typeOf(piece);
            for (int index = 0; index < previous.size(); index++) {
                if (previous.get(index).isEmpty() && touchesSomething(intermediate, registry, piece,
                        type.pinLocation(piece, index))) {
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
        List<Optional<DefinitionPin>> previous = AppearanceReconciler.previousPins(before, replacement);
        List<DefinitionPin> result = new ArrayList<>();
        for (int index = 0; index < previous.size(); index++) {
            if (previous.get(index).isEmpty()) {
                result.add(replacement.pinout().get(index));
            }
        }
        return result;
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
