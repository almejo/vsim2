package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ComponentDrawing;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.paint.SignalColors;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/** Paints a circuit. {@link #paint} and {@link #paintGhost} work in document coordinates (8 px per grid unit). */
public final class CircuitRenderer {

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final BasicStroke WIRE = new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke CONFLICT_WIRE = new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke SELECTED_WIRE = new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke CONFLICT_OUTLINE = new BasicStroke(1.5f, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER, 10f, new float[]{4f, 3f}, 0f);
    private static final float SELECTION_OUTLINE_WIDTH = 1.5f;
    private static final double JUNCTION_RADIUS = 3.5;
    private static final double HALO_RADIUS = 7;
    private static final double OUTLINE_GAP = 3;
    private static final double MINIMUM_DOT_SPACING = 6;

    private final ComponentTypeRegistry registry;
    private final PainterRegistry painters;
    private final ColorScheme scheme;

    public CircuitRenderer(ComponentTypeRegistry registry, PainterRegistry painters, ColorScheme scheme) {
        this.registry = registry;
        this.painters = painters;
        this.scheme = scheme;
    }

    public ColorScheme scheme() {
        return scheme;
    }

    /** Background and grid dots, in screen coordinates. */
    public void paintBackground(Graphics2D graphics, Viewport viewport, int width, int height) {
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
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        ShortCircuitHighlight highlight = ShortCircuitHighlight.of(state.snapshot(), state.netlist(),
                state.netlistToken(), state.document(), registry);

        for (Wire wire : state.document().wires()) {
            if (state.selection().contains(wire.id())) {
                Shape halo = SELECTED_WIRE.createStrokedShape(line(wire));
                graphics.setColor(withAlpha(scheme.selection(), 90));
                graphics.fill(halo);
                MarchingAnts.draw(graphics, halo, scheme.selection(), SELECTION_OUTLINE_WIDTH,
                        state.selectionDashPhase());
            }
        }
        for (Wire wire : state.document().wires()) {
            boolean shorted = highlight.wires().contains(wire.id());
            graphics.setStroke(shorted ? CONFLICT_WIRE : WIRE);
            graphics.setColor(shorted ? scheme.wireConflict()
                    : SignalColors.wireColor(state.netlist().netForWire(wire.id()), state.snapshot(),
                    state.netlistToken(), scheme));
            graphics.draw(line(wire));
        }
        graphics.setColor(scheme.componentStroke());
        for (GridPoint junction : Junctions.of(state.document())) {
            graphics.fill(circle(junction, JUNCTION_RADIUS));
        }

        for (Component component : state.document().components()) {
            ComponentType type = registry.require(component.typeId());
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

        for (GridPoint pin : highlight.driverPins()) {
            graphics.setColor(withAlpha(scheme.wireConflict(), 90));
            graphics.fill(circle(pin, HALO_RADIUS));
            graphics.setStroke(WIRE);
            graphics.setColor(scheme.wireConflict());
            graphics.draw(circle(pin, HALO_RADIUS));
        }
    }

    public void paintGhost(Graphics2D graphics, DocumentElement element) {
        Composite previous = graphics.getComposite();
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        try {
            switch (element) {
                case Component component -> ComponentDrawing.paint(graphics, registry.require(component.typeId()),
                        component, VisibleState.NONE, scheme, painters);
                case Wire wire -> {
                    graphics.setStroke(WIRE);
                    graphics.setColor(scheme.preview());
                    graphics.draw(line(wire));
                }
                case TextLabel label -> {
                    // Free text labels are drawn from M3 on.
                }
            }
        } finally {
            graphics.setComposite(previous);
        }
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

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
