package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WireStretchTest {

    private static final ComponentTypeRegistry REGISTRY = TestComponentTypes.registry();

    private final CircuitDocument document = new CircuitDocument();

    private ElementId wire(int startX, int startY, int endX, int endY) {
        Wire wire = new Wire(document.newWireId(), new GridPoint(startX, startY), new GridPoint(endX, endY));
        document.add(wire);
        return wire.id();
    }

    /** A test buffer at (x, y): input pin at (x, y + 1), output pin at (x + 2, y + 1). */
    private void buffer(int x, int y) {
        document.add(new Component(document.newComponentId(), TestComponentTypes.BUFFER, new GridPoint(x, y),
                Rotation.R0, Map.of(), ""));
    }

    /** A test 8-bit probe whose only pin is at (x, y). */
    private void wideProbe(int x, int y) {
        document.add(new Component(document.newComponentId(), TestComponentTypes.WIDE_PROBE, new GridPoint(x, y),
                Rotation.R0, Map.of(), ""));
    }

    private Set<ElementId> stretch(ElementId wireId) {
        return WireStretch.of(document, REGISTRY, wireId);
    }

    private Set<ElementId> connected(ElementId wireId) {
        return WireStretch.connected(document, REGISTRY, wireId);
    }

    @Test
    void anLRouteBetweenTwoPinsIsOneStretchInRouteOrder() {
        buffer(0, 0);   // output pin at (2, 1)
        buffer(20, 9);  // input pin at (20, 10)
        ElementId first = wire(2, 1, 10, 1);
        ElementId second = wire(10, 1, 10, 10);
        ElementId third = wire(10, 10, 20, 10);
        assertThat(stretch(second)).containsExactly(first, second, third);
        assertThat(stretch(first)).containsExactlyInAnyOrder(first, second, third);
        assertThat(stretch(third)).containsExactlyInAnyOrder(first, second, third);
    }

    @Test
    void aTJunctionAtACornerStopsTheChain() {
        ElementId first = wire(0, 0, 10, 0);
        ElementId second = wire(10, 0, 10, 10);
        ElementId branch = wire(10, 0, 20, 0);
        assertThat(stretch(first)).containsExactly(first);
        assertThat(stretch(second)).containsExactly(second);
        assertThat(stretch(branch)).containsExactly(branch);
    }

    @Test
    void aPinAtACornerStopsTheChain() {
        buffer(8, -1); // input pin at (8, 0)
        ElementId first = wire(0, 0, 8, 0);
        ElementId second = wire(8, 0, 8, 10);
        assertThat(stretch(first)).containsExactly(first);
        assertThat(stretch(second)).containsExactly(second);
    }

    @Test
    void aWireEndOnAnotherWiresInteriorStopsTheChain() {
        ElementId main = wire(0, 0, 20, 0);
        ElementId first = wire(10, 10, 10, 0);
        ElementId second = wire(0, 10, 10, 10);
        assertThat(stretch(second)).containsExactly(second, first);
        assertThat(stretch(main)).containsExactly(main);
    }

    @Test
    void twoCollinearWiresJoinedEndToEndAreOneStretch() {
        ElementId left = wire(0, 0, 10, 0);
        ElementId right = wire(10, 0, 20, 0);
        assertThat(stretch(right)).containsExactly(left, right);
    }

    @Test
    void aClosedLoopGivesItsFourWiresOnce() {
        ElementId top = wire(0, 0, 10, 0);
        ElementId right = wire(10, 0, 10, 10);
        ElementId bottom = wire(0, 10, 10, 10);
        ElementId left = wire(0, 0, 0, 10);
        assertThat(stretch(top)).hasSize(4).containsExactlyInAnyOrder(top, right, bottom, left);
        assertThat(connected(bottom)).containsExactly(top, right, bottom, left);
    }

    @Test
    void aCrossingNeitherContinuesTheStretchNorJoinsTheNet() {
        ElementId horizontal = wire(0, 5, 20, 5);
        ElementId vertical = wire(10, 0, 10, 20);
        assertThat(stretch(horizontal)).containsExactly(horizontal);
        assertThat(connected(horizontal)).containsExactly(horizontal);
        assertThat(connected(vertical)).containsExactly(vertical);
    }

    @Test
    void connectedOverATGivesTheThreeBranchesInDocumentOrder() {
        ElementId stem = wire(10, 0, 10, 10);
        ElementId main = wire(0, 0, 20, 0);
        ElementId other = wire(30, 30, 40, 30);
        ElementId tail = wire(10, 10, 15, 10);
        assertThat(connected(tail)).containsExactly(stem, main, tail).doesNotContain(other);
        assertThat(connected(main)).containsExactly(stem, main, tail);
    }

    @Test
    void connectedDoesNotGoThroughComponents() {
        buffer(10, -1); // input (10, 0), output (12, 0)
        ElementId before = wire(0, 0, 10, 0);
        ElementId after = wire(12, 0, 20, 0);
        assertThat(connected(before)).containsExactly(before);
        assertThat(connected(after)).containsExactly(after);
    }

    @Test
    void busWiresBehaveTheSame() {
        wideProbe(0, 0);
        wideProbe(20, 10);
        ElementId first = wire(0, 0, 10, 0);
        ElementId second = wire(10, 0, 10, 10);
        ElementId third = wire(10, 10, 20, 10);
        ElementId branch = wire(10, 5, 15, 5);
        assertThat(stretch(first)).containsExactly(first, second, third);
        assertThat(connected(branch)).containsExactly(first, second, third, branch);
    }

    @Test
    void anUnknownIdOrANonWireGivesNothing() {
        wire(0, 0, 10, 0);
        assertThat(stretch(new ElementId("w999"))).isEmpty();
        assertThat(connected(new ElementId("w999"))).isEmpty();
        buffer(30, 30);
        ElementId component = document.components().get(0).id();
        assertThat(stretch(component)).isEmpty();
        assertThat(connected(component)).isEmpty();
    }
}
