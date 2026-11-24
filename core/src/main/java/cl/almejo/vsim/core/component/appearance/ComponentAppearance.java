package cl.almejo.vsim.core.component.appearance;

import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.ComponentDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Local size, drawing and pin positions; absent drawing means the standard box, not an empty symbol. */
public record ComponentAppearance(GridSize size, Optional<SymbolDrawing> drawing, List<PinPlacement> pins) {
    public ComponentAppearance {
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(drawing, "drawing");
        pins = List.copyOf(pins);
        if (drawing.isPresent() && !drawing.get().size().equals(size)) {
            throw new IllegalArgumentException("appearance and drawing sizes must agree");
        }
        Set<ElementId> sources = new HashSet<>();
        for (PinPlacement pin : pins) {
            if (!sources.add(pin.source())) {
                throw new IllegalArgumentException("pin source placed twice: " + pin.source());
            }
            GridPoint point = pin.offset();
            if (point.x() < 0 || point.y() < 0 || point.x() > size.width() || point.y() > size.height()) {
                throw new IllegalArgumentException("pin placement outside its box: " + pin.source());
            }
        }
    }

    /** The general appearance of a definition, including the standard-box case. */
    public static ComponentAppearance of(ComponentDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return new ComponentAppearance(definition.size(), definition.appearance(), definition.pinout().stream()
                .map(pin -> new PinPlacement(pin.source(), pin.spec().position())).toList());
    }

    /**
     * {@code definition} drawn with this appearance (made canonical for its pinout): its size, its drawing and its
     * pins moved, as a new revision ({@link ComponentDefinition#withGeometry}; the same definition when nothing moves).
     */
    public ComponentDefinition appliedTo(ComponentDefinition definition) {
        ComponentAppearance canonical = canonical(definition.pinout());
        return definition.withGeometry(canonical.size(), canonical.placedPinout(definition.pinout()),
                canonical.drawing());
    }

    /**
     * {@code pinout} with each pin at the offset of this appearance's pin at the same index (a canonical appearance of
     * that pinout); names, directions, widths and sources stay.
     */
    public List<DefinitionPin> placedPinout(List<DefinitionPin> pinout) {
        List<DefinitionPin> placed = new ArrayList<>();
        for (int index = 0; index < pinout.size(); index++) {
            DefinitionPin pin = pinout.get(index);
            PinSpec spec = pin.spec();
            placed.add(new DefinitionPin(new PinSpec(spec.name(), pins.get(index).offset(), spec.direction(),
                    spec.width()), pin.source()));
        }
        return placed;
    }

    /** Validates an accepted override and orders its pins as the flattener binds them. */
    public ComponentAppearance canonical(List<DefinitionPin> pinout) {
        if (pins.size() != pinout.size()) {
            throw new IllegalArgumentException("appearance must place every definition pin exactly once");
        }
        Map<ElementId, PinPlacement> bySource = new HashMap<>();
        Set<GridPoint> positions = new HashSet<>();
        for (PinPlacement pin : pins) {
            bySource.put(pin.source(), pin);
            if (!positions.add(pin.offset())) {
                throw new IllegalArgumentException("coinciding pin placements: " + pin.offset());
            }
        }
        List<PinPlacement> ordered = pinout.stream().map(pin -> placementOf(bySource, pin)).toList();
        return new ComponentAppearance(size, drawing, ordered);
    }

    private static PinPlacement placementOf(Map<ElementId, PinPlacement> bySource, DefinitionPin pin) {
        PinPlacement placement = bySource.get(pin.source());
        if (placement == null) {
            throw new IllegalArgumentException("appearance does not place pin " + pin.source());
        }
        return placement;
    }
}
