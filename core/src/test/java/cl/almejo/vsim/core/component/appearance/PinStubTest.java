package cl.almejo.vsim.core.component.appearance;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PinStubTest {

    private final GridSize box = new GridSize(6, 4);

    @Test
    void aStubPointsInwardsFromTheNearestEdgeAndASideWinsATie() {
        assertThat(PinStub.of(new GridPoint(0, 2), box, 8)).isEqualTo(new PinStub(0, 16, 48, 32, 1, 0));
        assertThat(PinStub.of(new GridPoint(6, 1), box, 8)).isEqualTo(new PinStub(48, 8, 48, 32, -1, 0));
        assertThat(PinStub.of(new GridPoint(3, 0), box, 8)).isEqualTo(new PinStub(24, 0, 48, 32, 0, 1));
        assertThat(PinStub.of(new GridPoint(3, 4), box, 8)).isEqualTo(new PinStub(24, 32, 48, 32, 0, -1));
        assertThat(PinStub.of(new GridPoint(0, 0), box, 1).onSide()).as("a corner is on a side").isTrue();
        assertThat(PinStub.of(new GridPoint(3, 0), box, 1).onSide()).isFalse();
    }
}
