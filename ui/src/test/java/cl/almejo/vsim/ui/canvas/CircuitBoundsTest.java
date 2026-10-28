package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.LabelOffset;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

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
        document.add(registry.require(StandardComponentTypes.BUFFER)
                .newComponent(document.newComponentId(), new GridPoint(2, 3), Rotation.R0, Map.of(), ""));
        document.add(new Wire(document.newWireId(), new GridPoint(-5, 20), new GridPoint(10, 20)));
        assertThat(CircuitBounds.of(document, registry)).contains(new Bounds(-5, 3, 15, 17));
    }

    @Test
    void coversTheValueBoxOfABusPinButNotOfAOneBitPin() {
        CircuitDocument document = new CircuitDocument();
        document.add(registry.require(StandardComponentTypes.INPUT_PIN)
                .newComponent(document.newComponentId(), new GridPoint(10, 10), Rotation.R0, Map.of(), ""));
        assertThat(CircuitBounds.of(document, registry)).contains(new Bounds(10, 10, 2, 2));

        CircuitDocument buses = new CircuitDocument();
        buses.add(registry.require(StandardComponentTypes.INPUT_PIN).newComponent(buses.newComponentId(),
                new GridPoint(10, 10), Rotation.R0, Map.of("width", 8), ""));
        Bounds bounds = CircuitBounds.of(buses, registry).orElseThrow();
        assertThat(bounds.x()).isLessThan(10); // the box is left of the tag
        assertThat(bounds.maximumX()).isEqualTo(12);
    }

    @Test
    void coversFreeTexts() {
        CircuitDocument document = new CircuitDocument();
        document.add(new TextLabel(document.newLabelId(), new GridPoint(5, 5), "hola"));
        Bounds bounds = CircuitBounds.of(document, registry).orElseThrow();
        assertThat(bounds.x()).isEqualTo(5);
        assertThat(bounds.y()).isEqualTo(5);
        assertThat(bounds.width()).isPositive();
        assertThat(bounds.height()).isPositive();
    }

    @Test
    void coversAMovedComponentLabelAtItsNewPlace() {
        for (Rotation rotation : Rotation.values()) {
            CircuitDocument document = new CircuitDocument();
            Component gate = registry.require(StandardComponentTypes.BUFFER).newComponent(document.newComponentId(),
                    new GridPoint(0, 0), rotation, Map.of(), "U1").withLabelOffset(Optional.of(new LabelOffset(20, 0)));
            document.add(gate);
            GridPoint centre = rotation.apply(new GridPoint(20, 0));
            Bounds bounds = CircuitBounds.of(document, registry).orElseThrow();
            assertThat(bounds.x()).isLessThanOrEqualTo(centre.x() - 1);
            assertThat(bounds.maximumX()).isGreaterThanOrEqualTo(centre.x() + 1);
            assertThat(bounds.y()).isLessThanOrEqualTo(centre.y());
            assertThat(bounds.maximumY()).isGreaterThanOrEqualTo(centre.y());
        }
    }
}
