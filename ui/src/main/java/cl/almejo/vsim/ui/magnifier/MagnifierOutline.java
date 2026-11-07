package cl.almejo.vsim.ui.magnifier;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.paint.ColorScheme;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * «Mirar adentro»: the only mark on the canvas is an outline, in the scheme's selection colour (read at every paint),
 * around the custom piece whose panel is showing. Nothing is drawn on the other pieces.
 */
final class MagnifierOutline implements CanvasOverlay {

    /** Sizes in screen pixels, divided by the zoom where they are drawn in document pixels. */
    private static final double LINE_WIDTH = 2.0;
    private static final double MARGIN = 2.0;
    private static final double CORNER = 4.0;

    private final Supplier<Optional<ElementId>> shown;
    private final Supplier<ComponentTypeRegistry> registry;
    private final Supplier<ColorScheme> scheme;

    MagnifierOutline(Supplier<Optional<ElementId>> shown, Supplier<ComponentTypeRegistry> registry,
                     Supplier<ColorScheme> scheme) {
        this.shown = shown;
        this.registry = registry;
        this.scheme = scheme;
    }

    @Override
    public void paint(Graphics2D graphics, RenderState state) {
        Optional<ElementId> id = shown.get();
        if (id.isEmpty()) {
            return;
        }
        CircuitDocument document = state.document();
        Optional<DocumentElement> element = document.elements().stream()
                .filter(candidate -> candidate.id().equals(id.get())).findFirst();
        if (element.isEmpty()) {
            return;
        }
        Graphics2D local = (Graphics2D) graphics.create();
        try {
            local.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            local.setColor(scheme.get().selection());
            double zoom = zoomOf(local);
            local.setStroke(new BasicStroke((float) (LINE_WIDTH / zoom), BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            Bounds box = ElementBounds.of(element.get(), registry.get());
            double margin = MARGIN / zoom;
            double unit = GridPoint.PIXELS_PER_UNIT;
            local.draw(new RoundRectangle2D.Double(box.x() * unit - margin, box.y() * unit - margin,
                    box.width() * unit + 2 * margin, box.height() * unit + 2 * margin, CORNER / zoom, CORNER / zoom));
        } finally {
            local.dispose();
        }
    }

    /** The zoom the overlay is drawn under: the scale of the canvas transform (document pixels to screen). */
    private static double zoomOf(Graphics2D graphics) {
        var transform = graphics.getTransform();
        return Math.hypot(transform.getScaleX(), transform.getShearY());
    }
}
