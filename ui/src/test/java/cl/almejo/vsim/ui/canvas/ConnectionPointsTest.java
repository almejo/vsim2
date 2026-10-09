package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionPointsTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final ConnectionPoints points = new ConnectionPoints(registry);
    private final CircuitDocument document = new CircuitDocument();

    private void place(String typeId, int x, int y) {
        document.add(registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y),
                Rotation.R0, Map.of(), ""));
    }

    @Test
    void findsAPinWithinTheRadius() {
        place(StandardComponentTypes.NOT, 0, 0);
        assertThat(points.pinNear(document, new Point2D.Double(4.3, 2.2))).map(ConnectionPoints.PinHit::point).contains(new GridPoint(4, 2));
        assertThat(points.pinNear(document, new Point2D.Double(0.4, 2.4))).map(ConnectionPoints.PinHit::point).contains(new GridPoint(0, 2));
        assertThat(points.pinNear(document, new Point2D.Double(2, 2))).isEmpty();
    }

    @Test
    void pinNearChoosesTheClosestPin() {
        place(StandardComponentTypes.NOT, 0, 0);
        place(StandardComponentTypes.NOT, 4, 0);
        assertThat(points.pinNear(document, new Point2D.Double(4.1, 2))).map(ConnectionPoints.PinHit::point).contains(new GridPoint(4, 2));
        place(StandardComponentTypes.NOT, 0, 1);
        assertThat(points.pinNear(document, new Point2D.Double(0.1, 2.6))).map(ConnectionPoints.PinHit::point).contains(new GridPoint(0, 3));
    }

    @Test
    void pointOnWireSnapsAlongTheWireAndClampsToItsEnds() {
        Wire horizontal = new Wire(new ElementId("w1"), new GridPoint(2, 5), new GridPoint(10, 5));
        assertThat(ConnectionPoints.pointOnWire(horizontal, new Point2D.Double(6.4, 5.3)))
                .isEqualTo(new GridPoint(6, 5));
        assertThat(ConnectionPoints.pointOnWire(horizontal, new Point2D.Double(12, 5))).isEqualTo(new GridPoint(10, 5));
        Wire vertical = new Wire(new ElementId("w2"), new GridPoint(3, 1), new GridPoint(3, 7));
        assertThat(ConnectionPoints.pointOnWire(vertical, new Point2D.Double(3.2, 4.6))).isEqualTo(new GridPoint(3, 5));
    }
}
