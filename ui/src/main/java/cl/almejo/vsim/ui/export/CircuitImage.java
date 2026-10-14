package cl.almejo.vsim.ui.export;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.canvas.CircuitBounds;
import cl.almejo.vsim.ui.canvas.CircuitRenderer;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;

import java.awt.Color;
import java.awt.Graphics2D;
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
                         Bounds circuitBounds) {
        this.document = document;
        this.netlist = netlist;
        this.netlistToken = netlistToken;
        this.snapshot = snapshot;
        this.registry = registry;
        this.painters = painters;
        this.scheme = scheme;
        this.gridBounds = new Bounds(circuitBounds.x() - MARGIN, circuitBounds.y() - MARGIN,
                circuitBounds.width() + 2 * MARGIN, circuitBounds.height() + 2 * MARGIN);
    }

    /**
     * The image of {@code only} (empty: everything); the netlist and snapshot are the full circuit's, so values are
     * what the canvas shows. Empty when there is nothing to paint.
     */
    public static Optional<CircuitImage> of(CircuitDocument document, Netlist netlist, long netlistToken,
                                            SimulationSnapshot snapshot, ComponentTypeRegistry registry,
                                            PainterRegistry painters, ColorScheme scheme,
                                            Optional<Set<ElementId>> only) {
        if (only.isEmpty()) {
            return CircuitBounds.of(document, registry).map(bounds ->
                    new CircuitImage(document, netlist, netlistToken, snapshot, registry, painters, scheme, bounds));
        }
        CircuitDocument copy = filtered(document, only.get());
        // The copy has its own revision, and the renderer trusts the netlist and the values only while their tokens
        // match the document's: carry over exactly which of the two matched the original.
        boolean netlistMatches = netlistToken == document.revision();
        boolean valuesMatch = netlistToken == snapshot.token();
        long copyToken = netlistMatches ? copy.revision() : copy.revision() + 1;
        long snapshotToken = valuesMatch ? copyToken : copyToken + 1;
        SimulationSnapshot copySnapshot = new SimulationSnapshot(snapshot.revision(), snapshotToken, snapshot.time(),
                snapshot.running(), snapshot.netValues(), snapshot.componentStates(), snapshot.problems(),
                snapshot.failure());
        return CircuitBounds.of(copy, registry).map(bounds ->
                new CircuitImage(copy, netlist, copyToken, copySnapshot, registry, painters, scheme, bounds));
    }

    /** {@code scheme} on a white background (the «Fondo blanco para imprimir» option). */
    public static ColorScheme printScheme(ColorScheme scheme) {
        return new ColorScheme(Color.WHITE, scheme.grid(), scheme.wireZero(), scheme.wireOne(),
                scheme.wireHighImpedance(), scheme.wireConflict(), scheme.wireUnknown(), scheme.componentStroke(),
                scheme.componentFill(), scheme.componentShade(), scheme.shadow(), scheme.text(), scheme.selection(),
                scheme.preview(), scheme.displayBackground(), scheme.segmentOn(), scheme.segmentOff(),
                scheme.diagramBackground(), scheme.diagramGrid(), scheme.diagramTrace());
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
        graphics.setColor(scheme.background());
        graphics.fillRect(0, 0, width(scale), height(scale));
        graphics.scale(scale, scale);
        graphics.translate(-gridBounds.x() * GridPoint.PIXELS_PER_UNIT, -gridBounds.y() * GridPoint.PIXELS_PER_UNIT);
        new CircuitRenderer(registry, painters, scheme)
                .paint(graphics, new RenderState(document, netlist, netlistToken, snapshot, Set.of(), 0,
                        RenderState.NO_PROBLEM_GLOW));
    }
}
