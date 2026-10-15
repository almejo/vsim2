package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.geometry.Bounds;

import java.util.Optional;

public final class CircuitBounds {

    private CircuitBounds() {
    }

    /** The area the whole circuit covers as drawn, component labels and free text included. */
    public static Optional<Bounds> of(CircuitDocument document, ComponentTypeRegistry registry) {
        return DrawnBounds.of(document.elements(), registry);
    }
}
