package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;

import java.util.HashSet;
import java.util.Set;

/**
 * The problem highlights and bus points of the frame being painted, worked out again only when the document (identity
 * and revision), the netlist (identity and token) or the snapshot (identity) changes: an animated canvas repaints many
 * times a second with the same ones. Each highlight is worked out only when asked for, and the merged top-level nets
 * ({@link TopLevelNets}) once per document revision and netlist. One per renderer or overlay; EDT only. Holds
 * geometry and ids only, never scheme colors.
 */
public final class HighlightCache {

    private final ComponentTypeRegistry registry;
    private CircuitDocument document;
    private long revision;
    private Netlist netlist;
    private long netlistToken;
    private SimulationSnapshot snapshot;
    private boolean frameComputed;
    private ShortCircuitHighlight shortCircuits;
    private WidthMismatchHighlight widthMismatches;
    private CircuitDocument mergedDocument;
    private long mergedRevision;
    private Netlist mergedNetlist;
    private TopLevelNets merged;
    private Netlist busNetlist;
    private Set<GridPoint> busPoints;
    private int computations;
    private int highlightComputations;
    private int mergedComputations;

    public HighlightCache(ComponentTypeRegistry registry) {
        this.registry = registry;
    }

    public ShortCircuitHighlight shortCircuits(RenderState state) {
        return shortCircuits(state.document(), state.netlist(), state.netlistToken(), state.snapshot());
    }

    public ShortCircuitHighlight shortCircuits(CircuitDocument document, Netlist netlist, long netlistToken,
                                               SimulationSnapshot snapshot) {
        useFrame(document, netlist, netlistToken, snapshot);
        if (shortCircuits == null) {
            shortCircuits = ShortCircuitHighlight.of(snapshot, netlist, netlistToken, document, registry,
                    () -> merged(document, netlist));
            counted();
        }
        return shortCircuits;
    }

    public WidthMismatchHighlight widthMismatches(RenderState state) {
        return widthMismatches(state.document(), state.netlist(), state.netlistToken(), state.snapshot());
    }

    public WidthMismatchHighlight widthMismatches(CircuitDocument document, Netlist netlist, long netlistToken,
                                                  SimulationSnapshot snapshot) {
        useFrame(document, netlist, netlistToken, snapshot);
        if (widthMismatches == null) {
            widthMismatches = WidthMismatchHighlight.of(snapshot, netlist, netlistToken, document, registry,
                    () -> merged(document, netlist));
            counted();
        }
        return widthMismatches;
    }

    /** Every point of every bus: junctions there get the bigger dot. */
    public Set<GridPoint> busPoints(Netlist netlist) {
        if (netlist != busNetlist || busPoints == null) {
            Set<GridPoint> points = new HashSet<>();
            netlist.nets().stream().filter(net -> net.width() > 1).forEach(net -> points.addAll(net.points()));
            busPoints = Set.copyOf(points);
            busNetlist = netlist;
        }
        return busPoints;
    }

    /** For tests: how many frames had their highlights worked out. */
    int computations() {
        return computations;
    }

    /** For tests: how many single highlights were worked out. */
    int highlightComputations() {
        return highlightComputations;
    }

    /** For tests: how many times the merged top-level nets were worked out. */
    int mergedComputations() {
        return mergedComputations;
    }

    /** Forgets the highlights when the frame changes. */
    private void useFrame(CircuitDocument document, Netlist netlist, long netlistToken, SimulationSnapshot snapshot) {
        boolean same = document == this.document && document.revision() == revision && netlist == this.netlist
                && netlistToken == this.netlistToken && snapshot == this.snapshot;
        if (same) {
            return;
        }
        this.document = document;
        this.revision = document.revision();
        this.netlist = netlist;
        this.netlistToken = netlistToken;
        this.snapshot = snapshot;
        frameComputed = false;
        shortCircuits = null;
        widthMismatches = null;
    }

    private void counted() {
        highlightComputations++;
        if (!frameComputed) {
            frameComputed = true;
            computations++;
        }
    }

    private TopLevelNets merged(CircuitDocument document, Netlist netlist) {
        if (merged == null || document != mergedDocument || document.revision() != mergedRevision
                || netlist != mergedNetlist) {
            merged = TopLevelNets.of(document, netlist, registry);
            mergedDocument = document;
            mergedRevision = document.revision();
            mergedNetlist = netlist;
            mergedComputations++;
        }
        return merged;
    }
}
