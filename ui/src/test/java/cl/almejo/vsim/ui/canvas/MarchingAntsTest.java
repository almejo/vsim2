package cl.almejo.vsim.ui.canvas;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MarchingAntsTest {

    @Test
    void advanceMovesTheDashesOnePixelAndWrapsAtThePeriod() {
        MarchingAnts ants = new MarchingAnts();
        assertThat(ants.phase()).isZero();
        ants.advance();
        assertThat(ants.phase()).isEqualTo(1.0);
        for (int step = 0; step < 6; step++) {
            ants.advance();
        }
        assertThat(ants.phase()).as("one full period later").isCloseTo(0.0, within(1e-9));
    }

    @Test
    void theStrokeCarriesThePhase() {
        MarchingAnts ants = new MarchingAnts();
        ants.advance();
        ants.advance();
        assertThat(ants.stroke().getDashPhase()).isEqualTo(2f);
        assertThat(ants.stroke().getDashArray()).isNotEmpty();
    }
}
