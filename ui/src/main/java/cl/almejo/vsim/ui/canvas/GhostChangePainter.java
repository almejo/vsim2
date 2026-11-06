package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterSupport;
import cl.almejo.vsim.ui.tools.ToolContext;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Paints a change that has not happened yet: what it adds or moves semi-transparent where it will be, like a paste
 * preview ({@link ToolContext#paintGhost}), and what it removes or moves away dimmed under a veil of the background
 * colour with a dashed outline. Shared by the «Presentar» step ghost and the «Ordenar circuito» preview. Colours from
 * the scheme at paint time; editor chrome, callers never paint it on paper. EDT only.
 */
public final class GhostChangePainter {

    /** Opacity (0–255) of the veil that dims what the change takes away. */
    public static final int DIM_ALPHA = 170;
    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final BasicStroke DIMMED_WIRE = new BasicStroke(6f, BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND);
    private static final BasicStroke OUTLINE = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10f, new float[]{3f, 3f}, 0f);

    private final Supplier<ColorScheme> scheme;
    private final ComponentTypeRegistry registry;

    public GhostChangePainter(Supplier<ColorScheme> scheme, ComponentTypeRegistry registry) {
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /** Components whose type is not known yet (a definition the change creates) have no drawing to show. */
    public static List<DocumentElement> drawable(List<DocumentElement> elements, ComponentTypeRegistry registry) {
        return elements.stream()
                .filter(element -> !(element instanceof Component component)
                        || registry.findTypeOf(component).isPresent())
                .toList();
    }

    /**
     * Dims {@code leaving} (the elements as they are in the circuit now), then paints {@code arriving} as ghosts in
     * {@code pane}; {@code graphics} is in document coordinates.
     */
    public void paint(Graphics2D graphics, List<DocumentElement> leaving, List<DocumentElement> arriving,
                      ToolContext pane) {
        dim(graphics, leaving);
        for (DocumentElement element : drawable(arriving, registry)) {
            pane.paintGhost(graphics, element, registry);
        }
    }

    private void dim(Graphics2D graphics, List<DocumentElement> leaving) {
        ColorScheme colors = scheme.get();
        Graphics2D veil = (Graphics2D) graphics.create();
        try {
            for (DocumentElement element : leaving) {
                if (element instanceof Wire wire) {
                    Line2D line = new Line2D.Double(wire.start().x() * UNIT, wire.start().y() * UNIT,
                            wire.end().x() * UNIT, wire.end().y() * UNIT);
                    veil.setColor(PainterSupport.withAlpha(colors.background(), DIM_ALPHA));
                    veil.setStroke(DIMMED_WIRE);
                    veil.draw(line);
                    continue;
                }
                Bounds bounds = DrawnBounds.of(element, registry);
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
