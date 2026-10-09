package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;

import java.util.Optional;

public final class CircuitBounds {

    private CircuitBounds() {
    }

    public static Optional<Bounds> of(CircuitDocument document, ComponentTypeRegistry registry) {
        Bounds result = null;
        for (Component component : document.components()) {
            Bounds bounds = registry.require(component.typeId()).bounds(component);
            result = result == null ? bounds : result.union(bounds);
        }
        for (Wire wire : document.wires()) {
            Bounds bounds = Bounds.of(wire.start(), wire.end());
            result = result == null ? bounds : result.union(bounds);
        }
        return Optional.ofNullable(result);
    }
}
