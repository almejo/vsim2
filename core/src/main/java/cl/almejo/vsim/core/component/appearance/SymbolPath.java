package cl.almejo.vsim.core.component.appearance;

import java.util.List;

/** One open or closed figure; filled figures must be closed. */
public record SymbolPath(List<SymbolNode> nodes, boolean closed, boolean filled) implements SymbolElement {
    public static final int MAXIMUM_NODES = 256;

    /**
     * The point halfway along segment {@code segment} (from its node to the next one), on the curve when it is curved:
     * where «Curvar tramo» shows the segment's handle. In eighths, rounded.
     */
    public SymbolPoint segmentMiddle(int segment) {
        if (segment < 0 || segment >= nodes.size() - (closed ? 0 : 1)) {
            throw new IllegalArgumentException("No segment at " + segment);
        }
        SymbolNode start = nodes.get(segment);
        SymbolPoint end = nodes.get((segment + 1) % nodes.size()).point();
        if (start.control().isEmpty()) {
            return new SymbolPoint((int) Math.round((start.point().x() + end.x()) / 2.0),
                    (int) Math.round((start.point().y() + end.y()) / 2.0));
        }
        SymbolPoint control = start.control().get();
        // A quadratic Bézier at t = 1/2: a quarter of each end and half of the control.
        return new SymbolPoint((int) Math.round((start.point().x() + 2.0 * control.x() + end.x()) / 4),
                (int) Math.round((start.point().y() + 2.0 * control.y() + end.y()) / 4));
    }

    public SymbolPath {
        nodes = List.copyOf(nodes);
        int minimum = closed ? 3 : 2;
        if (nodes.size() < minimum || nodes.size() > MAXIMUM_NODES) {
            throw new IllegalArgumentException("a path needs " + minimum + ".." + MAXIMUM_NODES + " nodes");
        }
        if (filled && !closed) {
            throw new IllegalArgumentException("only closed paths may be filled");
        }
        if (!closed && nodes.getLast().control().isPresent()) {
            throw new IllegalArgumentException("an open path's last node has no following segment");
        }
    }
}
