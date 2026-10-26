package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.core.truthtable.Column;
import cl.almejo.vsim.core.truthtable.Row;
import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KarnaughHandoffTest {

    /** {@code inputs} one-bit inputs (or one {@code width}-bit input) and {@code outputs} one-bit outputs, all 0. */
    private static TruthTable table(int inputs, int inputWidth, int outputs, int outputWidth) {
        List<Column> inputColumns = new ArrayList<>();
        for (int index = 0; index < inputs; index++) {
            inputColumns.add(new Column("I" + index, "c" + index, inputWidth));
        }
        List<Column> outputColumns = new ArrayList<>();
        for (int index = 0; index < outputs; index++) {
            outputColumns.add(new Column("O" + index, "d" + index, outputWidth));
        }
        List<Row> rows = new ArrayList<>();
        for (int row = 0; row < 1 << inputs * inputWidth; row++) {
            List<Value> inputValues = new ArrayList<>();
            for (int index = 0; index < inputs; index++) {
                inputValues.add(Value.of(inputWidth, row >> (inputs - 1 - index) * inputWidth));
            }
            List<Value> outputValues = new ArrayList<>();
            for (int index = 0; index < outputs; index++) {
                outputValues.add(Value.of(outputWidth, row == 3 ? 1 : 0));
            }
            rows.add(new Row(inputValues, outputValues, true));
        }
        return new TruthTable(inputColumns, outputColumns, rows, false);
    }

    @Test
    void twoToFourOneBitInputsAndOneToFourOneBitOutputsAreTaken() {
        assertThat(KarnaughHandoff.refusal(table(2, 1, 1, 1))).isEmpty();
        assertThat(KarnaughHandoff.refusal(table(4, 1, 4, 1))).isEmpty();
    }

    @Test
    void otherTablesAreRefusedWithTheReason() {
        assertThat(KarnaughHandoff.refusal(table(5, 1, 1, 1)))
                .hasValue("Karnaugh funciona hasta 4 entradas de 1 bit.");
        assertThat(KarnaughHandoff.refusal(table(2, 2, 1, 1)))
                .hasValue("Karnaugh funciona hasta 4 entradas de 1 bit.");
        assertThat(KarnaughHandoff.refusal(table(1, 1, 1, 1)))
                .hasValue("Karnaugh necesita al menos 2 entradas de 1 bit.");
        assertThat(KarnaughHandoff.refusal(table(2, 1, 5, 1)))
                .hasValue("Karnaugh funciona con 1 a 4 salidas de 1 bit.");
        assertThat(KarnaughHandoff.refusal(table(2, 1, 1, 4)))
                .hasValue("Karnaugh funciona con 1 a 4 salidas de 1 bit.");
    }

    @Test
    void theWizardsTableKeepsTheColumnsAndValues() {
        TruthTableSpec spec = KarnaughHandoff.spec(table(2, 1, 2, 1), "Circuito");
        assertThat(spec.title()).isEqualTo("Circuito");
        assertThat(spec.inputNames()).containsExactly("I0", "I1");
        assertThat(spec.outputNames()).containsExactly("O0", "O1");
        assertThat(spec.outputs().get(1)).containsExactly(LogicValue.ZERO, LogicValue.ZERO, LogicValue.ZERO,
                LogicValue.ONE);
        assertThatThrownBy(() -> KarnaughHandoff.spec(table(1, 1, 1, 1), "Circuito"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
