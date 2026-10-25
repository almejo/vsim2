package cl.almejo.vsim.core.component.appearance;

import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * How the segment from a node to the next one bends. The editor makes cubics (two handles, one at each end, so a node
 * between two curves can be smooth); quadratics come from older files and the templates, and stay as they are until
 * their segment is edited.
 */
public sealed interface SegmentCurve permits SegmentCurve.Quadratic, SegmentCurve.Cubic {

    /** The control points, in the order the path data writes them. */
    List<SymbolPoint> controls();

    SegmentCurve map(UnaryOperator<SymbolPoint> mapper);

    /**
     * The same curve as a cubic between {@code start} and {@code end}: a cubic as it is; a quadratic with its handles
     * two thirds of the way towards its control, rounded to the eighth (exact when those land on eighths).
     */
    Cubic cubic(SymbolPoint start, SymbolPoint end);

    /** One control shared by both ends: SVG's {@code Q}. */
    record Quadratic(SymbolPoint control) implements SegmentCurve {
        public Quadratic {
            Objects.requireNonNull(control, "control");
        }

        @Override
        public List<SymbolPoint> controls() {
            return List.of(control);
        }

        @Override
        public SegmentCurve map(UnaryOperator<SymbolPoint> mapper) {
            return new Quadratic(mapper.apply(control));
        }

        @Override
        public Cubic cubic(SymbolPoint start, SymbolPoint end) {
            return new Cubic(twoThirds(start, control), twoThirds(end, control));
        }

        private static SymbolPoint twoThirds(SymbolPoint from, SymbolPoint towards) {
            return new SymbolPoint((int) Math.round(from.x() + 2.0 * (towards.x() - from.x()) / 3),
                    (int) Math.round(from.y() + 2.0 * (towards.y() - from.y()) / 3));
        }
    }

    /**
     * Two handles: {@code first} leaves the segment's start node, {@code second} arrives at its end node. SVG's
     * {@code C}.
     */
    record Cubic(SymbolPoint first, SymbolPoint second) implements SegmentCurve {
        public Cubic {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
        }

        @Override
        public List<SymbolPoint> controls() {
            return List.of(first, second);
        }

        @Override
        public SegmentCurve map(UnaryOperator<SymbolPoint> mapper) {
            return new Cubic(mapper.apply(first), mapper.apply(second));
        }

        @Override
        public Cubic cubic(SymbolPoint start, SymbolPoint end) {
            return this;
        }
    }
}
