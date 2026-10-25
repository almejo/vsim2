package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ConnectionPoints;
import cl.almejo.vsim.ui.canvas.HitTester;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ComponentDrawing;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A dot on the top-right corner of every piece with its own shape (spec §6.2, mockup 13), shown while the pointer is
 * over the piece or the piece is selected, and «Forma propia» in its help (a pin under the pointer keeps its own help).
 * The scheme is read when painting, never kept.
 */
public final class OwnShapeMark implements CanvasOverlay {
    private static final double RADIUS = 3;

    private final OpenDocument openDocument;
    private final Supplier<ColorScheme> scheme;
    private final Runnable repaint;
    private final HitTester hitTester;
    private final ConnectionPoints connectionPoints;
    private volatile Optional<ElementId> hovered = Optional.empty();

    public OwnShapeMark(OpenDocument openDocument, Supplier<ColorScheme> scheme, Runnable repaint) {
        this.openDocument = openDocument;
        this.scheme = scheme;
        this.repaint = repaint;
        this.hitTester = new HitTester(openDocument.registry());
        this.connectionPoints = new ConnectionPoints(openDocument.registry());
    }

    /** Follows the pointer of {@code pane}; the returned runnable stops following it. */
    public Runnable follow(CircuitCanvas pane) {
        MouseAdapter pointer = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent event) {
                hoverAt(pane.viewport().screenToGrid(event.getX(), event.getY()));
            }

            @Override public void mouseExited(MouseEvent event) {
                setHovered(Optional.empty());
            }
        };
        pane.addMouseMotionListener(pointer);
        pane.addMouseListener(pointer);
        return () -> {
            pane.removeMouseMotionListener(pointer);
            pane.removeMouseListener(pointer);
        };
    }

    /** The pointer is at {@code gridPoint} (grid units): that piece, if it has its own shape, shows the dot. */
    public void hoverAt(Point2D gridPoint) {
        setHovered(pieceWithOwnShapeAt(gridPoint).map(Component::id));
    }

    /** «Forma propia» over a piece with its own shape, unless the pointer is on one of its pins. */
    public Optional<String> tooltipAt(Point2D gridPoint) {
        if (connectionPoints.pinNear(openDocument.document(), gridPoint).isPresent()) {
            return Optional.empty();
        }
        return pieceWithOwnShapeAt(gridPoint).map(piece -> Messages.text("shape.own"));
    }

    @Override
    public void paint(Graphics2D graphics, RenderState state) {
        ColorScheme colors = scheme.get();
        Graphics2D dot = (Graphics2D) graphics.create();
        try {
            dot.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            dot.setStroke(new BasicStroke(1f));
            for (Component piece : state.document().components()) {
                boolean shown = piece.appearance().isPresent()
                        && (state.selection().contains(piece.id()) || hovered.filter(piece.id()::equals).isPresent());
                Optional<ComponentType> type = shown ? openDocument.registry().findTypeOf(piece) : Optional.empty();
                if (type.isPresent()) {
                    paintDot(dot, ComponentDrawing.documentBounds(type.get(), piece), colors);
                }
            }
        } finally {
            dot.dispose();
        }
    }

    private static void paintDot(Graphics2D graphics, Rectangle2D bounds, ColorScheme colors) {
        Ellipse2D dot = new Ellipse2D.Double(bounds.getMaxX() - 2 * RADIUS - 1, bounds.getMinY() + 1,
                2 * RADIUS, 2 * RADIUS);
        graphics.setColor(colors.selection());
        graphics.fill(dot);
        graphics.setColor(colors.background());
        graphics.draw(dot);
    }

    private Optional<Component> pieceWithOwnShapeAt(Point2D gridPoint) {
        CircuitDocument document = openDocument.document();
        Optional<DocumentElement> element = hitTester.elementAt(document, gridPoint);
        return element.filter(Component.class::isInstance).map(Component.class::cast)
                .filter(piece -> piece.appearance().isPresent());
    }

    private void setHovered(Optional<ElementId> next) {
        if (!next.equals(hovered)) {
            hovered = next;
            repaint.run();
        }
    }
}
