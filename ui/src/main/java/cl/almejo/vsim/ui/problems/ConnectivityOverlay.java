package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.paint.ColorScheme;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Warning triangles identify disconnected components, including a visible parent of an internal problem. */
final class ConnectivityOverlay implements CanvasOverlay {

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final double MARK_SIZE = 9;
    private static final double PIN_RADIUS = 4;
    private static final BasicStroke STROKE = new BasicStroke(1.5f);

    private final OpenDocument openDocument;
    private final Supplier<ColorScheme> scheme;

    ConnectivityOverlay(OpenDocument openDocument, Supplier<ColorScheme> scheme) {
        this.openDocument = openDocument;
        this.scheme = scheme;
    }

    @Override
    public void paint(Graphics2D graphics, RenderState state) {
        Set<ElementId> marked = new HashSet<>();
        ColorScheme colors = scheme.get();
        graphics.setStroke(STROKE);
        for (ConnectivityProblem problem : openDocument.connectivityProblems()) {
            ElementId visibleId = new ElementId(InstancePath.topLevel(problem.elementId()));
            Optional<Component> component = state.document().component(visibleId);
            if (component.isPresent() && marked.add(visibleId)) {
                Bounds bounds = openDocument.registry().typeOf(component.get()).bounds(component.get());
                warning(graphics, bounds.maximumX() * UNIT, bounds.y() * UNIT, colors);
            }
            if (!InstancePath.isNested(problem.elementId())) {
                double x = problem.position().x() * UNIT;
                double y = problem.position().y() * UNIT;
                graphics.setColor(colors.wireConflict());
                graphics.draw(new Ellipse2D.Double(x - PIN_RADIUS, y - PIN_RADIUS, PIN_RADIUS * 2, PIN_RADIUS * 2));
                if (!problem.isPin()) {
                    warning(graphics, x + PIN_RADIUS, y - MARK_SIZE, colors);
                }
            }
        }
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
