package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.AppPreferences.GridStyle;
import cl.almejo.vsim.ui.frames.FramePainter;
import cl.almejo.vsim.ui.memory.MemoryNames;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ComponentDrawing;
import cl.almejo.vsim.ui.paint.MemoryPainter;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.paint.PainterSupport;
import cl.almejo.vsim.ui.paint.TextFonts;
import cl.almejo.vsim.ui.paint.SignalColors;
import cl.almejo.vsim.ui.paint.TunnelPainter;
import cl.almejo.vsim.ui.sticky.StickyNoteLayout;
import cl.almejo.vsim.ui.sticky.StickyNotePainter;
import cl.almejo.vsim.ui.paint.WireState;
import cl.almejo.vsim.ui.paint.MonochromeGraphics;
import cl.almejo.vsim.ui.paint.MonochromeScheme;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

/** Paints a circuit. {@link #paint} and {@link #paintGhost} work in document coordinates (8 px per grid unit). */
public final class CircuitRenderer {

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final BasicStroke WIRE = new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke CONFLICT_WIRE = new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke BUS_WIRE = new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke SELECTED_WIRE = new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke CONFLICT_OUTLINE = new BasicStroke(1.5f, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER, 10f, new float[]{4f, 3f}, 0f);
    /** The glow under a problem wire is this much wider than the wire's own stroke, in document pixels. */
    private static final BasicStroke PROBLEM_GLOW = new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final float SELECTION_OUTLINE_WIDTH = 1.5f;
    private static final BasicStroke MISMATCH_WIRE = new BasicStroke(4.5f, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_ROUND, 10f, new float[]{6f, 4f}, 0f);
    /** Where a mismatched pin's width is written, from the pin, in document pixels. */
    private static final double PIN_WIDTH_OFFSET = 4;
    private static final double JUNCTION_RADIUS = 3.5;
    private static final double BUS_JUNCTION_RADIUS = 5;
    private static final double HALO_RADIUS = 7;
    /** How much bigger than the pin dot the mark of an open pin is, so its hole is obvious and covers the dot. */
    private static final double OPEN_PIN_MARGIN = 1;
    private static final double OPEN_PIN_RADIUS = PainterSupport.PIN_DOT_RADIUS + OPEN_PIN_MARGIN;
    private static final double OPEN_BUS_PIN_HALF_SIDE = PainterSupport.BUS_PIN_HALF_SIDE + OPEN_PIN_MARGIN;
    private static final BasicStroke OPEN_PIN_RING = new BasicStroke(1f);
    /** Gap between a bus and its value, in document pixels. */
    private static final double BUS_LABEL_GAP = 4;
    private static final double OUTLINE_GAP = 3;
    private static final double MINIMUM_DOT_SPACING = 6;
    /** The grid of lines: a stronger line every fifth minor line (engineering paper: every 10 units at 2). */
    private static final int MINOR_LINES_PER_MAJOR = 5;

    private final JunctionCache junctionCache = new JunctionCache();
    private final CrossingCache crossingCache = new CrossingCache();
    private final ComponentTypeRegistry registry;
    private final PainterRegistry painters;
    private final Appearance appearance;
    private final HighlightCache highlights;
    private final ConnectionPoints connectionPoints;

    public CircuitRenderer(ComponentTypeRegistry registry, PainterRegistry painters, ColorScheme scheme) {
        this(registry, painters, new Appearance(scheme));
    }

    public CircuitRenderer(ComponentTypeRegistry registry, PainterRegistry painters, Appearance appearance) {
        this.registry = registry;
        this.painters = painters;
        this.appearance = appearance;
        this.highlights = new HighlightCache(registry);
        this.connectionPoints = new ConnectionPoints(registry);
    }

    /** The scheme in use right now; it changes when the user picks another one. */
    public ColorScheme scheme() {
        return appearance.scheme();
    }

    /** The highlights and bus points of the last frame, shared with the canvas so it does not work them out again. */
    public HighlightCache highlights() {
        return highlights;
    }

    /** {@link #paintBackground(Graphics2D, Viewport, int, int, GridStyle, int)} with the default 2-unit lines. */
    public void paintBackground(Graphics2D graphics, Viewport viewport, int width, int height, GridStyle style) {
        paintBackground(graphics, viewport, width, height, style, AppPreferences.DEFAULT_GRID_LINE_SPACING);
    }

    /**
     * Background and grid in {@code style} (dots, lines every {@code lineSpacing} units or nothing), in screen
     * coordinates. Editor chrome only: image export and the PDF paint their own background and never call this.
     */
    public void paintBackground(Graphics2D graphics, Viewport viewport, int width, int height, GridStyle style,
                                int lineSpacing) {
        ColorScheme scheme = scheme();
        graphics.setColor(scheme.background());
        graphics.fillRect(0, 0, width, height);
        switch (style) {
            case DOTS -> paintGridDots(graphics, viewport, width, height);
            case LINES -> paintGridLines(graphics, viewport, width, height, lineSpacing);
            case NONE -> {
            }
        }
    }

    /** Today's grid: a dot on every unit (every fourth one when they would be too close). */
    public void paintGridDots(Graphics2D graphics, Viewport viewport, int width, int height) {
        int step = viewport.pixelsPerUnit() < MINIMUM_DOT_SPACING ? 4 : 1;
        Point2D topLeft = viewport.screenToGrid(0, 0);
        Point2D bottomRight = viewport.screenToGrid(width, height);
        int firstX = Math.floorDiv((int) Math.floor(topLeft.getX()), step) * step;
        int firstY = Math.floorDiv((int) Math.floor(topLeft.getY()), step) * step;
        int dotSize = viewport.zoom() >= 2 ? 2 : 1;
        graphics.setColor(scheme().grid());
        for (int gridX = firstX; gridX <= bottomRight.getX(); gridX += step) {
            for (int gridY = firstY; gridY <= bottomRight.getY(); gridY += step) {
                Point2D screen = viewport.gridToScreen(gridX, gridY);
                graphics.fillRect((int) Math.round(screen.getX()), (int) Math.round(screen.getY()), dotSize, dotSize);
            }
        }
    }

    /**
     * Engineering paper (spec §4.6): a 1 px line every {@code lineSpacing} units and a stronger one every
     * {@value #MINOR_LINES_PER_MAJOR} of them; the lines closer than the dots' minimum spacing are left out.
     */
    private void paintGridLines(Graphics2D graphics, Viewport viewport, int width, int height, int lineSpacing) {
        OptionalInt finest = finestLineStep(viewport.pixelsPerUnit(), lineSpacing);
        int majorStep = lineSpacing * MINOR_LINES_PER_MAJOR;
        if (finest.isEmpty()) {
            return;
        }
        int step = finest.getAsInt();
        ColorScheme scheme = scheme();
        Point2D topLeft = viewport.screenToGrid(0, 0);
        Point2D bottomRight = viewport.screenToGrid(width, height);
        for (int gridX = firstMultiple(topLeft.getX(), step); gridX <= bottomRight.getX(); gridX += step) {
            graphics.setColor(lineColor(scheme, gridX, majorStep));
            graphics.fillRect((int) Math.round(viewport.gridToScreen(gridX, 0).getX()), 0, 1, height);
        }
        for (int gridY = firstMultiple(topLeft.getY(), step); gridY <= bottomRight.getY(); gridY += step) {
            graphics.setColor(lineColor(scheme, gridY, majorStep));
            graphics.fillRect(0, (int) Math.round(viewport.gridToScreen(0, gridY).getY()), width, 1);
        }
    }

    private static Color lineColor(ColorScheme scheme, int gridCoordinate, int majorStep) {
        return Math.floorMod(gridCoordinate, majorStep) == 0 ? scheme.gridLineMajor() : scheme.gridLine();
    }

    private static int firstMultiple(double gridCoordinate, int step) {
        return Math.floorDiv((int) Math.floor(gridCoordinate), step) * step;
    }

    /**
     * The step in grid units of the finest lines worth drawing at {@code pixelsPerUnit} with minor lines every
     * {@code lineSpacing} units: the minor lines, only the major ones when the minor would be under
     * {@code MINIMUM_DOT_SPACING} pixels apart, none when even those are.
     */
    static OptionalInt finestLineStep(double pixelsPerUnit, int lineSpacing) {
        if (lineSpacing * pixelsPerUnit >= MINIMUM_DOT_SPACING) {
            return OptionalInt.of(lineSpacing);
        }
        int majorStep = lineSpacing * MINOR_LINES_PER_MAJOR;
        if (majorStep * pixelsPerUnit >= MINIMUM_DOT_SPACING) {
            return OptionalInt.of(majorStep);
        }
        return OptionalInt.empty();
    }

    /**
     * Paints the frame under {@code state.policy()}: in black and white with {@link MonochromeScheme} and through
     * {@link MonochromeGraphics}, so no colour gets through.
     */
    public void paint(Graphics2D graphics, RenderState state) {
        if (state.policy().monochrome()) {
            paintScene(new MonochromeGraphics(graphics), state, MonochromeScheme.of(scheme()));
        } else {
            paintScene(graphics, state, scheme());
        }
    }

    private void paintScene(Graphics2D graphics, RenderState state, ColorScheme scheme) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        ShortCircuitHighlight highlight = highlights.shortCircuits(state);
        WidthMismatchHighlight mismatches = highlights.widthMismatches(state);

        paintFrames(graphics, state, scheme);
        paintSelectedWireHalos(graphics, state, scheme);
        paintProblemGlow(graphics, state, scheme, highlight, mismatches);
        paintWires(graphics, state, scheme, highlight, mismatches);
        paintJunctions(graphics, state, scheme);
        paintComponents(graphics, state, scheme, highlight);

        paintComponentLabels(graphics, state, scheme);
        paintTextLabels(graphics, state, scheme);
        paintBusValues(graphics, state, scheme);
        paintDriverPinHalos(graphics, scheme, highlight);
        paintPinWidths(graphics, mismatches, scheme);
        paintOpenPins(graphics, state, scheme);
        paintStickyNotes(graphics, state, scheme);
    }

    /** The HiDPI scale of the screen {@code graphics} paints on, 1 where it has no device configuration. */
    private static double deviceScale(Graphics2D graphics) {
        GraphicsConfiguration configuration = graphics.getDeviceConfiguration();
        double scale = configuration == null ? 1 : configuration.getDefaultTransform().getScaleX();
        return scale > 0 ? scale : 1;
    }

    /** The user's sticky notes, the last pass: paper on top of everything, in document order. */
    private void paintStickyNotes(Graphics2D graphics, RenderState state, ColorScheme scheme) {
        if (!state.policy().paintStickyNotes()) {
            return;
        }
        // logical pixels, like the hit tester's: the device (HiDPI) scale is not part of the zoom the user sees
        double pixelsPerGrid = UNIT * graphics.getTransform().getScaleX() / deviceScale(graphics);
        for (StickyNote note : state.document().stickyNotes()) {
            StickyNotePainter.paint(graphics, note, scheme, pixelsPerGrid, state.policy());
            if (state.selection().contains(note.id())) {
                Rectangle2D bounds = StickyNoteLayout.paperOf(note);
                MarchingAnts.draw(graphics, new Rectangle2D.Double(bounds.getX() - OUTLINE_GAP,
                        bounds.getY() - OUTLINE_GAP, bounds.getWidth() + 2 * OUTLINE_GAP,
                        bounds.getHeight() + 2 * OUTLINE_GAP), scheme.selection(), SELECTION_OUTLINE_WIDTH,
                        state.selectionDashPhase());
            }
        }
    }

    /** Halo and marching ants under each selected, visible wire. */
    private void paintSelectedWireHalos(Graphics2D graphics, RenderState state, ColorScheme scheme) {
        for (Wire wire : state.document().wires()) {
            if (state.selection().contains(wire.id()) && !state.hiddenWires().contains(wire.id())) {
                Shape halo = SELECTED_WIRE.createStrokedShape(line(wire));
                graphics.setColor(PainterSupport.withAlpha(scheme.selection(), 90));
                graphics.fill(halo);
                MarchingAnts.draw(graphics, halo, scheme.selection(), SELECTION_OUTLINE_WIDTH,
                        state.selectionDashPhase());
            }
        }
    }

    /** The pulsing glow under wires with a short circuit or a width mismatch. */
    private void paintProblemGlow(Graphics2D graphics, RenderState state, ColorScheme scheme,
                                  ShortCircuitHighlight highlight, WidthMismatchHighlight mismatches) {
        if (state.paintsProblemGlow()) {
            // one path, so where problem wires meet the glow is not painted twice (and darker)
            Path2D.Double glow = new Path2D.Double();
            for (Wire wire : state.document().wires()) {
                if ((highlight.wires().contains(wire.id()) || mismatches.wires().contains(wire.id()))
                        && !state.hiddenWires().contains(wire.id())) {
                    glow.append(line(wire), false);
                }
            }
            graphics.setStroke(PROBLEM_GLOW);
            graphics.setColor(PainterSupport.withAlpha(scheme.wireConflict(),
                    ProblemPulse.alpha(state.problemPulsePhase())));
            graphics.draw(glow);
        }
    }

    /** The wires themselves, with a gap where one crosses another. */
    private void paintWires(Graphics2D graphics, RenderState state, ColorScheme scheme,
                            ShortCircuitHighlight highlight, WidthMismatchHighlight mismatches) {
        // one net lookup and one stroke choice per wire. A gap's width depends on the stroke of each wire crossing it,
        // so when the document has crossings every look is worked out first and kept; without crossings (the common
        // circuit) nothing is kept and no map is made
        Map<ElementId, List<Crossing>> gaps = crossingCache.gapsByVerticalWire(state.document(), registry);
        Map<ElementId, WireLook> looks = gaps.isEmpty() ? Map.of() : new HashMap<>();
        if (!gaps.isEmpty()) {
            for (Wire wire : state.document().wires()) {
                looks.put(wire.id(), WireLook.of(wire, state, highlight, mismatches, scheme));
            }
        }
        for (Wire wire : state.document().wires()) {
            if (state.hiddenWires().contains(wire.id())) {
                continue;
            }
            WireLook look = gaps.isEmpty() ? WireLook.of(wire, state, highlight, mismatches, scheme)
                    : looks.get(wire.id());
            graphics.setStroke(look.stroke());
            graphics.setColor(look.color());
            List<Crossing> crossings = gaps.getOrDefault(wire.id(), List.of());
            if (crossings.isEmpty()) {
                graphics.draw(line(wire));
            } else {
                drawWithGaps(graphics, wire, crossings, looks, look.stroke());
            }
        }
    }

    /** The dots where wires join. */
    private void paintJunctions(Graphics2D graphics, RenderState state, ColorScheme scheme) {
        Set<GridPoint> busPoints = highlights.busPoints(state.netlist());
        graphics.setColor(scheme.componentStroke());
        for (GridPoint junction : junctionCache.of(state.document())) {
            graphics.fill(circle(junction, busPoints.contains(junction) ? BUS_JUNCTION_RADIUS : JUNCTION_RADIUS));
        }
    }

    /** Every component, with its conflict and selection outlines. */
    private void paintComponents(Graphics2D graphics, RenderState state, ColorScheme scheme,
                                 ShortCircuitHighlight highlight) {
        Map<String, Integer> tunnelColors = Tunnels.colorsByName(state.document());
        MemoryNames.Numbering memoryNames = new MemoryNames.Numbering();
        for (Component component : state.document().components()) {
            ComponentType type = registry.typeOf(component);
            if (painters.painterFor(type.id()) instanceof TunnelPainter tunnelPainter) {
                TunnelPainter.Look look = tunnelLook(component, state, tunnelColors, scheme);
                ComponentDrawing.paintPlaced(graphics, component,
                        local -> tunnelPainter.paint(local, component, look, scheme));
            } else if (painters.painterFor(type.id()) instanceof MemoryPainter memoryPainter) {
                // The name sits in the memory's title bar (RAM1, ROM1... per kind when it has no label).
                String name = memoryNames.nameOf(component);
                VisibleState memoryState = state.snapshot().componentState(component.id().value());
                ComponentDrawing.paintPlaced(graphics, component, local -> memoryPainter.paintWithLabel(local, type,
                        component.parameters(), memoryState, scheme, name));
            } else {
                ComponentDrawing.paint(graphics, type, component,
                        state.snapshot().componentState(component.id().value()), scheme, painters);
            }
            if (highlight.components().contains(component.id())) {
                graphics.setStroke(CONFLICT_OUTLINE);
                graphics.setColor(scheme.wireConflict());
                graphics.draw(outline(type, component));
            }
            if (state.selection().contains(component.id())) {
                MarchingAnts.draw(graphics, outline(type, component), scheme.selection(), SELECTION_OUTLINE_WIDTH,
                        state.selectionDashPhase());
            }
        }
    }

    /** A halo on each pin driving a conflicted net. */
    private void paintDriverPinHalos(Graphics2D graphics, ColorScheme scheme, ShortCircuitHighlight highlight) {
        for (GridPoint pin : highlight.driverPins()) {
            graphics.setColor(PainterSupport.withAlpha(scheme.wireConflict(), 90));
            graphics.fill(circle(pin, HALO_RADIUS));
            graphics.setStroke(WIRE);
            graphics.setColor(scheme.wireConflict());
            graphics.draw(circle(pin, HALO_RADIUS));
        }
    }

    /**
     * How a tunnel looks this frame: a salida takes its entrada's colour (grey without one), and the stub is drawn
     * like the wire it joins, in its net's state colour and thick when that net is a bus. The tunnel's pin has no
     * width of its own, so the width is the net's. In black and white every tunnel is the same dark grey and the
     * stub takes its wire's grey.
     */
    private TunnelPainter.Look tunnelLook(Component tunnel, RenderState state, Map<String, Integer> tunnelColors,
                                          ColorScheme scheme) {
        Optional<Net> net = state.netlist().netForPin(new PinRef(tunnel.id(), 0));
        boolean bus = net.map(found -> found.width() > 1).orElse(false);
        if (state.policy().monochrome()) {
            return new TunnelPainter.Look(MonochromeScheme.TUNNEL, MonochromeScheme.monochromeLook(
                    WireState.of(net, state.snapshot(), state.netlistToken()), bus).color(), bus);
        }
        return new TunnelPainter.Look(scheme.tunnelColor(TunnelPainter.colorIndex(tunnel, tunnelColors)),
                SignalColors.wireColor(net, state.snapshot(), state.netlistToken(), scheme), bus);
    }

    /** Frames under everything else, the larger first so a nested frame's tint shows over its parent's. */
    private void paintFrames(Graphics2D graphics, RenderState state, ColorScheme scheme) {
        List<Frame> frames = state.document().frames();
        if (frames.isEmpty()) {
            return;
        }
        double pixelsPerGrid = UNIT * graphics.getTransform().getScaleX();
        for (Frame frame : frames.stream()
                .sorted(Comparator.comparingLong((Frame candidate) -> (long) candidate.width() * candidate.height())
                        .reversed())
                .toList()) {
            FramePainter.paint(graphics, frame, scheme, pixelsPerGrid, state.policy());
            if (state.selection().contains(frame.id())) {
                Rectangle2D bounds = FramePainter.bounds(frame);
                MarchingAnts.draw(graphics, new Rectangle2D.Double(bounds.getX() - OUTLINE_GAP,
                        bounds.getY() - OUTLINE_GAP, bounds.getWidth() + 2 * OUTLINE_GAP,
                        bounds.getHeight() + 2 * OUTLINE_GAP), scheme.selection(), SELECTION_OUTLINE_WIDTH,
                        state.selectionDashPhase());
            }
        }
    }

    /**
     * How one wire is drawn this frame: its stroke and colour, from its net (looked up once per wire per frame) and
     * whether it is a problem wire (short circuit or width mismatch, in the conflict color). In black and white the
     * state is told by grey level and dash pattern ({@link MonochromeScheme#monochromeLook}); a mismatched wire keeps
     * its own dashes, in black. The stroke is never thinner than the policy's minimum.
     */
    private record WireLook(BasicStroke stroke, Color color) {

        static WireLook of(Wire wire, RenderState state, ShortCircuitHighlight highlight,
                           WidthMismatchHighlight mismatches, ColorScheme scheme) {
            PaintPolicy policy = state.policy();
            Optional<Net> net = state.netlist().netForWire(wire.id());
            boolean bus = net.map(found -> found.width() > 1).orElse(false);
            if (policy.monochrome()) {
                MonochromeScheme.WireLook look;
                if (highlight.wires().contains(wire.id())) {
                    look = MonochromeScheme.monochromeLook(WireState.CONFLICT, bus);
                } else if (mismatches.wires().contains(wire.id())) {
                    look = new MonochromeScheme.WireLook(MonochromeScheme.BLACK, MISMATCH_WIRE);
                } else {
                    look = MonochromeScheme.monochromeLook(
                            WireState.of(net, state.snapshot(), state.netlistToken()), bus);
                }
                return new WireLook(policy.widened(look.stroke()), look.color());
            }
            if (highlight.wires().contains(wire.id())) {
                return new WireLook(policy.widened(CONFLICT_WIRE), scheme.wireConflict());
            }
            if (mismatches.wires().contains(wire.id())) {
                return new WireLook(policy.widened(MISMATCH_WIRE), scheme.wireConflict());
            }
            return new WireLook(policy.widened(bus ? BUS_WIRE : WIRE),
                    SignalColors.wireColor(net, state.snapshot(), state.netlistToken(), scheme));
        }
    }

    /**
     * How far {@code stroke} paints past the end of a line: half its width for round (and square) caps, nothing for
     * a butt cap (the dashed mismatch stroke).
     */
    private static double capReach(BasicStroke stroke) {
        return stroke.getEndCap() == BasicStroke.CAP_BUTT ? 0 : stroke.getLineWidth() / 2;
    }

    /**
     * A vertical wire that other wires cross, drawn as the spans between the gaps ({@link WireGaps}), so it reads as
     * passing under them. The gap allows for the vertical stroke's own cap ({@link #capReach}), so a round-capped and
     * a butt-capped stroke both stop {@link WireGaps#CLEARANCE} pixels from the horizontal stroke. A dashed stroke
     * restarts its pattern on each span.
     */
    private static void drawWithGaps(Graphics2D graphics, Wire wire, List<Crossing> crossings,
                                     Map<ElementId, WireLook> looks, BasicStroke stroke) {
        List<WireGaps.Cut> cuts = crossings.stream()
                .map(crossing -> new WireGaps.Cut(crossing.point().y() * UNIT,
                        lineWidth(looks.get(crossing.horizontalWire()))))
                .toList();
        double x = wire.start().x() * UNIT;
        for (WireGaps.Span span : WireGaps.visibleSpans(wire.start().y() * UNIT, wire.end().y() * UNIT, cuts,
                capReach(stroke))) {
            graphics.draw(new Line2D.Double(x, span.from(), x, span.to()));
        }
    }

    private static double lineWidth(WireLook look) {
        return (look == null ? WIRE : look.stroke()).getLineWidth();
    }

    /**
     * An empty socket (a hollow mark, a little bigger than the pin dot) on each pin that nothing else touches (no
     * wire, no other pin): every input pin of any component (M6.1-E: a loose input stays Z, and this shows it) and
     * every pin of a bus splitter or joiner, so a bit left unwired is noticed. Outputs of other components keep their
     * dot. Only with a netlist that matches the document.
     */
    private void paintOpenPins(Graphics2D graphics, RenderState state, ColorScheme scheme) {
        if (state.netlistToken() != state.document().revision()) {
            return;
        }
        graphics.setStroke(OPEN_PIN_RING);
        for (Component component : state.document().components()) {
            boolean busPart = StandardComponentTypes.BUS_SPLITTER.equals(component.typeId())
                    || StandardComponentTypes.BUS_JOINER.equals(component.typeId());
            ComponentType type = registry.typeOf(component);
            for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                if (!busPart && !type.pin(pinIndex).isInput()) {
                    continue;
                }
                Optional<Net> net = state.netlist().netForPin(new PinRef(component.id(), pinIndex));
                boolean open = connectionPoints.isOpen(state.document(), component, pinIndex, net);
                if (!open) {
                    continue;
                }
                GridPoint location = type.pinLocation(component, pinIndex);
                Shape mark = type.pin(pinIndex).width() > 1
                        ? new Rectangle2D.Double(location.x() * UNIT - OPEN_BUS_PIN_HALF_SIDE,
                        location.y() * UNIT - OPEN_BUS_PIN_HALF_SIDE, 2 * OPEN_BUS_PIN_HALF_SIDE,
                        2 * OPEN_BUS_PIN_HALF_SIDE)
                        : circle(location, OPEN_PIN_RADIUS);
                graphics.setColor(scheme.background());
                graphics.fill(mark);
                graphics.setColor(scheme.componentStroke());
                graphics.draw(mark);
            }
        }
    }

    /** The widths of the pins of mismatched nets ("1", "8", or "1/8" for pins touching at one point). */
    private void paintPinWidths(Graphics2D graphics, WidthMismatchHighlight mismatches, ColorScheme scheme) {
        graphics.setFont(TextFonts.tagFont());
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        FontMetrics metrics = graphics.getFontMetrics();
        for (Map.Entry<GridPoint, List<Integer>> entry : mismatches.pinWidths().entrySet()) {
            String text = entry.getValue().stream().map(String::valueOf).collect(Collectors.joining("/"));
            double left = entry.getKey().x() * UNIT + PIN_WIDTH_OFFSET;
            double baseline = entry.getKey().y() * UNIT - PIN_WIDTH_OFFSET;
            paintTag(graphics, metrics, text, left, baseline, scheme.wireConflict(), scheme);
        }
    }

    /** The registry this renderer resolves the circuit's components with. */
    public ComponentTypeRegistry registry() {
        return registry;
    }

    /** A ghost drawn without its circuit: a salida in palette colour 0 (see the four-argument overload). */
    public void paintGhost(Graphics2D graphics, DocumentElement element) {
        paintGhost(graphics, element, registry);
    }

    /** Like {@link #paintGhost(Graphics2D, DocumentElement, ComponentTypeRegistry, Map)} without tunnel colours. */
    public void paintGhost(Graphics2D graphics, DocumentElement element, ComponentTypeRegistry types) {
        paintGhost(graphics, element, types, Map.of());
    }

    /**
     * A semi-transparent preview; {@code types} resolves components (a pasted fragment brings its own definitions).
     * A salida's tag takes the colour of its name in {@code tunnelColors} (palette colour 0 for a name without one),
     * so the ghost shows the colour it will have once placed; the caller, who knows the target circuit, resolves them
     * ({@link Tunnels#colorsByName}). An entrada keeps its own colour.
     */
    public void paintGhost(Graphics2D graphics, DocumentElement element, ComponentTypeRegistry types,
                           Map<String, Integer> tunnelColors) {
        ColorScheme scheme = scheme();
        Composite previous = graphics.getComposite();
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        try {
            switch (element) {
                case Component component when painters.painterFor(component.typeId())
                        instanceof TunnelPainter tunnelPainter ->
                        paintTunnelGhost(graphics, component, tunnelPainter, tunnelColors, scheme);
                case Component component -> ComponentDrawing.paint(graphics, types.typeOf(component),
                        component, VisibleState.NONE, scheme, painters);
                case Wire wire -> {
                    graphics.setStroke(WIRE);
                    graphics.setColor(scheme.preview());
                    graphics.draw(line(wire));
                }
                case TextLabel label -> paintNoteGhost(graphics, label, scheme);
                case Frame frame -> FramePainter.paint(graphics, frame, scheme, Double.MAX_VALUE);
                case StickyNote note -> StickyNotePainter.paintGhost(graphics, note, scheme.preview());
            }
        } finally {
            graphics.setComposite(previous);
        }
    }

    private static void paintTunnelGhost(Graphics2D graphics, Component tunnel, TunnelPainter tunnelPainter,
                                         Map<String, Integer> tunnelColors, ColorScheme scheme) {
        int colorIndex = TunnelPainter.colorIndex(tunnel, tunnelColors);
        TunnelPainter.Look look = new TunnelPainter.Look(
                scheme.tunnelColor(colorIndex == Tunnels.NO_COLOR ? 0 : colorIndex), scheme.componentStroke(), false);
        ComponentDrawing.paintPlaced(graphics, tunnel, local -> tunnelPainter.paint(local, tunnel, look, scheme));
    }

    private void paintComponentLabels(Graphics2D graphics, RenderState state, ColorScheme scheme) {
        graphics.setFont(LabelLayout.font());
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(scheme.text());
        for (Component component : state.document().components()) {
            LabelLayout.componentLabel(registry.typeOf(component), component)
                    .ifPresent(placed -> graphics.drawString(placed.text(), placed.baselineX(), placed.baselineY()));
        }
    }


    /** Each bus's value in hex, above its longest wire (beside it when vertical), on a backdrop of the board color. */
    private void paintBusValues(Graphics2D graphics, RenderState state, ColorScheme scheme) {
        graphics.setFont(TextFonts.tagFont());
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        FontMetrics metrics = graphics.getFontMetrics();
        for (BusLabels.BusLabel label : highlights.busLabels(state)) {
            double textWidth = metrics.stringWidth(label.text());
            double left = label.vertical() ? label.x() + BUS_LABEL_GAP : label.x() - textWidth / 2;
            double baseline = label.vertical() ? label.y() + (metrics.getAscent() - metrics.getDescent()) / 2.0
                    : label.y() - BUS_LABEL_GAP;
            paintTag(graphics, metrics, label.text(), left, baseline, scheme.text(), scheme);
        }
    }

    /** Text on a translucent backdrop of the board color, so it stays legible over wires. */
    private static void paintTag(Graphics2D graphics, FontMetrics metrics, String text, double left,
                                 double baseline, Color color, ColorScheme scheme) {
        PainterSupport.paintTag(graphics, metrics, text, left, baseline, color, scheme.background());
    }

    private void paintTextLabels(Graphics2D graphics, RenderState state, ColorScheme scheme) {
        for (TextLabel label : state.document().labels()) {
            NotePainter.paint(graphics, label, scheme, state.policy());
            if (state.selection().contains(label.id())) {
                Rectangle2D bounds = LabelLayout.noteBox(label);
                MarchingAnts.draw(graphics, new Rectangle2D.Double(bounds.getX() - OUTLINE_GAP,
                        bounds.getY() - OUTLINE_GAP, bounds.getWidth() + 2 * OUTLINE_GAP,
                        bounds.getHeight() + 2 * OUTLINE_GAP), scheme.selection(), SELECTION_OUTLINE_WIDTH,
                        state.selectionDashPhase());
            }
        }
    }

    /**
     * The ghost of a note, in the preview colour like every other ghost: its lines and, when it has a box, the box's
     * outline (no fill, so what lies under the drop point shows through; review M4).
     */
    private static void paintNoteGhost(Graphics2D graphics, TextLabel label, ColorScheme scheme) {
        if (label.style().boxed()) {
            NotePainter.paintOutline(graphics, label, scheme.preview());
        }
        NotePainter.paintLines(graphics, label, scheme.preview());
    }

    private Rectangle2D outline(ComponentType type, Component component) {
        Rectangle2D bounds = ComponentDrawing.documentBounds(type, component);
        return new Rectangle2D.Double(bounds.getX() - OUTLINE_GAP, bounds.getY() - OUTLINE_GAP,
                bounds.getWidth() + 2 * OUTLINE_GAP, bounds.getHeight() + 2 * OUTLINE_GAP);
    }

    private static Line2D line(Wire wire) {
        return new Line2D.Double(wire.start().x() * UNIT, wire.start().y() * UNIT,
                wire.end().x() * UNIT, wire.end().y() * UNIT);
    }

    private static Ellipse2D circle(GridPoint center, double radius) {
        return new Ellipse2D.Double(center.x() * UNIT - radius, center.y() * UNIT - radius, 2 * radius, 2 * radius);
    }
}
