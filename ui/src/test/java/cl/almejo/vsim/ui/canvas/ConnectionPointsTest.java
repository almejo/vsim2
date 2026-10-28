package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.tunnel.Tunnels;
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
        place(StandardComponentTypes.BUFFER, 0, 0);
        assertThat(points.pinNear(document, new Point2D.Double(4.3, 2.2))).map(ConnectionPoints.PinHit::point).contains(new GridPoint(4, 2));
        assertThat(points.pinNear(document, new Point2D.Double(0.4, 2.4))).map(ConnectionPoints.PinHit::point).contains(new GridPoint(0, 2));
        assertThat(points.pinNear(document, new Point2D.Double(2, 2))).isEmpty();
    }

    @Test
    void pinNearChoosesTheClosestPin() {
        place(StandardComponentTypes.BUFFER, 0, 0);
        place(StandardComponentTypes.BUFFER, 4, 0);
        assertThat(points.pinNear(document, new Point2D.Double(4.1, 2))).map(ConnectionPoints.PinHit::point).contains(new GridPoint(4, 2));
        place(StandardComponentTypes.BUFFER, 0, 1);
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

    private void place(String typeId, int x, int y, Map<String, Object> parameters) {
        document.add(registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y),
                Rotation.R0, parameters, ""));
    }

    @Test
    void widthAtIsTheWidthOfTheInterfacePinOrSplitterBusPinThere() {
        place(StandardComponentTypes.INPUT_PIN, 0, 0, Map.of(InterfacePins.WIDTH, 8));
        place(StandardComponentTypes.BUS_SPLITTER, 20, 0, Map.of());
        assertThat(widthAt(registry.typeOf(document.components().get(0))
                .pinLocation(document.components().get(0), 0))).isEqualTo(8);
        assertThat(widthAt(new GridPoint(20, 0))).isEqualTo(8);
        assertThat(widthAt(new GridPoint(22, 1))).isEqualTo(1);
    }

    @Test
    void widthAtIsOneForAGatePinAndForEmptySpace() {
        place(StandardComponentTypes.BUFFER, 0, 0);
        assertThat(widthAt(new GridPoint(0, 2))).isEqualTo(1);
        assertThat(widthAt(new GridPoint(50, 50))).isEqualTo(1);
    }

    @Test
    void widthAtIsTheWidestWhenPinsOfDifferentWidthsShareAPoint() {
        place(StandardComponentTypes.BUFFER, 0, 0);
        place(StandardComponentTypes.BUS_SPLITTER, 0, 2);
        assertThat(widthAt(new GridPoint(0, 2))).isEqualTo(8);
    }

    @Test
    void widthAtATunnelsPinIsTheWidthOfItsNet() {
        place(StandardComponentTypes.INPUT_PIN, 0, 0, Map.of(InterfacePins.WIDTH, 8));
        GridPoint bus = registry.typeOf(document.components().get(0)).pinLocation(document.components().get(0), 0);
        Component entrance = tunnelWithPinAt(StandardComponentTypes.TUNNEL_ENTRANCE, "D", bus);
        Component exit = tunnelWithPinAt(StandardComponentTypes.TUNNEL_EXIT, "D", new GridPoint(40, 41));
        Component lone = tunnelWithPinAt(StandardComponentTypes.TUNNEL_EXIT, "LONE", new GridPoint(40, 51));

        assertThat(widthAt(pinOf(entrance))).isEqualTo(8);
        assertThat(widthAt(pinOf(exit))).isEqualTo(8);
        assertThat(widthAt(pinOf(lone))).isEqualTo(1);
    }

    @Test
    void aTunnelsPinIsOpenWhenNothingIsDrawnAtItEvenIfItsNameJoinsItToOthers() {
        Component entrance = tunnelWithPinAt(StandardComponentTypes.TUNNEL_ENTRANCE, "A", new GridPoint(0, 1));
        Component exit = tunnelWithPinAt(StandardComponentTypes.TUNNEL_EXIT, "A", new GridPoint(40, 41));
        assertThat(isOpen(entrance)).isTrue();
        assertThat(isOpen(exit)).isTrue();

        document.add(new Wire(document.newWireId(), new GridPoint(-10, 1), new GridPoint(0, 1)));
        assertThat(isOpen(entrance)).isFalse();
        assertThat(isOpen(exit)).isTrue();

        place(StandardComponentTypes.LED, 39, 38); // its pin lands on the salida's, at (40, 41)
        assertThat(isOpen(exit)).isFalse();
    }

    @Test
    void anyOtherPinIsOpenWhenItsNetHoldsNothingElse() {
        place(StandardComponentTypes.BUFFER, 0, 0);
        Component gate = document.components().get(0);
        assertThat(isOpen(gate)).isTrue();

        document.add(new Wire(document.newWireId(), new GridPoint(-10, 2), new GridPoint(0, 2)));
        assertThat(isOpen(gate)).isFalse();
    }

    private int widthAt(GridPoint point) {
        return points.widthAt(document, new NetlistCompiler(registry).compile(document), point);
    }

    private boolean isOpen(Component component) {
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        return points.isOpen(document, component, 0, netlist.netForPin(new PinRef(component.id(), 0)));
    }

    private GridPoint pinOf(Component component) {
        return registry.typeOf(component).pinLocation(component, 0);
    }

    private Component tunnelWithPinAt(String typeId, String name, GridPoint pin) {
        Component atOrigin = registry.require(typeId).newComponent(document.newComponentId(), GridPoint.ORIGIN,
                Rotation.R0, Map.of(Tunnels.NAME, name), "");
        GridPoint offset = pinOf(atOrigin);
        Component placed = atOrigin.translate(pin.x() - offset.x(), pin.y() - offset.y());
        document.add(placed);
        return placed;
    }
}
