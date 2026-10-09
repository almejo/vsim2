package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HitTesterTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final HitTester hitTester = new HitTester(registry);
    private final CircuitDocument document = new CircuitDocument();

    private Component place(String typeId, int x, int y, Rotation rotation) {
        Component component = registry.require(typeId)
                .newComponent(document.newComponentId(), new GridPoint(x, y), rotation, Map.of(), "");
        document.add(component);
        return component;
    }

    private Wire wire(int startX, int startY, int endX, int endY) {
        Wire wire = new Wire(document.newWireId(), new GridPoint(startX, startY), new GridPoint(endX, endY));
        document.add(wire);
        return wire;
    }

    private DocumentElement at(double x, double y) {
        return hitTester.elementAt(document, new Point2D.Double(x, y)).orElse(null);
    }

    @Test
    void findsAComponentInsideItsBounds() {
        Component gate = place(StandardComponentTypes.AND2, 10, 10, Rotation.R0);
        assertThat(at(12, 12)).isEqualTo(gate);
        assertThat(at(14, 14)).isEqualTo(gate);
        assertThat(at(14.5, 12)).isNull();
    }

    @Test
    void usesRotatedBounds() {
        Component led = place(StandardComponentTypes.LED, 10, 10, Rotation.R90);
        assertThat(at(8.5, 11)).isEqualTo(led);
        assertThat(at(11, 11)).isNull();
    }

    @Test
    void laterComponentsAreOnTop() {
        place(StandardComponentTypes.AND2, 10, 10, Rotation.R0);
        Component top = place(StandardComponentTypes.NOT, 12, 12, Rotation.R0);
        assertThat(at(13, 13)).isEqualTo(top);
    }

    @Test
    void findsTheClosestWireWithinTolerance() {
        Wire horizontal = wire(0, 0, 10, 0);
        Wire vertical = wire(5, 1, 5, 10);
        assertThat(at(3, 0.3)).isEqualTo(horizontal);
        assertThat(at(5.2, 0.9)).isEqualTo(vertical);
        assertThat(at(3, 0.6)).isNull();
    }

    @Test
    void componentsWinOverWires() {
        Component gate = place(StandardComponentTypes.AND2, 0, 0, Rotation.R0);
        wire(0, 2, 10, 2);
        assertThat(at(2, 2)).isEqualTo(gate);
    }
}
