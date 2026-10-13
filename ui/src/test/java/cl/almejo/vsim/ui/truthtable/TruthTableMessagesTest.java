package cl.almejo.vsim.ui.truthtable;

import cl.almejo.vsim.core.truthtable.Column;
import cl.almejo.vsim.core.truthtable.Preparation;
import cl.almejo.vsim.core.truthtable.Row;
import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class TruthTableMessagesTest {

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @AfterEach
    void restore() {
        Messages.initialize(Messages.SPANISH);
    }

    private static TruthTable table(int inputs, int outputs, int rows) {
        Row row = new Row(List.of(), List.of(), true);
        return new TruthTable(Collections.nCopies(inputs, new Column("A", "c1", 1)),
                Collections.nCopies(outputs, new Column("Y", "c2", 1)), Collections.nCopies(rows, row), false);
    }

    @Test
    void everyRefusalHasItsMessage() {
        assertThat(TruthTableMessages.refusal(new Preparation.Refused(Preparation.Reason.NO_INPUTS, 0)))
                .isEqualTo("No hay entradas: usa interruptores o pines de entrada");
        assertThat(TruthTableMessages.refusal(new Preparation.Refused(Preparation.Reason.NO_OUTPUTS, 2)))
                .isEqualTo("No hay salidas: usa LED o pines de salida");
        assertThat(TruthTableMessages.refusal(new Preparation.Refused(Preparation.Reason.TOO_MANY_INPUT_BITS, 12)))
                .isEqualTo("La tabla tendría 4 096 filas; el máximo es 1 024 (10 bits de entrada)");
        assertThat(TruthTableMessages.refusal(new Preparation.Refused(Preparation.Reason.SEQUENTIAL, 1)))
                .isEqualTo("La tabla de verdad es para circuitos combinacionales: este tiene flip-flops o relojes. "
                        + "Prueba las formas de onda.");
    }

    @Test
    void aSixtyFourBitInputStillGivesARowCount() {
        assertThat(TruthTableMessages.refusal(new Preparation.Refused(Preparation.Reason.TOO_MANY_INPUT_BITS, 64)))
                .startsWith("La tabla tendría 18 446 744 073 709 551 616 filas");
    }

    @Test
    void theSummaryCountsColumnsAndRowsInSingularAndPlural() {
        assertThat(TruthTableMessages.summary(table(3, 2, 8))).isEqualTo("3 entradas, 2 salidas, 8 filas");
        assertThat(TruthTableMessages.summary(table(1, 1, 2))).isEqualTo("1 entrada, 1 salida, 2 filas");
    }

    @Test
    void titlesBannerAndTooltip() {
        assertThat(TruthTableMessages.title("sumador")).isEqualTo("Tabla de verdad — sumador");
        assertThat(TruthTableMessages.title(TruthTableMessages.selectionName()))
                .isEqualTo("Tabla de verdad — selección");
        assertThat(TruthTableMessages.instanceMenuText("Sumador completo"))
                .isEqualTo("Tabla de verdad de «Sumador completo»…");
        assertThat(TruthTableMessages.feedbackBanner()).isEqualTo("El circuito tiene realimentación: cada fila se "
                + "calcula desde cero, así que la memoria no se ve");
        assertThat(TruthTableMessages.unsettledTooltip())
                .isEqualTo("Sin valor estable (oscila o depende del estado anterior)");
    }

    @Test
    void englishTextsComeFromTheEnglishBundle() {
        Messages.initialize(Locale.ENGLISH);
        assertThat(TruthTableMessages.summary(table(3, 1, 8))).isEqualTo("3 inputs, 1 output, 8 rows");
        assertThat(TruthTableMessages.refusal(new Preparation.Refused(Preparation.Reason.TOO_MANY_INPUT_BITS, 11)))
                .isEqualTo("The table would have 2 048 rows; the maximum is 1 024 (10 input bits)");
    }
}
