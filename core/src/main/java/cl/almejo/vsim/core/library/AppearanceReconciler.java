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
    /** A validated whole override, or empty to inherit the definition, with localizable explanation keys. */
    public record ReconcileResult(Optional<ComponentAppearance> kept, List<String> messageKeys) {
        public ReconcileResult {
            Objects.requireNonNull(kept, "kept");
            messageKeys = List.copyOf(messageKeys);
        }
    }

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
        ReconcileResult result = reconcile(before, candidate, Optional.of(ComponentAppearance.of(before)));
        ComponentAppearance appearance = result.kept().orElseGet(() -> ComponentAppearance.of(candidate));
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

    /** Match by source first, then an unambiguous name/direction/width; never return a partially invalid override. */
    public static ReconcileResult reconcile(ComponentDefinition before, ComponentDefinition after,
                                             Optional<ComponentAppearance> appearance) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(appearance, "appearance");
        if (appearance.isEmpty()) {
            return new ReconcileResult(Optional.empty(), List.of());
        }
        ComponentAppearance canonical;
        try {
            canonical = appearance.orElseThrow().canonical(before.pinout());
        } catch (IllegalArgumentException exception) {
            return dropped();
        }
        Map<ElementId, PinPlacement> placements = new HashMap<>();
        canonical.pins().forEach(pin -> placements.put(pin.source(), pin));
        Set<ElementId> matched = new HashSet<>();
        List<PinPlacement> result = new ArrayList<>();
        List<DefinitionPin> newPins = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        for (DefinitionPin pin : after.pinout()) {
            Optional<DefinitionPin> previous = matching(before, pin, matched);
            if (previous.isPresent()) {
                DefinitionPin oldPin = previous.get();
                matched.add(oldPin.source());
                result.add(new PinPlacement(pin.source(), placements.get(oldPin.source()).offset()));
            } else {
                newPins.add(pin);
            }
        }
        Set<GridPoint> occupied = new HashSet<>();
        result.forEach(pin -> occupied.add(pin.offset()));
        GridSize size = canonical.size();
        for (DefinitionPin pin : newPins) {
            GridPoint point = pin.spec().position();
            if (point.x() > size.width() || point.y() > size.height() || occupied.contains(point)) {
                Optional<GridPoint> free = freeEdge(pin, size, occupied);
                if (free.isEmpty()) {
                    size = new GridSize(size.width(), Math.addExact(size.height(), 1));
                    free = freeEdge(pin, size, occupied);
                }
                point = free.orElseThrow();
            }
            result.add(new PinPlacement(pin.source(), point));
            occupied.add(point);
            messages.add("appearance.reconcile.newPin");
        }
        GridSize finalSize = size;
        Optional<SymbolDrawing> drawing = canonical.drawing().map(original ->
                new SymbolDrawing(finalSize, original.elements(), original.showPinNames()));
        try {
            ComponentAppearance reconciled = new ComponentAppearance(size, drawing, result).canonical(after.pinout());
            return new ReconcileResult(Optional.of(reconciled), messages);
        } catch (IllegalArgumentException exception) {
            return dropped();
        }
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

    private static Optional<GridPoint> freeEdge(DefinitionPin pin, GridSize size, Set<GridPoint> occupied) {
        int x = pin.spec().isInput() ? 0 : size.width();
        int limit = Math.min(size.height(), occupied.size());
        for (int y = 0; y <= limit; y++) {
            GridPoint candidate = new GridPoint(x, y);
            if (!occupied.contains(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static ReconcileResult dropped() {
        return new ReconcileResult(Optional.empty(), List.of("appearance.reconcile.dropped"));
    }
}
