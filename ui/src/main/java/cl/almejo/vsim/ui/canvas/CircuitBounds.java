package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;

import java.awt.geom.Rectangle2D;
import java.util.Optional;

public final class CircuitBounds {

    private CircuitBounds() {
    }

    public static Optional<Bounds> of(CircuitDocument document, ComponentTypeRegistry registry) {
        Bounds result = null;
        for (Component component : document.components()) {
            Bounds bounds = registry.typeOf(component).bounds(component);
            result = result == null ? bounds : result.union(bounds);
        }
        for (Wire wire : document.wires()) {
            Bounds bounds = Bounds.of(wire.start(), wire.end());
            result = result == null ? bounds : result.union(bounds);
        }
        for (TextLabel label : document.labels()) {
            Rectangle2D area = LabelLayout.gridBounds(label);
            int minimumX = (int) Math.floor(area.getMinX());
            int minimumY = (int) Math.floor(area.getMinY());
            Bounds bounds = new Bounds(minimumX, minimumY, (int) Math.ceil(area.getMaxX()) - minimumX,
                    (int) Math.ceil(area.getMaxY()) - minimumY);
            result = result == null ? bounds : result.union(bounds);
        }
        return Optional.ofNullable(result);
    }
}
