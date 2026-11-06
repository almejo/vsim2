package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.layout.TextBoxes;
import cl.almejo.vsim.ui.canvas.LabelLayout;

import java.awt.geom.Rectangle2D;
import java.util.Optional;

/**
 * The size of texts as the canvas draws them ({@link LabelLayout}, with the current label font), in grid units
 * rounded outward, for «Ordenar circuito». Read-only: it only measures, so the planning thread may use it.
 */
public final class CanvasTextBoxes implements TextBoxes {

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;

    private final ComponentTypeRegistry registry;

    /** {@code registry} resolves a component's type (its label sits relative to the body); null only in tests of free texts. */
    public CanvasTextBoxes(ComponentTypeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Optional<Bounds> componentLabel(Component component) {
        return registry.findTypeOf(component)
                .flatMap(type -> LabelLayout.componentLabel(type, component))
                .map(placed -> outward(placed.bounds()));
    }

    @Override
    public Bounds freeText(TextLabel label) {
        return outward(LabelLayout.noteBox(label));
    }

    /** The grid units a box in document pixels touches: the start rounded down, the end rounded up. */
    static Bounds outward(Rectangle2D pixels) {
        int left = (int) Math.floor(pixels.getMinX() / UNIT);
        int top = (int) Math.floor(pixels.getMinY() / UNIT);
        int right = (int) Math.ceil(pixels.getMaxX() / UNIT);
        int bottom = (int) Math.ceil(pixels.getMaxY() / UNIT);
        return new Bounds(left, top, right - left, bottom - top);
    }
}
