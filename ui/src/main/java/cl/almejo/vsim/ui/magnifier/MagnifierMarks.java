package cl.almejo.vsim.ui.magnifier;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.paint.ColorScheme;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The small magnifier drawn on the bottom-right corner of every user-component instance while the Lupa is on (spec
 * §8.2), at a constant size on screen. The colour is read from the scheme at every paint.
 */
final class MagnifierMarks implements CanvasOverlay {

    /** Sizes in screen pixels, divided by the zoom where they are drawn in document pixels. */
    private static final double LENS_DIAMETER = 7;
    private static final double HANDLE_LENGTH = 4;
    private static final double MARGIN = 2;
    private static final double LINE_WIDTH = 1.6;

    private final BooleanSupplier toggledOn;
    private final Supplier<ComponentTypeRegistry> registry;
    private final Supplier<ColorScheme> scheme;

    MagnifierMarks(BooleanSupplier toggledOn, Supplier<ComponentTypeRegistry> registry, Supplier<ColorScheme> scheme) {
        this.toggledOn = toggledOn;
        this.registry = registry;
        this.scheme = scheme;
    }

    @Override
    public void paint(Graphics2D graphics, RenderState state) {
        if (!toggledOn.getAsBoolean()) {
            return;
        }
        CircuitDocument document = state.document();
        Graphics2D local = (Graphics2D) graphics.create();
        try {
            local.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            local.setColor(scheme.get().selection());
            double zoom = zoomOf(local);
            local.setStroke(new BasicStroke((float) (LINE_WIDTH / zoom), BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            for (DocumentElement element : document.elements()) {
                if (element instanceof Component component && isInstance(document, component)) {
                    paintMark(local, component, zoom);
                }
            }
        } finally {
            local.dispose();
        }
    }

    private static boolean isInstance(CircuitDocument document, Component component) {
        return SubcircuitTypes.definitionIdOf(component.typeId())
                .filter(definitionId -> document.definition(definitionId).isPresent()).isPresent();
    }

    private void paintMark(Graphics2D graphics, Component component, double zoom) {
        Bounds box = ElementBounds.of(component, registry.get());
        double right = (box.x() + box.width()) * (double) GridPoint.PIXELS_PER_UNIT - MARGIN / zoom;
        double bottom = (box.y() + box.height()) * (double) GridPoint.PIXELS_PER_UNIT - MARGIN / zoom;
        double diameter = LENS_DIAMETER / zoom;
        double handle = HANDLE_LENGTH / zoom;
        double lensX = right - diameter - handle * Math.sqrt(0.5);
        double lensY = bottom - diameter - handle * Math.sqrt(0.5);
        graphics.draw(new Ellipse2D.Double(lensX, lensY, diameter, diameter));
        double centerX = lensX + diameter / 2;
        double centerY = lensY + diameter / 2;
        double radius = diameter / 2;
        double start = radius * Math.sqrt(0.5);
        graphics.draw(new Line2D.Double(centerX + start, centerY + start, centerX + start + handle * Math.sqrt(0.5),
                centerY + start + handle * Math.sqrt(0.5)));
    }

    /** The zoom the overlay is drawn under: the scale of the canvas transform (document pixels to screen). */
    private static double zoomOf(Graphics2D graphics) {
        var transform = graphics.getTransform();
        return Math.hypot(transform.getScaleX(), transform.getShearY());
    }
}
