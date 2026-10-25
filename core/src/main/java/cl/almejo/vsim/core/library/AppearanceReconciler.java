package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.appearance.PinPlacement;
import cl.almejo.vsim.core.component.appearance.SymbolDrawing;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Reconciles a complete instance override when its definition's interface identity changes. */
public final class AppearanceReconciler {
    private AppearanceReconciler() {
    }

    /**
     * A local logic repair rebuilds a body, not its outside symbol. Retain the reconciled general drawing and
     * external pin layout while taking the candidate's body and metadata. The caller assigns the accepted revision
     * once afterwards; explicit library replacements must keep the incoming appearance instead of calling this.
     */
    public static ComponentDefinition preserveGeneralAppearance(ComponentDefinition before,
                                                                 ComponentDefinition candidate) {
        if (!before.id().equals(candidate.id())) {
            throw new IllegalArgumentException("appearance can only be retained for the same component identity");
        }
        ComponentAppearance appearance = reconcile(before, candidate, Optional.of(ComponentAppearance.of(before)))
                .orElseGet(() -> ComponentAppearance.of(candidate));
        List<DefinitionPin> pins = new ArrayList<>();
        for (int index = 0; index < candidate.pinout().size(); index++) {
            DefinitionPin original = candidate.pinout().get(index);
            PinSpec spec = original.spec();
            pins.add(new DefinitionPin(new PinSpec(spec.name(), appearance.pins().get(index).offset(),
                    spec.direction(), spec.width()), original.source()));
        }
        return new ComponentDefinition(candidate.id(), candidate.name(), candidate.description(), appearance.size(),
                pins, candidate.body(), appearance.drawing(), candidate.revision());
    }

    /**
     * A validated whole override, or empty to inherit the definition. Match by source first, then an unambiguous
     * name/direction/width; never return a partially invalid override.
     */
    public static Optional<ComponentAppearance> reconcile(ComponentDefinition before, ComponentDefinition after,
                                             Optional<ComponentAppearance> appearance) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(appearance, "appearance");
        if (appearance.isEmpty()) {
            return Optional.empty();
        }
        ComponentAppearance canonical;
        try {
            canonical = appearance.orElseThrow().canonical(before.pinout());
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        Map<ElementId, PinPlacement> placements = new HashMap<>();
        canonical.pins().forEach(pin -> placements.put(pin.source(), pin));
        List<PinPlacement> result = new ArrayList<>();
        List<DefinitionPin> newPins = new ArrayList<>();
        List<Optional<DefinitionPin>> previousPins = previousPins(before, after);
        for (int index = 0; index < after.pinout().size(); index++) {
            DefinitionPin pin = after.pinout().get(index);
            Optional<DefinitionPin> previous = previousPins.get(index);
            if (previous.isPresent()) {
                result.add(new PinPlacement(pin.source(), placements.get(previous.get().source()).offset()));
            } else {
                newPins.add(pin);
            }
        }
        Set<GridPoint> occupied = new HashSet<>();
        result.forEach(pin -> occupied.add(pin.offset()));
        GridSize size = canonical.size();
        for (DefinitionPin pin : newPins) {
            GridPoint point = pin.spec().position();
            if (!isFreeEdgeSpot(pin, point, size, occupied)) {
                Optional<GridPoint> free = freeEdge(pin, size, occupied);
                while (free.isEmpty()) {
                    size = new GridSize(size.width(), Math.addExact(size.height(), DefinitionBuilder.PIN_SPACING));
                    free = freeEdge(pin, size, occupied);
                }
                point = free.orElseThrow();
            }
            result.add(new PinPlacement(pin.source(), point));
            occupied.add(point);
        }
        GridSize finalSize = size;
        Optional<SymbolDrawing> drawing = canonical.drawing().map(original ->
                new SymbolDrawing(finalSize, original.elements(), original.showPinNames()));
        try {
            ComponentAppearance reconciled = new ComponentAppearance(size, drawing, result).canonical(after.pinout());
            return Optional.of(reconciled);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /**
     * For each pin of {@code after}, in order, the pin of {@code before} it continues: the one with the same source,
     * else the only unmatched one with the same name, direction and width; empty for a new pin. The pins of
     * {@code before} that no pin continues are the removed ones.
     */
    public static List<Optional<DefinitionPin>> previousPins(ComponentDefinition before, ComponentDefinition after) {
        Set<ElementId> matched = new HashSet<>();
        List<Optional<DefinitionPin>> result = new ArrayList<>();
        for (DefinitionPin pin : after.pinout()) {
            Optional<DefinitionPin> previous = matching(before, pin, matched);
            previous.ifPresent(oldPin -> matched.add(oldPin.source()));
            result.add(previous);
        }
        return result;
    }

    private static Optional<DefinitionPin> matching(ComponentDefinition before, DefinitionPin pin,
                                                    Set<ElementId> alreadyMatched) {
        Optional<DefinitionPin> source = before.pinout().stream()
                .filter(old -> old.source().equals(pin.source()) && !alreadyMatched.contains(old.source())).findFirst();
        if (source.isPresent()) {
            return source;
        }
        List<DefinitionPin> matches = before.pinout().stream()
                .filter(old -> !alreadyMatched.contains(old.source()))
                .filter(old -> old.spec().name().equals(pin.spec().name())
                        && old.spec().direction() == pin.spec().direction() && old.spec().width() == pin.spec().width())
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    /** A new pin goes strictly inside its own side (never on a corner), at least one spacing from the other pins. */
    private static boolean isFreeEdgeSpot(DefinitionPin pin, GridPoint point, GridSize size, Set<GridPoint> occupied) {
        int edgeX = pin.spec().isInput() ? 0 : size.width();
        if (point.x() != edgeX || point.y() <= 0 || point.y() >= size.height()) {
            return false;
        }
        for (GridPoint other : occupied) {
            if (other.x() == edgeX && Math.abs(other.y() - point.y()) < DefinitionBuilder.PIN_SPACING) {
                return false;
            }
        }
        return true;
    }

    private static Optional<GridPoint> freeEdge(DefinitionPin pin, GridSize size, Set<GridPoint> occupied) {
        int edgeX = pin.spec().isInput() ? 0 : size.width();
        for (int y = 1; y < size.height(); y++) {
            GridPoint candidate = new GridPoint(edgeX, y);
            if (isFreeEdgeSpot(pin, candidate, size, occupied)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
