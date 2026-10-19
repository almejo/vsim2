package cl.almejo.vsim.ui.search;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.paint.ColorScheme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The soft highlight of the find bar's matches: a translucent patch of the scheme's selection colour over each
 * matching element (an outline for frames, which would otherwise cover what they hold), a stronger one with an outline
 * for the current match. It keeps ids only and looks each one up in the frame's document, so an element deleted
 * meanwhile is simply not painted. EDT only.
 */
public final class FindHighlightOverlay implements CanvasOverlay {

    /** Document pixels between an element's drawn bounds and its patch. */
    private static final double PADDING = 3;
    private static final double ARC = 8;
    private static final int MATCH_ALPHA = 56;
    private static final int CURRENT_ALPHA = 104;
    private static final BasicStroke OUTLINE = new BasicStroke(1.5f);
    private static final BasicStroke FRAME_OUTLINE = new BasicStroke(4f);

    private final Supplier<ColorScheme> scheme;
    private final ComponentTypeRegistry registry;
    private final Runnable repaint;
    private Set<ElementId> highlighted = Set.of();
    private Optional<ElementId> current = Optional.empty();

    /** {@code scheme} is read on every paint; {@code repaint} runs when what is highlighted changes. */
    public FindHighlightOverlay(Supplier<ColorScheme> scheme, ComponentTypeRegistry registry, Runnable repaint) {
        this.scheme = scheme;
        this.registry = registry;
        this.repaint = repaint;
    }

    /** Highlights {@code matches}, {@code currentMatch} more strongly (a repaint only when something changed). */
    public void show(Set<ElementId> matches, Optional<ElementId> currentMatch) {
        Set<ElementId> next = Set.copyOf(matches);
        if (next.equals(highlighted) && currentMatch.equals(current)) {
            return;
        }
        highlighted = next;
        current = currentMatch;
        repaint.run();
    }

    public void clear() {
        show(Set.of(), Optional.empty());
    }

    public Set<ElementId> highlighted() {
        return highlighted;
    }

    public Optional<ElementId> current() {
        return current;
    }

    @Override
    public void paint(Graphics2D graphics, RenderState state) {
        Set<ElementId> shown = highlighted;
        if (shown.isEmpty()) {
            return;
        }
        Color selection = scheme.get().selection();
        CircuitDocument document = state.document();
        for (ElementId id : shown) {
            document.element(id).ifPresent(element ->
                    paint(graphics, element, selection, current.filter(id::equals).isPresent()));
        }
    }

    private void paint(Graphics2D graphics, DocumentElement element, Color selection, boolean isCurrent) {
        Bounds bounds = DrawnBounds.of(element, registry);
        double unit = GridPoint.PIXELS_PER_UNIT;
        RoundRectangle2D patch = new RoundRectangle2D.Double(bounds.x() * unit - PADDING, bounds.y() * unit - PADDING,
                bounds.width() * unit + 2 * PADDING, bounds.height() * unit + 2 * PADDING, ARC, ARC);
        Color translucent = new Color(selection.getRed(), selection.getGreen(), selection.getBlue(),
                isCurrent ? CURRENT_ALPHA : MATCH_ALPHA);
        graphics.setColor(translucent);
        if (element instanceof Frame) {
            graphics.setStroke(FRAME_OUTLINE);
            graphics.draw(patch);
        } else {
            graphics.fill(patch);
        }
        if (isCurrent) {
            graphics.setColor(selection);
            graphics.setStroke(OUTLINE);
            graphics.draw(patch);
        }
    }
}
