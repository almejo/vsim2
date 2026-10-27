package cl.almejo.vsim.ui.export;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.canvas.CircuitBounds;
import cl.almejo.vsim.ui.canvas.CircuitRenderer;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.canvas.PaintPolicy;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.paint.ColorRole;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A circuit (or a selection of it) ready to be painted into any {@link Graphics2D}: the bounds with a margin, and the
 * frame as the canvas draws it at zoom 100 % times a scale, without the editing chrome (no grid, no selection).
 */
public final class CircuitImage {

    /** Empty space around the circuit, in grid units. */
    public static final int MARGIN = 2;

    private final CircuitDocument document;
    private final Netlist netlist;
    private final long netlistToken;
    private final SimulationSnapshot snapshot;
    private final ComponentTypeRegistry registry;
    private final PainterRegistry painters;
    private final ColorScheme scheme;
    private final Bounds gridBounds;

    private CircuitImage(CircuitDocument document, Netlist netlist, long netlistToken, SimulationSnapshot snapshot,
                         ComponentTypeRegistry registry, PainterRegistry painters, ColorScheme scheme,
                         Bounds gridBounds) {
        this.document = document;
        this.netlist = netlist;
        this.netlistToken = netlistToken;
        this.snapshot = snapshot;
        this.registry = registry;
        this.painters = painters;
        this.scheme = scheme;
        this.gridBounds = gridBounds;
    }

    /**
     * The image of {@code only} (empty: everything) without the user's sticky notes; see the overload that takes
     * {@code includeStickyNotes}.
     */
    public static Optional<CircuitImage> of(CircuitDocument document, Netlist netlist, long netlistToken,
                                            SimulationSnapshot snapshot, ComponentTypeRegistry registry,
                                            PainterRegistry painters, ColorScheme scheme,
                                            Optional<Set<ElementId>> only) {
        return of(document, netlist, netlistToken, snapshot, registry, painters, scheme, only, false);
    }

    /**
     * The image of {@code only} (empty: everything); the netlist and snapshot are the full circuit's, so values are
     * what the canvas shows. Sticky notes are reminders, not part of the figure: they are left out (and do not grow
     * the bounds) unless {@code includeStickyNotes}. Empty when there is nothing to paint.
     */
    public static Optional<CircuitImage> of(CircuitDocument document, Netlist netlist, long netlistToken,
                                            SimulationSnapshot snapshot, ComponentTypeRegistry registry,
                                            PainterRegistry painters, ColorScheme scheme,
                                            Optional<Set<ElementId>> only, boolean includeStickyNotes) {
        Optional<Set<ElementId>> wanted = only;
        if (!includeStickyNotes && !document.stickyNotes().isEmpty()) {
            wanted = Optional.of(withoutStickyNotes(document, only));
        }
        if (wanted.isEmpty()) {
            return CircuitBounds.of(document, registry).map(bounds ->
                    new CircuitImage(document, netlist, netlistToken, snapshot, registry, painters, scheme,
                            withMargin(bounds)));
        }
        CircuitDocument copy = filtered(document, wanted.get());
        long copyToken = copyToken(copy, document, netlistToken);
        SimulationSnapshot copySnapshot = copySnapshot(snapshot, copyToken, netlistToken);
        return CircuitBounds.of(copy, registry).map(bounds ->
                new CircuitImage(copy, netlist, copyToken, copySnapshot, registry, painters, scheme,
                        withMargin(bounds)));
    }

    /** The ids of {@code only} (empty: every element) that are not sticky notes. */
    private static Set<ElementId> withoutStickyNotes(CircuitDocument document, Optional<Set<ElementId>> only) {
        Set<ElementId> kept = new LinkedHashSet<>();
        for (DocumentElement element : document.elements()) {
            if (!(element instanceof StickyNote) && only.map(ids -> ids.contains(element.id())).orElse(true)) {
                kept.add(element.id());
            }
        }
        return kept;
    }

    /**
     * The image of exactly {@code region} (grid units, no margin added) of the whole circuit, empty or not, without
     * sticky notes: what lies outside the region is cut off (the AI's region pictures, spec §6).
     */
    public static CircuitImage ofRegion(CircuitDocument document, Netlist netlist, long netlistToken,
                                        SimulationSnapshot snapshot, ComponentTypeRegistry registry,
                                        PainterRegistry painters, ColorScheme scheme, Bounds region) {
        return ofRegion(document, netlist, netlistToken, snapshot, registry, painters, scheme, region, false);
    }

    /** Like {@link #ofRegion} with the user's sticky notes painted when {@code includeStickyNotes}. */
    public static CircuitImage ofRegion(CircuitDocument document, Netlist netlist, long netlistToken,
                                        SimulationSnapshot snapshot, ComponentTypeRegistry registry,
                                        PainterRegistry painters, ColorScheme scheme, Bounds region,
                                        boolean includeStickyNotes) {
        if (includeStickyNotes || document.stickyNotes().isEmpty()) {
            return new CircuitImage(document, netlist, netlistToken, snapshot, registry, painters, scheme, region);
        }
        CircuitDocument copy = filtered(document, withoutStickyNotes(document, Optional.empty()));
        long copyToken = copyToken(copy, document, netlistToken);
        return new CircuitImage(copy, netlist, copyToken, copySnapshot(snapshot, copyToken, netlistToken), registry,
                painters, scheme, region);
    }

    /**
     * This image with only {@code ids} of its elements, painted at the same place (the same {@link #gridBounds()},
     * values and colours): one printed sheet's share of a big circuit, so a sheet does not carry the whole circuit.
     */
    public CircuitImage only(Set<ElementId> ids) {
        CircuitDocument copy = filtered(document, ids);
        long copyToken = copyToken(copy, document, netlistToken);
        return new CircuitImage(copy, netlist, copyToken, copySnapshot(snapshot, copyToken, netlistToken), registry,
                painters, scheme, gridBounds);
    }

    private static Bounds withMargin(Bounds circuitBounds) {
        return new Bounds(circuitBounds.x() - MARGIN, circuitBounds.y() - MARGIN, circuitBounds.width() + 2 * MARGIN,
                circuitBounds.height() + 2 * MARGIN);
    }

    /**
     * Where each element is drawn, in grid units ({@link DrawnBounds}, text included, so measure it under the font
     * that will paint), in paint order.
     */
    public Map<ElementId, Bounds> drawnBounds() {
        Map<ElementId, Bounds> result = new LinkedHashMap<>();
        for (DocumentElement element : document.elements()) {
            result.put(element.id(), DrawnBounds.of(element, registry));
        }
        return result;
    }

    // A copy has its own revision, and the renderer trusts the netlist and the values only while their tokens match
    // the document's: carry over exactly which of the two matched the original.
    private static long copyToken(CircuitDocument copy, CircuitDocument original, long netlistToken) {
        return netlistToken == original.revision() ? copy.revision() : copy.revision() + 1;
    }

    private static SimulationSnapshot copySnapshot(SimulationSnapshot snapshot, long copyToken, long netlistToken) {
        long snapshotToken = netlistToken == snapshot.token() ? copyToken : copyToken + 1;
        return new SimulationSnapshot(snapshot.revision(), snapshotToken, snapshot.time(), snapshot.running(),
                snapshot.netValues(), snapshot.componentStates(), snapshot.problems(), snapshot.failure());
    }

    /** {@code scheme} on a white background (the «Fondo blanco para imprimir» option). */
    public static ColorScheme printScheme(ColorScheme scheme) {
        return scheme.with(ColorRole.BACKGROUND, Color.WHITE);
    }

    /** A copy with only {@code ids}, in the same ids and paint order, and the same definitions. */
    private static CircuitDocument filtered(CircuitDocument document, Set<ElementId> ids) {
        CircuitDocument copy = new CircuitDocument();
        for (ComponentDefinition definition : document.definitions()) {
            copy.putDefinition(definition);
        }
        for (DocumentElement element : document.elements()) {
            if (ids.contains(element.id())) {
                copy.restore(element, document.orderOf(element.id()));
            }
        }
        return copy;
    }

    /** The painted area in grid units, margin included. */
    public Bounds gridBounds() {
        return gridBounds;
    }

    public int width(double scale) {
        return pixels(gridBounds.width(), scale);
    }

    public int height(double scale) {
        return pixels(gridBounds.height(), scale);
    }

    private static int pixels(int units, double scale) {
        return (int) Math.ceil(units * GridPoint.PIXELS_PER_UNIT * scale);
    }

    /**
     * Fills the background and paints the circuit so the bounds' top-left corner lands on (0, 0); the glow under
     * problem wires is canvas chrome and is not painted (the problem wires keep their own color and thickness).
     */
    public void paint(Graphics2D graphics, double scale) {
        paint(graphics, scale, PaintPolicy.CANVAS);
    }

    /**
     * Like {@link #paint(Graphics2D, double)} under {@code policy} (the PNG/SVG export uses {@link PaintPolicy#CANVAS});
     * in black and white the background is white whatever the scheme.
     */
    public void paint(Graphics2D graphics, double scale, PaintPolicy policy) {
        graphics.setColor(policy.monochrome() ? Color.WHITE : scheme.background());
        graphics.fillRect(0, 0, width(scale), height(scale));
        graphics.scale(scale, scale);
        graphics.translate(-gridBounds.x() * GridPoint.PIXELS_PER_UNIT, -gridBounds.y() * GridPoint.PIXELS_PER_UNIT);
        new CircuitRenderer(registry, painters, scheme)
                .paint(graphics, new RenderState(document, netlist, netlistToken, snapshot, Set.of(), 0,
                        RenderState.NO_PROBLEM_GLOW, Set.of(), policy));
    }
}
