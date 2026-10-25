package cl.almejo.vsim.core.component.appearance;

import java.util.List;
import java.util.Optional;

/**
 * One segment as an exact cubic Bézier in eighths: a straight segment has its controls at a third and two thirds, a
 * quadratic is raised exactly (no rounding), so every kind of segment is measured, sampled and split the same way.
 */
record CubicSpan(CurvePoint start, CurvePoint first, CurvePoint second, CurvePoint end) {
    /** Samples when looking for the nearest point: far finer than a pointer can aim (the old quadratic search's). */
    private static final int NEAREST_SAMPLES = 1024;

    static CubicSpan of(SymbolPoint start, Optional<SegmentCurve> curve, SymbolPoint end) {
        CurvePoint from = exact(start);
        CurvePoint to = exact(end);
        if (curve.isEmpty()) {
            return new CubicSpan(from, between(from, to, 1.0 / 3), between(from, to, 2.0 / 3), to);
        }
        return switch (curve.get()) {
            case SegmentCurve.Quadratic quadratic -> new CubicSpan(from,
                    between(from, exact(quadratic.control()), 2.0 / 3),
                    between(to, exact(quadratic.control()), 2.0 / 3), to);
            case SegmentCurve.Cubic cubic -> new CubicSpan(from, exact(cubic.first()), exact(cubic.second()), to);
        };
    }

    CurvePoint at(double parameter) {
        double rest = 1 - parameter;
        double startWeight = rest * rest * rest;
        double firstWeight = 3 * rest * rest * parameter;
        double secondWeight = 3 * rest * parameter * parameter;
        double endWeight = parameter * parameter * parameter;
        return new CurvePoint(
                startWeight * start.x() + firstWeight * first.x() + secondWeight * second.x() + endWeight * end.x(),
                startWeight * start.y() + firstWeight * first.y() + secondWeight * second.y() + endWeight * end.y());
    }

    /** The parameter (0 to 1) of the point of the segment nearest to ({@code x}, {@code y}), sampled. */
    double nearestParameter(double x, double y) {
        double nearest = 0;
        double distance = Double.POSITIVE_INFINITY;
        for (int sample = 0; sample <= NEAREST_SAMPLES; sample++) {
            double parameter = sample / (double) NEAREST_SAMPLES;
            CurvePoint point = at(parameter);
            double candidate = Math.pow(point.x() - x, 2) + Math.pow(point.y() - y, 2);
            if (candidate < distance) {
                distance = candidate;
                nearest = parameter;
            }
        }
        return nearest;
    }

    /** The two halves at {@code parameter} (de Casteljau): together they draw exactly this segment. */
    List<CubicSpan> split(double parameter) {
        CurvePoint startFirst = between(start, first, parameter);
        CurvePoint firstSecond = between(first, second, parameter);
        CurvePoint secondEnd = between(second, end, parameter);
        CurvePoint left = between(startFirst, firstSecond, parameter);
        CurvePoint right = between(firstSecond, secondEnd, parameter);
        CurvePoint middle = between(left, right, parameter);
        return List.of(new CubicSpan(start, startFirst, left, middle), new CubicSpan(middle, right, secondEnd, end));
    }

    static CurvePoint exact(SymbolPoint point) {
        return new CurvePoint(point.x(), point.y());
    }

    static SymbolPoint rounded(CurvePoint point) {
        return new SymbolPoint((int) Math.round(point.x()), (int) Math.round(point.y()));
    }

    private static CurvePoint between(CurvePoint from, CurvePoint to, double fraction) {
        return new CurvePoint(from.x() + (to.x() - from.x()) * fraction, from.y() + (to.y() - from.y()) * fraction);
    }
}
