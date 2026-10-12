package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ComponentDrawing;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.paint.PainterSupport;
import cl.almejo.vsim.ui.paint.SignalColors;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final Font BUS_FONT = LabelLayout.FONT.deriveFont(Font.BOLD, 9f);
    /** Gap between a bus and its value, in document pixels. */
    private static final double BUS_LABEL_GAP = 4;
    private static final double OUTLINE_GAP = 3;
    private static final double MINIMUM_DOT_SPACING = 6;

    private final JunctionCache junctionCache = new JunctionCache();
    private final CrossingCache crossingCache = new CrossingCache();
    private final ComponentTypeRegistry registry;
    private final PainterRegistry painters;
    private final Appearance appearance;
    private final HighlightCache highlights;

    public CircuitRenderer(ComponentTypeRegistry registry, PainterRegistry painters, ColorScheme scheme) {
        this(registry, painters, new Appearance(scheme));
    }

    public CircuitRenderer(ComponentTypeRegistry registry, PainterRegistry painters, Appearance appearance) {
        this.registry = registry;
        this.painters = painters;
        this.appearance = appearance;
        this.highlights = new HighlightCache(registry);
    }

    /** The scheme in use right now; it changes when the user picks another one. */
    public ColorScheme scheme() {
        return appearance.scheme();
    }

    /** The highlights and bus points of the last frame, shared with the canvas so it does not work them out again. */
    public HighlightCache highlights() {
        return highlights;
    }

    /** Background and grid dots, in screen coordinates. */
    public void paintBackground(Graphics2D graphics, Viewport viewport, int width, int height) {
        ColorScheme scheme = scheme();
        graphics.setColor(scheme.background());
        graphics.fillRect(0, 0, width, height);
        int step = viewport.pixelsPerUnit() < MINIMUM_DOT_SPACING ? 4 : 1;
        Point2D topLeft = viewport.screenToGrid(0, 0);
        Point2D bottomRight = viewport.screenToGrid(width, height);
        int firstX = Math.floorDiv((int) Math.floor(topLeft.getX()), step) * step;
        int firstY = Math.floorDiv((int) Math.floor(topLeft.getY()), step) * step;
        int dotSize = viewport.zoom() >= 2 ? 2 : 1;
        graphics.setColor(scheme.grid());
        for (int gridX = firstX; gridX <= bottomRight.getX(); gridX += step) {
            for (int gridY = firstY; gridY <= bottomRight.getY(); gridY += step) {
                Point2D screen = viewport.gridToScreen(gridX, gridY);
                graphics.fillRect((int) Math.round(screen.getX()), (int) Math.round(screen.getY()), dotSize, dotSize);
            }
        }
    }

    public void paint(Graphics2D graphics, RenderState state) {
        ColorScheme scheme = scheme();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        ShortCircuitHighlight highlight = highlights.shortCircuits(state);
        WidthMismatchHighlight mismatches = highlights.widthMismatches(state);

        for (Wire wire : state.document().wires()) {
            if (state.selection().contains(wire.id())) {
                Shape halo = SELECTED_WIRE.createStrokedShape(line(wire));
                graphics.setColor(PainterSupport.withAlpha(scheme.selection(), 90));
                graphics.fill(halo);
                MarchingAnts.draw(graphics, halo, scheme.selection(), SELECTION_OUTLINE_WIDTH,
                        state.selectionDashPhase());
            }
        }
        // one path, so where problem wires meet the glow is not painted twice (and darker)
        Path2D.Double glow = new Path2D.Double();
        for (Wire wire : state.document().wires()) {
            if (highlight.wires().contains(wire.id()) || mismatches.wires().contains(wire.id())) {
                glow.append(line(wire), false);
            }
        }
        graphics.setStroke(PROBLEM_GLOW);
        graphics.setColor(PainterSupport.withAlpha(scheme.wireConflict(), ProblemPulse.alpha(state.problemPulsePhase())));
        graphics.draw(glow);
        // one net lookup and one stroke choice per wire. A gap's width depends on the stroke of each wire crossing it,
        // so when the document has crossings every look is worked out first and kept; without crossings (the common
        // circuit) nothing is kept and no map is made
        Map<ElementId, List<Crossing>> gaps = crossingCache.gapsByVerticalWire(state.document(), registry);
        Map<ElementId, WireLook> looks = gaps.isEmpty() ? Map.of() : new HashMap<>();
        if (!gaps.isEmpty()) {
            for (Wire wire : state.document().wires()) {
                looks.put(wire.id(), WireLook.of(wire, state, highlight, mismatches));
            }
        }
        for (Wire wire : state.document().wires()) {
            WireLook look = gaps.isEmpty() ? WireLook.of(wire, state, highlight, mismatches) : looks.get(wire.id());
            graphics.setStroke(look.stroke());
            graphics.setColor(look.conflict() ? scheme.wireConflict()
                    : SignalColors.wireColor(look.net(), state.snapshot(), state.netlistToken(), scheme));
            List<Crossing> crossings = gaps.getOrDefault(wire.id(), List.of());
            if (crossings.isEmpty()) {
                graphics.draw(line(wire));
            } else {
                drawWithGaps(graphics, wire, crossings, looks, look.stroke());
            }
        }
        Set<GridPoint> busPoints = highlights.busPoints(state.netlist());
        graphics.setColor(scheme.componentStroke());
        for (GridPoint junction : junctionCache.of(state.document())) {
            graphics.fill(circle(junction, busPoints.contains(junction) ? BUS_JUNCTION_RADIUS : JUNCTION_RADIUS));
        }

        for (Component component : state.document().components()) {
            ComponentType type = registry.typeOf(component);
            ComponentDrawing.paint(graphics, type, component, state.snapshot().componentState(component.id().value()),
                    scheme, painters);
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

        paintComponentLabels(graphics, state);
        paintTextLabels(graphics, state);
        paintBusValues(graphics, state);

        for (GridPoint pin : highlight.driverPins()) {
            graphics.setColor(PainterSupport.withAlpha(scheme.wireConflict(), 90));
            graphics.fill(circle(pin, HALO_RADIUS));
            graphics.setStroke(WIRE);
            graphics.setColor(scheme.wireConflict());
            graphics.draw(circle(pin, HALO_RADIUS));
        }
        paintPinWidths(graphics, mismatches);
        paintOpenPins(graphics, state);
    }

    /**
     * How one wire is drawn this frame: its net (looked up once per wire per frame), its stroke and whether it is
     * drawn in the conflict color (short circuit or width mismatch).
     */
    private record WireLook(Optional<Net> net, BasicStroke stroke, boolean conflict) {

        static WireLook of(Wire wire, RenderState state, ShortCircuitHighlight highlight,
                           WidthMismatchHighlight mismatches) {
            Optional<Net> net = state.netlist().netForWire(wire.id());
            if (highlight.wires().contains(wire.id())) {
                return new WireLook(net, CONFLICT_WIRE, true);
            }
            if (mismatches.wires().contains(wire.id())) {
                return new WireLook(net, MISMATCH_WIRE, true);
            }
            boolean bus = net.map(found -> found.width() > 1).orElse(false);
            return new WireLook(net, bus ? BUS_WIRE : WIRE, false);
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
    private void paintOpenPins(Graphics2D graphics, RenderState state) {
        if (state.netlistToken() != state.document().revision()) {
            return;
        }
        ColorScheme scheme = scheme();
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
                boolean open = net.map(found -> found.pins().size() == 1 && found.wires().isEmpty()).orElse(true);
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
    private void paintPinWidths(Graphics2D graphics, WidthMismatchHighlight mismatches) {
        ColorScheme scheme = scheme();
        graphics.setFont(BUS_FONT);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        FontMetrics metrics = graphics.getFontMetrics();
        for (Map.Entry<GridPoint, List<Integer>> entry : mismatches.pinWidths().entrySet()) {
            String text = entry.getValue().stream().map(String::valueOf).collect(Collectors.joining("/"));
            double left = entry.getKey().x() * UNIT + PIN_WIDTH_OFFSET;
            double baseline = entry.getKey().y() * UNIT - PIN_WIDTH_OFFSET;
            paintTag(graphics, metrics, text, left, baseline, scheme.wireConflict());
        }
    }

    public void paintGhost(Graphics2D graphics, DocumentElement element) {
        paintGhost(graphics, element, registry);
    }

    /** A semi-transparent preview; {@code types} resolves components (a pasted fragment brings its own definitions). */
    public void paintGhost(Graphics2D graphics, DocumentElement element, ComponentTypeRegistry types) {
        ColorScheme scheme = scheme();
        Composite previous = graphics.getComposite();
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        try {
            switch (element) {
                case Component component -> ComponentDrawing.paint(graphics, types.typeOf(component),
                        component, VisibleState.NONE, scheme, painters);
                case Wire wire -> {
                    graphics.setStroke(WIRE);
                    graphics.setColor(scheme.preview());
                    graphics.draw(line(wire));
                }
                case TextLabel label -> paintText(graphics, label, scheme.preview());
            }
        } finally {
            graphics.setComposite(previous);
        }
    }

    private void paintComponentLabels(Graphics2D graphics, RenderState state) {
        ColorScheme scheme = scheme();
        graphics.setFont(LabelLayout.FONT);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(scheme.text());
        for (Component component : state.document().components()) {
            LabelLayout.componentLabel(registry.typeOf(component), component)
                    .ifPresent(placed -> graphics.drawString(placed.text(), placed.baselineX(), placed.baselineY()));
        }
    }


    /** Each bus's value in hex, above its longest wire (beside it when vertical), on a backdrop of the board color. */
    private void paintBusValues(Graphics2D graphics, RenderState state) {
        ColorScheme scheme = scheme();
        graphics.setFont(BUS_FONT);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        FontMetrics metrics = graphics.getFontMetrics();
        for (BusLabels.BusLabel label : highlights.busLabels(state)) {
            double textWidth = metrics.stringWidth(label.text());
            double left = label.vertical() ? label.x() + BUS_LABEL_GAP : label.x() - textWidth / 2;
            double baseline = label.vertical() ? label.y() + (metrics.getAscent() - metrics.getDescent()) / 2.0
                    : label.y() - BUS_LABEL_GAP;
            paintTag(graphics, metrics, label.text(), left, baseline, scheme.text());
        }
    }

    /** Text on a translucent backdrop of the board color, so it stays legible over wires. */
    private void paintTag(Graphics2D graphics, FontMetrics metrics, String text, double left, double baseline,
                          Color color) {
        PainterSupport.paintTag(graphics, metrics, text, left, baseline, color, scheme().background());
    }

    private void paintTextLabels(Graphics2D graphics, RenderState state) {
        ColorScheme scheme = scheme();
        for (TextLabel label : state.document().labels()) {
            paintText(graphics, label, scheme.text());
            if (state.selection().contains(label.id())) {
                Rectangle2D bounds = LabelLayout.freeText(label).bounds();
                MarchingAnts.draw(graphics, new Rectangle2D.Double(bounds.getX() - OUTLINE_GAP,
                        bounds.getY() - OUTLINE_GAP, bounds.getWidth() + 2 * OUTLINE_GAP,
                        bounds.getHeight() + 2 * OUTLINE_GAP), scheme.selection(), SELECTION_OUTLINE_WIDTH,
                        state.selectionDashPhase());
            }
        }
    }

    private static void paintText(Graphics2D graphics, TextLabel label, Color color) {
        LabelLayout.PlacedText placed = LabelLayout.freeText(label);
        graphics.setFont(LabelLayout.FONT);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(color);
        graphics.drawString(placed.text(), placed.baselineX(), placed.baselineY());
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
