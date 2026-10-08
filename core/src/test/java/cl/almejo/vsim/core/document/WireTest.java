package cl.almejo.vsim.core.document;

import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WireTest {

    private static final ElementId ID = new ElementId("w1");

    @Test
    void endpointsAreNormalized() {
        Wire wire = new Wire(ID, new GridPoint(5, 1), new GridPoint(2, 1));
        assertThat(wire.start()).isEqualTo(new GridPoint(2, 1));
        assertThat(wire.end()).isEqualTo(new GridPoint(5, 1));
        assertThat(wire.isHorizontal()).isTrue();
    }

    @Test
    void diagonalWireIsRejected() {
        assertThatThrownBy(() -> new Wire(ID, new GridPoint(0, 0), new GridPoint(1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroLengthWireIsRejected() {
        assertThatThrownBy(() -> new Wire(ID, new GridPoint(3, 3), new GridPoint(3, 3)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void containsInteriorAndEndpoints() {
        Wire wire = new Wire(ID, new GridPoint(0, 2), new GridPoint(0, 6));
        assertThat(wire.contains(new GridPoint(0, 2))).isTrue();
        assertThat(wire.contains(new GridPoint(0, 4))).isTrue();
        assertThat(wire.contains(new GridPoint(0, 7))).isFalse();
        assertThat(wire.contains(new GridPoint(1, 4))).isFalse();
    }

    @Test
    void overlapsCollinearRequiresSharedLength() {
        Wire wire = new Wire(ID, new GridPoint(0, 0), new GridPoint(4, 0));
        assertThat(wire.overlapsCollinear(new Wire(ID, new GridPoint(3, 0), new GridPoint(8, 0)))).isTrue();
        assertThat(wire.overlapsCollinear(new Wire(ID, new GridPoint(4, 0), new GridPoint(8, 0)))).isFalse();
        assertThat(wire.overlapsCollinear(new Wire(ID, new GridPoint(2, -1), new GridPoint(2, 1)))).isFalse();
        assertThat(wire.overlapsCollinear(new Wire(ID, new GridPoint(0, 1), new GridPoint(4, 1)))).isFalse();
    }
}
