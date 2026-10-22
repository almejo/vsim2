package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.present.StepPreview;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterSupport;
import cl.almejo.vsim.ui.tools.ToolContext;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The step «Paso a paso» waits on, before «Siguiente» (spec §5): what it adds or moves painted semi-transparent where
 * it will be, like a paste preview ({@link ToolContext#paintGhost}), and what it removes or moves away dimmed under a
 * veil of the background colour with a dashed outline. Nothing in the circuit changes. Painted by every pane (each
 * with its own tunnel colours); colours from the scheme at paint time; editor chrome, never on paper. EDT only.
 */
final class StepGhostOverlay {

    /** Opacity (0–255) of the veil that dims what the step takes away. */
    static final int DIM_ALPHA = 170;
    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final BasicStroke DIMMED_WIRE = new BasicStroke(6f, BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND);
    private static final BasicStroke OUTLINE = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10f, new float[]{3f, 3f}, 0f);

    private final Supplier<ColorScheme> scheme;
    private final ComponentTypeRegistry registry;
    private final Runnable repaint;
    private StepPreview shown;

    StepGhostOverlay(Supplier<ColorScheme> scheme, ComponentTypeRegistry registry, Runnable repaint) {
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.repaint = Objects.requireNonNull(repaint, "repaint");
    }

    void show(StepPreview preview) {
        shown = Objects.requireNonNull(preview, "preview");
        repaint.run();
    }

    void clear() {
        if (shown != null) {
            shown = null;
            repaint.run();
        }
    }

    Optional<StepPreview> shown() {
        return Optional.ofNullable(shown);
    }

    /**
     * Where the step happens in {@code document} (grid units): its own bounds when it says them, else what it adds or
     * moves (as drawn, rotation included) and what it removes that is still there.
     */
    static Optional<Bounds> changedBounds(StepPreview preview, CircuitDocument document,
                                          ComponentTypeRegistry registry) {
        if (preview.changedBounds().isPresent()) {
            return preview.changedBounds();
        }
        List<DocumentElement> elements = new ArrayList<>(drawable(preview.ghostElements(), registry));
        preview.removedIds().stream().flatMap(id -> document.element(id).stream()).forEach(elements::add);
        return DrawnBounds.of(elements, registry);
    }

    /** Components whose type is not known yet (a definition the step creates) have no drawing to show. */
    private static List<DocumentElement> drawable(List<DocumentElement> elements, ComponentTypeRegistry registry) {
        return elements.stream()
                .filter(element -> !(element instanceof Component component)
                        || registry.findTypeOf(component).isPresent())
                .toList();
    }

    /** Paints in {@code pane}; {@code graphics} is in document coordinates. */
    void paint(Graphics2D graphics, RenderState state, ToolContext pane) {
        StepPreview preview = shown;
        if (preview == null) {
            return;
        }
        Set<ElementId> leaving = new LinkedHashSet<>(preview.removedIds());
        preview.ghostElements().forEach(element -> leaving.add(element.id())); // a moved element leaves its place
        dim(graphics, state.document(), leaving);
        for (DocumentElement element : drawable(preview.ghostElements(), registry)) {
            pane.paintGhost(graphics, element, registry);
        }
    }

    private void dim(Graphics2D graphics, CircuitDocument document, Set<ElementId> leaving) {
        ColorScheme colors = scheme.get();
        Graphics2D veil = (Graphics2D) graphics.create();
        try {
            for (ElementId id : leaving) {
                Optional<DocumentElement> present = document.element(id);
                if (present.isEmpty()) {
                    continue;
                }
                if (present.get() instanceof Wire wire) {
                    Line2D line = new Line2D.Double(wire.start().x() * UNIT, wire.start().y() * UNIT,
                            wire.end().x() * UNIT, wire.end().y() * UNIT);
                    veil.setColor(PainterSupport.withAlpha(colors.background(), DIM_ALPHA));
                    veil.setStroke(DIMMED_WIRE);
                    veil.draw(line);
                    continue;
                }
                Bounds bounds = DrawnBounds.of(present.get(), registry);
                Rectangle2D area = new Rectangle2D.Double(bounds.x() * UNIT, bounds.y() * UNIT,
                        bounds.width() * UNIT, bounds.height() * UNIT);
                veil.setColor(PainterSupport.withAlpha(colors.background(), DIM_ALPHA));
                veil.fill(area);
                veil.setColor(colors.preview());
                veil.setStroke(OUTLINE);
                veil.draw(area);
            }
        } finally {
            veil.dispose();
        }
    }
}
