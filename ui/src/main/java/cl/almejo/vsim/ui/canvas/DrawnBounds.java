package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.awt.geom.Rectangle2D;
import java.util.Collection;
import java.util.Optional;

/**
 * The grid area elements cover on screen: like {@link ElementBounds}, plus the text the canvas draws for them (a
 * component's label, a free label's whole text), which can stick out of the element's own bounds.
 */
public final class DrawnBounds {

    private static final double GRID_UNIT = GridPoint.PIXELS_PER_UNIT;

    private DrawnBounds() {
    }

    /** Empty when there are no elements. */
    public static Optional<Bounds> of(Collection<? extends DocumentElement> elements, ComponentTypeRegistry registry) {
        Bounds result = null;
        for (DocumentElement element : elements) {
            Bounds bounds = of(element, registry);
            result = result == null ? bounds : result.union(bounds);
        }
        return Optional.ofNullable(result);
    }

    public static Bounds of(DocumentElement element, ComponentTypeRegistry registry) {
        return switch (element) {
            case Component component -> {
                Bounds body = registry.typeOf(component).bounds(component);
                yield LabelLayout.componentLabel(registry.typeOf(component), component)
                        .map(label -> body.union(toGrid(label.bounds(), GRID_UNIT)))
                        .orElse(body);
            }
            case TextLabel label -> toGrid(LabelLayout.gridBounds(label), 1);
            default -> ElementBounds.of(element, registry);
        };
    }

    /** The smallest grid rectangle holding {@code area}, given in units of {@code unit} pixels per grid unit. */
    private static Bounds toGrid(Rectangle2D area, double unit) {
        int minimumX = (int) Math.floor(area.getMinX() / unit);
        int minimumY = (int) Math.floor(area.getMinY() / unit);
        return new Bounds(minimumX, minimumY, (int) Math.ceil(area.getMaxX() / unit) - minimumX,
                (int) Math.ceil(area.getMaxY() / unit) - minimumY);
    }
}
