package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.SymbolElement;
import cl.almejo.vsim.core.component.appearance.SymbolNode;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;

import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.QuadCurve2D;
import java.util.List;
import java.util.Optional;

/** Picks actual straight or curved segments, ignoring figure fills and text bounds. Coordinates are eighths. */
final class ShapeSegments {
    record Hit(int pathIndex, int segmentIndex, SymbolPoint point) { }
    private record Nearest(double x, double y, double distanceSquared) { }

    private ShapeSegments() { }

    static Optional<Hit> find(List<SymbolElement> elements, double x, double y, double radius) {
        Optional<Hit> hit = Optional.empty();
        double bestDistance = radius * radius;
        for (int pathIndex = elements.size() - 1; pathIndex >= 0; pathIndex--) {
            if (!(elements.get(pathIndex) instanceof SymbolPath path)) { continue; }
            int count = path.nodes().size() - (path.closed() ? 0 : 1);
            for (int segment = 0; segment < count; segment++) {
                Nearest nearest = nearest(shape(path, segment), x, y, Math.max(0.05, radius / 8));
                SymbolPoint point = new SymbolPoint((int) Math.round(nearest.x()), (int) Math.round(nearest.y()));
                if (nearest.distanceSquared() < bestDistance
                        && !point.equals(path.nodes().get(segment).point())
                        && !point.equals(path.nodes().get((segment + 1) % path.nodes().size()).point())) {
                    bestDistance = nearest.distanceSquared();
                    hit = Optional.of(new Hit(pathIndex, segment, point));
                }
            }
        }
        return hit;
    }

    static Shape shape(SymbolPath path, int segment) {
        SymbolNode start = path.nodes().get(segment);
        SymbolPoint end = path.nodes().get((segment + 1) % path.nodes().size()).point();
        if (start.control().isPresent()) {
            SymbolPoint control = start.control().get();
            return new QuadCurve2D.Double(start.point().x(), start.point().y(), control.x(), control.y(), end.x(), end.y());
        }
        return new Line2D.Double(start.point().x(), start.point().y(), end.x(), end.y());
    }

    private static Nearest nearest(Shape shape, double x, double y, double flatness) {
        PathIterator segments = shape.getPathIterator(null, flatness);
        double[] coordinates = new double[6];
        double previousX = 0;
        double previousY = 0;
        Nearest best = new Nearest(0, 0, Double.POSITIVE_INFINITY);
        while (!segments.isDone()) {
            int kind = segments.currentSegment(coordinates);
            if (kind == PathIterator.SEG_LINETO) {
                double horizontal = coordinates[0] - previousX;
                double vertical = coordinates[1] - previousY;
                double length = horizontal * horizontal + vertical * vertical;
                double fraction = length == 0 ? 0 : Math.clamp(
                        ((x - previousX) * horizontal + (y - previousY) * vertical) / length, 0, 1);
                double projectedX = previousX + horizontal * fraction;
                double projectedY = previousY + vertical * fraction;
                double distance = Math.pow(projectedX - x, 2) + Math.pow(projectedY - y, 2);
                if (distance < best.distanceSquared()) { best = new Nearest(projectedX, projectedY, distance); }
            }
            previousX = coordinates[0]; previousY = coordinates[1];
            segments.next();
        }
        return best;
    }
}
