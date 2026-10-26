package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.PinRef;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;

class WireGraphTest {

    private final CircuitDocument document = new CircuitDocument();

    private ElementId buffer(int x, int y) {
        ElementId id = document.newComponentId();
        document.add(new Component(id, TestComponentTypes.BUFFER, new GridPoint(x, y), Rotation.R0, Map.of(), ""));
        return id;
    }

    private WireGraph graph() {
        return WireGraph.of(document.wires(), document, TestComponentTypes.registry());
    }

    @Test
    void pinsAreFoundAtTheirLocation() {
        ElementId buffer = buffer(0, 0);
        assertThat(graph().pinsAt(new GridPoint(0, 1))).containsExactly(new PinRef(buffer, 0));
        assertThat(graph().pinsAt(new GridPoint(2, 1))).containsExactly(new PinRef(buffer, 1));
        assertThat(graph().pinsAt(new GridPoint(1, 1))).isEmpty();
    }

    @Test
    void wiresThroughAPointIncludeEndsAndInteriors() {
        Wire horizontal = wire(document, 0, 0, 10, 0);
        Wire vertical = wire(document, 5, 0, 5, 4);
        Wire crossing = wire(document, 7, -3, 7, 3);
        WireGraph graph = graph();
        assertThat(graph.wiresThrough(new GridPoint(5, 0))).containsExactly(horizontal, vertical);
        assertThat(graph.wiresThrough(new GridPoint(7, 0))).containsExactly(horizontal, crossing);
        assertThat(graph.wiresThrough(new GridPoint(20, 20))).isEmpty();
    }

    @Test
    void connectionPointsAreOtherWireEndsAndPinsOnTheWireNotItsFreeEnds() {
        ElementId buffer = buffer(0, 0);
        Wire main = wire(document, 2, 1, 12, 1);
        wire(document, 6, 1, 6, 5);
        wire(document, 9, -2, 9, 4);
        assertThat(buffer).isNotNull();
        assertThat(graph().connectionPointsOn(main))
                .containsExactlyInAnyOrder(new GridPoint(2, 1), new GridPoint(6, 1));
    }

    @Test
    void anEndTouchedByAnotherWireInteriorCounts() {
        Wire stub = wire(document, 5, 0, 5, 3);
        wire(document, 0, 3, 10, 3);
        assertThat(graph().connectionPointsOn(stub)).containsExactly(new GridPoint(5, 3));
    }

    @Test
    void anEndAloneIsNotConnected() {
        Wire lonely = wire(document, 0, 0, 4, 0);
        assertThat(graph().connectionPointsOn(lonely)).isEmpty();
    }
}
