package cl.almejo.vsim.ui.stepping;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ComponentDrawing;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.function.Supplier;

/**
 * Paints a thin dashed outline in the scheme's selection color around each component of the current
 * {@link PendingOutline} (M6.1-B): what changes next, next to the glow of what just changed. No timer: it changes
 * only when {@link #show} gets another outline. EDT only.
 */
public final class PendingOutlineOverlay implements CanvasOverlay {

    /** Pixels between a component's bounds and its outline: outside the selection's marching ants (3 px). */
    static final double GAP = 4;
    private static final BasicStroke STROKE = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10f, new float[]{4f, 3f}, 0f);

    private final Supplier<ColorScheme> scheme;
    private final ComponentTypeRegistry registry;
    private final Runnable repaint;
    private PendingOutline outline = PendingOutline.NONE;
    private boolean disposed;

    /** {@code scheme} is read on every paint; {@code repaint} runs when the outline changes. */
    public PendingOutlineOverlay(Supplier<ColorScheme> scheme, ComponentTypeRegistry registry, Runnable repaint) {
        this.scheme = scheme;
        this.registry = registry;
        this.repaint = repaint;
    }

    /** Shows {@code next} instead of the current outline (a repaint only when it differs). Ignored after dispose. */
    public void show(PendingOutline next) {
        if (disposed || next.equals(outline)) {
            return;
        }
        outline = next;
        repaint.run();
    }

    public void clear() {
        show(PendingOutline.NONE);
    }

    public PendingOutline current() {
        return outline;
    }

    public void dispose() {
        disposed = true;
        outline = PendingOutline.NONE;
    }

    @Override
    public void paint(Graphics2D graphics, RenderState state) {
        PendingOutline shown = outline;
        if (shown.components().isEmpty() || !shown.matches(state.netlistToken())) {
            return;
        }
        graphics.setStroke(STROKE);
        graphics.setColor(scheme.get().selection());
        for (Component component : state.document().components()) {
            if (shown.components().contains(component.id())) {
                Rectangle2D bounds = ComponentDrawing.documentBounds(registry.typeOf(component), component);
                graphics.draw(new Rectangle2D.Double(bounds.getX() - GAP, bounds.getY() - GAP,
                        bounds.getWidth() + 2 * GAP, bounds.getHeight() + 2 * GAP));
            }
        }
    }
}
