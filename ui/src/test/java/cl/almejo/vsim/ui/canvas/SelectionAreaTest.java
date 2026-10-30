package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.paint.ComponentDrawing;
import cl.almejo.vsim.ui.paint.InterfacePinPainter;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SelectionAreaTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    private Component pin(String typeId, Rotation rotation, Map<String, Object> parameters) {
        return registry.require(typeId).newComponent(new ElementId("c1"), new GridPoint(10, 10), rotation,
                parameters, "");
    }

    @Test
    void aBusPinsSelectionAreaEnclosesBodyAndValueBoxInEveryRotation() {
        for (String typeId : List.of(StandardComponentTypes.INPUT_PIN, StandardComponentTypes.OUTPUT_PIN)) {
            for (Rotation rotation : Rotation.values()) {
                Component component = pin(typeId, rotation, Map.of("width", 8));
                ComponentType type = registry.typeOf(component);
                Rectangle2D body = ComponentDrawing.documentBounds(type, component);
                Rectangle2D box = InterfacePinPainter.valueBox(type, component).orElseThrow();
                Rectangle2D area = SelectionArea.of(component, registry);
                assertThat(area.contains(body)).as(typeId + " " + rotation).isTrue();
                assertThat(area.contains(box)).as(typeId + " " + rotation).isTrue();
            }
        }
    }

    @Test
    void aOneBitPinsSelectionAreaIsItsBody() {
        Component component = pin(StandardComponentTypes.INPUT_PIN, Rotation.R0, Map.of());
        assertThat(SelectionArea.of(component, registry))
                .isEqualTo(ComponentDrawing.documentBounds(registry.typeOf(component), component));
    }
}
