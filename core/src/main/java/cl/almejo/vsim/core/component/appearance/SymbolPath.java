package cl.almejo.vsim.core.component.appearance;

import java.util.List;

/** One open or closed figure; filled figures must be closed. */
public record SymbolPath(List<SymbolNode> nodes, boolean closed, boolean filled) implements SymbolElement {
    public static final int MAXIMUM_NODES = 256;

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
