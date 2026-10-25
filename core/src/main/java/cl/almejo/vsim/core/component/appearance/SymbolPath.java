package cl.almejo.vsim.core.component.appearance;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** One open or closed figure; filled figures must be closed. */
public record SymbolPath(List<SymbolNode> nodes, boolean closed, boolean filled) implements SymbolElement {
    public static final int MAXIMUM_NODES = 256;
    /**
     * A node counts as smooth when its shorter handle ends this close to the line of the longer one (rounding every
     * point to the eighth moves a handle by up to about 0.7 of one) ...
     */
    private static final double SMOOTH_DISTANCE_EIGHTHS = 1.0;
    /** ... or when the two handles turn by at most this angle: long handles, slightly off. */
    private static final double SMOOTH_ANGLE_RADIANS = Math.toRadians(3);

    public SymbolPath {
        nodes = List.copyOf(nodes);
        int minimum = closed ? 3 : 2;
        if (nodes.size() < minimum || nodes.size() > MAXIMUM_NODES) {
            throw new IllegalArgumentException("a path needs " + minimum + ".." + MAXIMUM_NODES + " nodes");
        }
        if (filled && !closed) {
            throw new IllegalArgumentException("only closed paths may be filled");
        }
        if (!closed && nodes.getLast().curved()) {
            throw new IllegalArgumentException("an open path's last node has no following segment");
        }
    }

    /** Segments from each node to the next: one fewer than the nodes when open. */
    public int segmentCount() {
        return nodes.size() - (closed ? 0 : 1);
    }

    /** The exact point of segment {@code segment} at {@code parameter} (0 at its node, 1 at the next one). */
    public CurvePoint pointAt(int segment, double parameter) {
        return span(segment).at(parameter);
    }

    /** The parameter of the point of segment {@code segment} nearest to ({@code x}, {@code y}), in eighths. */
    public double nearestParameter(int segment, double x, double y) {
        return span(segment).nearestParameter(x, y);
    }

    /**
     * Where handle {@code side} of node {@code node} is: the outgoing one belongs to the segment leaving the node, the
     * incoming one to the segment arriving at it. Empty when that segment is straight or does not exist (the ends of
     * an open path). A quadratic shows the handles of its exact cubic, rounded to the eighth.
     */
    public Optional<SymbolPoint> handle(int node, HandleSide side) {
        OptionalInt segment = segmentOf(node, side);
        if (segment.isEmpty()) {
            return Optional.empty();
        }
        SymbolNode start = nodes.get(segment.getAsInt());
        return start.curve().map(curve -> {
            SegmentCurve.Cubic cubic = curve.cubic(start.point(), segmentEnd(segment.getAsInt()));
            return side == HandleSide.OUTGOING ? cubic.first() : cubic.second();
        });
    }

    /** The segment that holds handle {@code side} of node {@code node}; empty at the open ends of a path. */
    public OptionalInt segmentOf(int node, HandleSide side) {
        if (node < 0 || node >= nodes.size()) {
            throw new IllegalArgumentException("No node at " + node);
        }
        if (side == HandleSide.OUTGOING) {
            return node < segmentCount() ? OptionalInt.of(node) : OptionalInt.empty();
        }
        return closed || node > 0 ? OptionalInt.of(Math.floorMod(node - 1, nodes.size())) : OptionalInt.empty();
    }

    /** Whether node {@code node} sits between two curved segments: only such a node can be smooth. */
    public boolean hasTwoHandles(int node) {
        return handle(node, HandleSide.INCOMING).isPresent() && handle(node, HandleSide.OUTGOING).isPresent();
    }

    /**
     * Whether node {@code node}'s two handles lie on one line through it, pointing away from each other (within the
     * rounding of a stored file): how a smooth node is recognised, since smooth or corner is not stored.
     */
    public boolean looksSmooth(int node) {
        if (!hasTwoHandles(node)) {
            return false;
        }
        SymbolPoint point = nodes.get(node).point();
        SymbolPoint incoming = handle(node, HandleSide.INCOMING).orElseThrow();
        SymbolPoint outgoing = handle(node, HandleSide.OUTGOING).orElseThrow();
        double backX = incoming.x() - point.x();
        double backY = incoming.y() - point.y();
        double aheadX = outgoing.x() - point.x();
        double aheadY = outgoing.y() - point.y();
        double backLength = Math.hypot(backX, backY);
        double aheadLength = Math.hypot(aheadX, aheadY);
        if (backLength == 0 || aheadLength == 0 || backX * aheadX + backY * aheadY >= 0) {
            return false;
        }
        double sine = Math.abs(backX * aheadY - backY * aheadX) / (backLength * aheadLength);
        return Math.min(backLength, aheadLength) * sine <= SMOOTH_DISTANCE_EIGHTHS
                || Math.asin(Math.min(1, sine)) <= SMOOTH_ANGLE_RADIANS;
    }

    /** The node where segment {@code segment} ends. */
    public SymbolPoint segmentEnd(int segment) {
        return nodes.get((segment + 1) % nodes.size()).point();
    }

    CubicSpan span(int segment) {
        if (segment < 0 || segment >= segmentCount()) {
            throw new IllegalArgumentException("No segment at " + segment);
        }
        SymbolNode start = nodes.get(segment);
        return CubicSpan.of(start.point(), start.curve(), segmentEnd(segment));
    }
}
