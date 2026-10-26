package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;

class JunctionsTest {

    private final CircuitDocument document = new CircuitDocument();

    @Test
    void endpointInsideAnotherWireIsAJunction() {
        wire(document, 0, 0, 10, 0);
        wire(document, 5, 0, 5, 5);
        assertThat(Junctions.of(document)).containsExactly(new GridPoint(5, 0));
    }

    @Test
    void threeEndsMeetingIsAJunction() {
        wire(document, 0, 0, 5, 0);
        wire(document, 5, 0, 10, 0);
        wire(document, 5, 0, 5, 5);
        assertThat(Junctions.of(document)).containsExactly(new GridPoint(5, 0));
    }

    @Test
    void cornersAndCrossingsAreNotJunctions() {
        wire(document, 0, 0, 5, 0);
        wire(document, 5, 0, 5, 5);
        wire(document, 10, -5, 10, 5);
        wire(document, 8, 0, 12, 0);
        assertThat(Junctions.of(document)).isEmpty();
    }

    @Test
    void collinearWiresMeetingEndToEndAreNotAJunction() {
        wire(document, 0, 0, 5, 0);
        wire(document, 5, 0, 10, 0);
        wire(document, 0, 3, 0, 8);
        wire(document, 0, 8, 0, 12);
        assertThat(Junctions.of(document)).isEmpty();
    }

    @Test
    void overlappingCollinearWiresAreNotAJunction() {
        wire(document, 0, 0, 10, 0);
        wire(document, 5, 0, 15, 0);
        wire(document, 20, 0, 20, 10);
        wire(document, 20, 4, 20, 6);
        assertThat(Junctions.of(document)).isEmpty();
    }

    @Test
    void aBranchAtTheEndOfAnOverlapIsStillAJunction() {
        wire(document, 0, 0, 10, 0);
        wire(document, 5, 0, 15, 0);
        wire(document, 10, 0, 10, 5);
        assertThat(Junctions.of(document)).containsExactly(new GridPoint(10, 0));
    }

    @Test
    void fourEndsMeetingIsAJunction() {
        wire(document, 0, 5, 5, 5);
        wire(document, 5, 5, 10, 5);
        wire(document, 5, 0, 5, 5);
        wire(document, 5, 5, 5, 10);
        assertThat(Junctions.of(document)).containsExactly(new GridPoint(5, 5));
    }
}
