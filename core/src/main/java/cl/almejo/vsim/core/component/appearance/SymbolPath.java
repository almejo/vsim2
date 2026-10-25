package cl.almejo.vsim.core.component.appearance;

import cl.almejo.vsim.core.document.NoteColor;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * One open or closed figure; filled figures must be closed. {@code fill} is the colour of the fill (only a filled
 * figure has one) and {@code line} the colour of the outline, from the notes' palette; {@code NONE} is the plain look.
 * Never a red fill: red means broken in a circuit ({@link #FILL_CHOICES}).
 */
public record SymbolPath(List<SymbolNode> nodes, boolean closed, boolean filled, NoteColor fill, NoteColor line)
        implements SymbolElement {
    public static final int MAXIMUM_NODES = 256;
    /** The colours a fill may take: the palette without red. */
    public static final List<NoteColor> FILL_CHOICES = Arrays.stream(NoteColor.values())
            .filter(color -> color != NoteColor.RED).toList();

    /** A figure in the plain look. */
    public SymbolPath(List<SymbolNode> nodes, boolean closed, boolean filled) {
        this(nodes, closed, filled, NoteColor.NONE, NoteColor.NONE);
    }

    /** This figure with other nodes and closing or fill; the colours stay, the fill's only while it is filled. */
    public SymbolPath reshaped(List<SymbolNode> newNodes, boolean newClosed, boolean newFilled) {
        return new SymbolPath(newNodes, newClosed, newFilled, newFilled ? fill : NoteColor.NONE, line);
    }

    /** This figure with those fill and outline colours. */
    public SymbolPath coloured(NoteColor newFill, NoteColor newLine) {
        return new SymbolPath(nodes, closed, filled, newFill, newLine);
    }

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
        Objects.requireNonNull(fill, "fill");
        Objects.requireNonNull(line, "line");
        if (!FILL_CHOICES.contains(fill)) {
            throw new IllegalArgumentException("a fill is never " + fill);
        }
        if (!filled && fill != NoteColor.NONE) {
            throw new IllegalArgumentException("only a filled path has a fill colour");
        }
        if (!closed && nodes.getLast().control().isPresent()) {
            throw new IllegalArgumentException("an open path's last node has no following segment");
        }
    }
}
