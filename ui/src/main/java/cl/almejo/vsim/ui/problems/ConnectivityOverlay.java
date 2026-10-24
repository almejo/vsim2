package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterSupport;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The red marks of structural faults (never colour only: a glow and a warning triangle): a component broken inside, at
 * any depth, glows as a whole; an outer terminal whose interface element is unconnected inside, and a pin loosened by
 * a shape change, glow on the pin. Colours are read from the scheme at every paint.
 */
final class ConnectivityOverlay implements CanvasOverlay {

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final double MARK_SIZE = 9;
    private static final double PIN_RADIUS = 4;
    private static final double PIN_GLOW_RADIUS = 6;
    private static final double PIECE_MARGIN = 3;
    private static final int GLOW_FILL_ALPHA = 55;
    private static final int GLOW_OUTLINE_ALPHA = 210;
    private static final BasicStroke STROKE = new BasicStroke(1.5f);
    private static final BasicStroke PIECE_OUTLINE = new BasicStroke(2.5f);

    private final OpenDocument openDocument;
    private final Supplier<ColorScheme> scheme;

    ConnectivityOverlay(OpenDocument openDocument, Supplier<ColorScheme> scheme) {
        this.openDocument = openDocument;
        this.scheme = scheme;
    }

    @Override
    public void paint(Graphics2D graphics, RenderState state) {
        Set<ElementId> glowing = new HashSet<>();
        ColorScheme colors = scheme.get();
        graphics.setStroke(STROKE);
        for (ConnectivityProblem problem : openDocument.connectivityProblems()) {
            if (InstancePath.isNested(problem.elementId())) {
                ElementId visibleId = new ElementId(InstancePath.topLevel(problem.elementId()));
                state.document().component(visibleId).filter(component -> glowing.add(visibleId))
                        .ifPresent(component -> glowPiece(graphics, component, colors));
            } else {
                markPin(graphics, problem.position(), colors);
            }
        }
        for (PinRef pin : openDocument.loosenedPins()) {
            state.document().component(pin.componentId()).ifPresent(component -> {
                ComponentType type = openDocument.registry().typeOf(component);
                if (pin.pinIndex() < type.pinCount()) {
                    markPin(graphics, type.pinLocation(component, pin.pinIndex()), colors);
                }
            });
        }
    }

    private void glowPiece(Graphics2D graphics, Component component, ColorScheme colors) {
        Bounds bounds = openDocument.registry().typeOf(component).bounds(component);
        RoundRectangle2D outline = new RoundRectangle2D.Double(bounds.x() * UNIT - PIECE_MARGIN,
                bounds.y() * UNIT - PIECE_MARGIN, bounds.width() * UNIT + 2 * PIECE_MARGIN,
                bounds.height() * UNIT + 2 * PIECE_MARGIN, UNIT, UNIT);
        graphics.setColor(PainterSupport.withAlpha(colors.wireConflict(), GLOW_FILL_ALPHA));
        graphics.fill(outline);
        graphics.setColor(PainterSupport.withAlpha(colors.wireConflict(), GLOW_OUTLINE_ALPHA));
        graphics.setStroke(PIECE_OUTLINE);
        graphics.draw(outline);
        graphics.setStroke(STROKE);
        warning(graphics, bounds.maximumX() * UNIT, bounds.y() * UNIT, colors);
    }

    private void markPin(Graphics2D graphics, GridPoint position, ColorScheme colors) {
        double x = position.x() * UNIT;
        double y = position.y() * UNIT;
        graphics.setColor(PainterSupport.withAlpha(colors.wireConflict(), GLOW_FILL_ALPHA));
        graphics.fill(new Ellipse2D.Double(x - PIN_GLOW_RADIUS, y - PIN_GLOW_RADIUS, PIN_GLOW_RADIUS * 2,
                PIN_GLOW_RADIUS * 2));
        graphics.setColor(colors.wireConflict());
        graphics.draw(new Ellipse2D.Double(x - PIN_RADIUS, y - PIN_RADIUS, PIN_RADIUS * 2, PIN_RADIUS * 2));
        warning(graphics, x + PIN_RADIUS, y - MARK_SIZE, colors);
    }

    private void warning(Graphics2D graphics, double left, double top, ColorScheme colors) {
        Path2D triangle = new Path2D.Double();
        triangle.moveTo(left + MARK_SIZE / 2, top);
        triangle.lineTo(left + MARK_SIZE, top + MARK_SIZE);
        triangle.lineTo(left, top + MARK_SIZE);
        triangle.closePath();
        graphics.setColor(colors.componentFill());
        graphics.fill(triangle);
        graphics.setColor(colors.wireConflict());
        graphics.draw(triangle);
        double center = left + MARK_SIZE / 2;
        graphics.draw(new Line2D.Double(center, top + 3, center, top + 5));
        graphics.fill(new Ellipse2D.Double(center - 0.7, top + 6.5, 1.4, 1.4));
    }
}
