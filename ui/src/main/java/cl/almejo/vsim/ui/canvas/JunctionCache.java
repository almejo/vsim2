package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.Set;

/**
 * {@link Junctions#of} computed once per document revision, so repaints (marching ants, simulation snapshots) do not
 * repeat the search over every pair of wires. One per renderer; used on the EDT only.
 */
public final class JunctionCache {

    private CircuitDocument document;
    private long revision;
    private Set<GridPoint> junctions = Set.of();

    public Set<GridPoint> of(CircuitDocument current) {
        if (current != document || current.revision() != revision) {
            junctions = Set.copyOf(Junctions.of(current));
            document = current;
            revision = current.revision();
        }
        return junctions;
    }
}
