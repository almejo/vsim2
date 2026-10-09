package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JunctionsTest {

    private final CircuitDocument document = new CircuitDocument();

    private void wire(int startX, int startY, int endX, int endY) {
        document.add(new Wire(document.newWireId(), new GridPoint(startX, startY), new GridPoint(endX, endY)));
    }

    @Test
    void endpointInsideAnotherWireIsAJunction() {
        wire(0, 0, 10, 0);
        wire(5, 0, 5, 5);
        assertThat(Junctions.of(document)).containsExactly(new GridPoint(5, 0));
    }

    @Test
    void threeEndsMeetingIsAJunction() {
        wire(0, 0, 5, 0);
        wire(5, 0, 10, 0);
        wire(5, 0, 5, 5);
        assertThat(Junctions.of(document)).containsExactly(new GridPoint(5, 0));
    }

    @Test
    void cornersAndCrossingsAreNotJunctions() {
        wire(0, 0, 5, 0);
        wire(5, 0, 5, 5);
        wire(10, -5, 10, 5);
        wire(8, 0, 12, 0);
        assertThat(Junctions.of(document)).isEmpty();
    }

    @Test
    void collinearWiresMeetingEndToEndAreNotAJunction() {
        wire(0, 0, 5, 0);
        wire(5, 0, 10, 0);
        wire(0, 3, 0, 8);
        wire(0, 8, 0, 12);
        assertThat(Junctions.of(document)).isEmpty();
    }

    @Test
    void overlappingCollinearWiresAreNotAJunction() {
        wire(0, 0, 10, 0);
        wire(5, 0, 15, 0);
        wire(20, 0, 20, 10);
        wire(20, 4, 20, 6);
        assertThat(Junctions.of(document)).isEmpty();
    }

    @Test
    void aBranchAtTheEndOfAnOverlapIsStillAJunction() {
        wire(0, 0, 10, 0);
        wire(5, 0, 15, 0);
        wire(10, 0, 10, 5);
        assertThat(Junctions.of(document)).containsExactly(new GridPoint(10, 0));
    }

    @Test
    void fourEndsMeetingIsAJunction() {
        wire(0, 5, 5, 5);
        wire(5, 5, 10, 5);
        wire(5, 0, 5, 5);
        wire(5, 5, 5, 10);
        assertThat(Junctions.of(document)).containsExactly(new GridPoint(5, 5));
    }
}
