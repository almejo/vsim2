package cl.almejo.vsim.core.component.appearance;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether a pin on the box edge can reach the drawing: the painter gives such a pin a stub of at most
 * {@link #MAXIMUM_STUB_UNITS} grid units straight inward, and a pin whose stub would end in empty space floats
 * (spec §5), so the editor warns about it.
 */
final class OutlineReach {
    /** The longest pin stub, the same limit the painter applies. */
    static final int MAXIMUM_STUB_UNITS = 2;
    private static final int CURVE_STEPS = 8;
    private static final double STROKE_TOLERANCE = 1;

    private OutlineReach() { }

    /** True when the pin's stub reaches a figure of {@code drawing}, or the drawing has no figure at all. */
    static boolean reaches(SymbolDrawing drawing, GridSize box, GridPoint pin) {
        List<SymbolPoint[]> segments = segments(drawing);
        if (segments.isEmpty()) {
            return true;
        }
        double x = pin.x() * (double) SymbolPoint.EIGHTHS;
        double y = pin.y() * (double) SymbolPoint.EIGHTHS;
        double width = box.width() * (double) SymbolPoint.EIGHTHS;
        double height = box.height() * (double) SymbolPoint.EIGHTHS;
        boolean onSide = Math.min(x, width - x) <= Math.min(y, height - y);
        double directionX = onSide ? (x <= width / 2 ? 1 : -1) : 0;
        double directionY = onSide ? 0 : (y <= height / 2 ? 1 : -1);
        double limit = MAXIMUM_STUB_UNITS * SymbolPoint.EIGHTHS + STROKE_TOLERANCE;
        return segments.stream().anyMatch(segment ->
                distanceAlong(segment, x, y, directionX, directionY) <= limit);
    }

    /** The distance from the pin to where the ray along (directionX, directionY) meets the segment. */
    private static double distanceAlong(SymbolPoint[] segment, double x, double y, double directionX,
                                        double directionY) {
        double fromX = segment[0].x();
        double fromY = segment[0].y();
        double toX = segment[1].x();
        double toY = segment[1].y();
        boolean alongX = directionX != 0;
        double pinAxis = alongX ? y : x;
        double fromAxis = alongX ? fromY : fromX;
        double toAxis = alongX ? toY : toX;
        if (Math.abs(toAxis - fromAxis) < 1e-9) {
            return Math.abs(pinAxis - fromAxis) <= STROKE_TOLERANCE
                    ? firstContact(alongX ? fromX : fromY, alongX ? toX : toY, alongX ? x : y,
                            alongX ? directionX : directionY)
                    : Double.MAX_VALUE;
        }
        double fraction = (pinAxis - fromAxis) / (toAxis - fromAxis);
        if (fraction < -1e-9 || fraction > 1 + 1e-9) {
            return Double.MAX_VALUE;
        }
        double hit = alongX ? fromX + (toX - fromX) * fraction : fromY + (toY - fromY) * fraction;
        double distance = (hit - (alongX ? x : y)) * (alongX ? directionX : directionY);
        return distance >= -STROKE_TOLERANCE ? Math.max(0, distance) : Double.MAX_VALUE;
    }

    /** A segment lying along the ray: the nearest of its two ends in front of the pin. */
    private static double firstContact(double from, double to, double origin, double direction) {
        double nearest = Double.MAX_VALUE;
        for (double end : new double[] {from, to}) {
            double distance = (end - origin) * direction;
            if (distance >= -STROKE_TOLERANCE) {
                nearest = Math.min(nearest, Math.max(0, distance));
            }
        }
        return nearest;
    }

    private static List<SymbolPoint[]> segments(SymbolDrawing drawing) {
        List<SymbolPoint[]> segments = new ArrayList<>();
        for (SymbolPath path : drawing.paths()) {
            for (int index = 0; index < path.segmentCount(); index++) {
                SymbolNode node = path.nodes().get(index);
                SymbolPoint end = path.segmentEnd(index);
                if (node.curved()) {
                    addCurve(segments, path.span(index));
                } else {
                    segments.add(new SymbolPoint[] {node.point(), end});
                }
            }
        }
        return segments;
    }

    private static void addCurve(List<SymbolPoint[]> segments, CubicSpan curve) {
        SymbolPoint previous = CubicSpan.rounded(curve.start());
        for (int step = 1; step <= CURVE_STEPS; step++) {
            SymbolPoint next = CubicSpan.rounded(curve.at(step / (double) CURVE_STEPS));
            segments.add(new SymbolPoint[] {previous, next});
            previous = next;
        }
    }
}
