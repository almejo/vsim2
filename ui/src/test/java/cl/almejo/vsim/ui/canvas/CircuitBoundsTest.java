package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBoundsTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    @Test
    void emptyDocumentHasNoBounds() {
        assertThat(CircuitBounds.of(new CircuitDocument(), registry)).isEmpty();
    }

    @Test
    void coversComponentsAndWires() {
        CircuitDocument document = new CircuitDocument();
        document.add(registry.require(StandardComponentTypes.NOT)
                .newComponent(document.newComponentId(), new GridPoint(2, 3), Rotation.R0, Map.of(), ""));
        document.add(new Wire(document.newWireId(), new GridPoint(-5, 20), new GridPoint(10, 20)));
        assertThat(CircuitBounds.of(document, registry)).contains(new Bounds(-5, 3, 15, 17));
    }
}
