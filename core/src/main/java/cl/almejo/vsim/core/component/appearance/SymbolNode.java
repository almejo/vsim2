package cl.almejo.vsim.core.component.appearance;

import java.util.Objects;
import java.util.Optional;

/** A path node and, when it is curved, how the segment from it to the following node bends. */
public record SymbolNode(SymbolPoint point, Optional<SegmentCurve> curve) {
    public SymbolNode {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(curve, "curve");
    }

    /** A node whose following segment is straight (or the last node of an open path). */
    public static SymbolNode straight(SymbolPoint point) {
        return new SymbolNode(point, Optional.empty());
    }

    public static SymbolNode quadratic(SymbolPoint point, SymbolPoint control) {
        return new SymbolNode(point, Optional.of(new SegmentCurve.Quadratic(control)));
    }

    public static SymbolNode cubic(SymbolPoint point, SymbolPoint first, SymbolPoint second) {
        return new SymbolNode(point, Optional.of(new SegmentCurve.Cubic(first, second)));
    }

    public boolean curved() {
        return curve.isPresent();
    }

    public SymbolNode withPoint(SymbolPoint moved) {
        return new SymbolNode(moved, curve);
    }

    public SymbolNode straightened() {
        return straight(point);
    }
}
