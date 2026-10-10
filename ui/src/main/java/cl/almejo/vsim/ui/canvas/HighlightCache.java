package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The problem highlights, bus points and bus labels of the frame being painted. The highlights are worked out again
 * only when the document (identity and revision), the netlist (identity and token) or the snapshot's token and
 * problems change, so a running simulation (a new snapshot many times a second, usually with the same problems) keeps
 * them. Each highlight is worked out only when asked for, and the merged top-level nets ({@link TopLevelNets}) and the
 * bus label anchors once per document revision and netlist. One per canvas, shared by its renderer, its overlays and
 * its tooltips; EDT only. Holds geometry and ids only, never scheme colors.
 */
public final class HighlightCache {

    private final ComponentTypeRegistry registry;
    private CircuitDocument document;
    private long revision;
    private Netlist netlist;
    private long netlistToken;
    private long snapshotToken;
    private List<Problem> problems;
    private boolean frameComputed;
    private ShortCircuitHighlight shortCircuits;
    private WidthMismatchHighlight widthMismatches;
    private CircuitDocument mergedDocument;
    private long mergedRevision;
    private Netlist mergedNetlist;
    private TopLevelNets merged;
    private Netlist busNetlist;
    private Set<GridPoint> busPoints;
    private CircuitDocument anchorDocument;
    private long anchorRevision;
    private Netlist anchorNetlist;
    private List<BusLabels.Anchor> anchors;
    private SimulationSnapshot labelSnapshot;
    private long labelToken;
    private List<BusLabels.BusLabel> labels;
    private int busAnchorComputations;
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
                    () -> topLevelNets(document, netlist));
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
                    () -> topLevelNets(document, netlist));
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

    /** Where each bus of {@code state} writes its value ({@link BusLabels}); the anchors once per revision and netlist. */
    public List<BusLabels.BusLabel> busLabels(RenderState state) {
        CircuitDocument document = state.document();
        Netlist netlist = state.netlist();
        if (anchors == null || document != anchorDocument || document.revision() != anchorRevision
                || netlist != anchorNetlist) {
            anchors = BusLabels.anchors(document, netlist);
            anchorDocument = document;
            anchorRevision = document.revision();
            anchorNetlist = netlist;
            labels = null;
            busAnchorComputations++;
        }
        if (labels == null || state.snapshot() != labelSnapshot || state.netlistToken() != labelToken) {
            labels = BusLabels.of(state, anchors);
            labelSnapshot = state.snapshot();
            labelToken = state.netlistToken();
        }
        return labels;
    }

    /** For tests: how many times the bus label anchors were worked out. */
    int busAnchorComputations() {
        return busAnchorComputations;
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
                && netlistToken == this.netlistToken && snapshot.token() == snapshotToken
                && snapshot.problems().equals(problems);
        if (same) {
            return;
        }
        this.document = document;
        this.revision = document.revision();
        this.netlist = netlist;
        this.netlistToken = netlistToken;
        this.snapshotToken = snapshot.token();
        this.problems = snapshot.problems();
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

    /**
     * The top-level nets of {@code document} merged through templates; flattening is costly, so worked out once per
     * document revision and netlist and shared by the highlights and the tooltips.
     */
    public TopLevelNets topLevelNets(CircuitDocument document, Netlist netlist) {
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
