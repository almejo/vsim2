package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.layout.CircuitTidyProgress;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TidyProgressTest {

    @Test
    void eachStageSaysWhatItMoves() {
        assertThat(TidyProgress.describe(CircuitTidyProgress.Stage.SEPARATING, 2, 3))
                .isEqualTo("Separando lo que se tapa… ronda 2 de 3");
        assertThat(TidyProgress.describe(CircuitTidyProgress.Stage.MOVING, 2, 5)).isEqualTo("Moviendo partes… 2 de 5");
        assertThat(TidyProgress.describe(CircuitTidyProgress.Stage.MOVING_TEXTS, 1, 4))
                .isEqualTo("Moviendo textos… 1 de 4");
        assertThat(TidyProgress.describe(CircuitTidyProgress.Stage.WIRES, 7, 0)).isEqualTo("Ordenando cables… pasada 7");
    }

    @Test
    void progressShowsTheNewStages() {
        assertThat(TidyProgress.describe(CircuitTidyProgress.Stage.ALIGNING, 0, 0)).isEqualTo("Alineando columnas…");
        assertThat(TidyProgress.describe(CircuitTidyProgress.Stage.STRAIGHTENING, 2, 10))
                .isEqualTo("Enderezando cables… ronda 2 de 10");
    }
}
