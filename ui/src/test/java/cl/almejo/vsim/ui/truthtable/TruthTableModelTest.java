package cl.almejo.vsim.ui.truthtable;

import cl.almejo.vsim.core.truthtable.Column;
import cl.almejo.vsim.core.truthtable.Row;
import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TruthTableModelTest {

    private final TruthTable table = new TruthTable(
            List.of(new Column("A", "c1", 1), new Column("D", "c2", 2)),
            List.of(new Column("Y", "c3", 1)),
            List.of(new Row(List.of(Value.ZERO, Value.of(2, 1)), List.of(Value.ONE.withConflict()), true),
                    new Row(List.of(Value.ONE, Value.of(2, 2)), List.of(Value.ZERO), false)),
            false);
    private final TruthTableModel model = new TruthTableModel(table);

    @Test
    void columnsAreTheInputsThenTheOutputs() {
        assertThat(model.getColumnCount()).isEqualTo(3);
        assertThat(List.of(model.getColumnName(0), model.getColumnName(1), model.getColumnName(2)))
                .containsExactly("A", "D", "Y");
        assertThat(model.firstOutputColumn()).isEqualTo(2);
        assertThat(model.getColumnClass(1)).isEqualTo(String.class);
        assertThat(model.table()).isSameAs(table);
    }

    @Test
    void cellsAreTextAndAnUnsettledRowShowsATilde() {
        assertThat(model.getRowCount()).isEqualTo(2);
        assertThat(List.of(model.getValueAt(0, 0), model.getValueAt(0, 1), model.getValueAt(0, 2)))
                .containsExactly("0", "0x1", "X");
        assertThat(List.of(model.getValueAt(1, 0), model.getValueAt(1, 1), model.getValueAt(1, 2)))
                .containsExactly("1", "0x2", "~");
        assertThat(model.isSettled(0)).isTrue();
        assertThat(model.isSettled(1)).isFalse();
        assertThat(model.isCellEditable(0, 0)).isFalse();
    }
}
