package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.appearance.SymbolDrawing;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;

import java.util.List;
import java.util.Objects;

/**
 * A Logisim circuit's look as a VSim custom component: the box {@code size}, the {@code drawing} in it, one pin per
 * Logisim {@code Pin} (on the box edge, named uniquely), the {@code anchor} (where, relative to the box's top-left in
 * grid units, an instance's Logisim location lands: possibly outside the box) and the drawing's {@code facing} (the
 * Logisim direction an instance with no turn faces), plus the notes of what changed on the way.
 */
public record ConvertedAppearance(GridSize size, SymbolDrawing drawing, List<ConvertedPin> pins, GridPoint anchor,
                                  String facing, List<ImportNote> notes) {

    public ConvertedAppearance {
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(drawing, "drawing");
        pins = List.copyOf(pins);
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(facing, "facing");
        notes = List.copyOf(notes);
    }

    /** A pin of the component and the Logisim {@code Pin} it comes from (the body's interface pin). */
    public record ConvertedPin(LogisimInterfacePin source, PinSpec spec) {
        public ConvertedPin {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(spec, "spec");
        }
    }

    /**
     * How a VSim instance turns when its Logisim instance faces {@code instanceFacing}: the turn from the drawing's
     * facing to it. An unreadable facing counts as the drawing's own.
     */
    public Rotation rotationFor(String instanceFacing) {
        LogisimFacing own = facingOr(facing, LogisimFacing.EAST);
        return facingOr(instanceFacing, own).rotationFrom(own);
    }

    private static LogisimFacing facingOr(String text, LogisimFacing fallback) {
        try {
            return LogisimFacing.parse(text);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
