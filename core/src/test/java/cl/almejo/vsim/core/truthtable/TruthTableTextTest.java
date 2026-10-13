package cl.almejo.vsim.core.truthtable;

import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TruthTableTextTest {

    private static final Value CONFLICT_ONE = Value.ONE.withConflict();

    /** Inputs «A, b» (1 bit) and «D» (2 bits); outputs «S» and «C». The second row never settled. */
    private static TruthTable table() {
        return new TruthTable(
                List.of(new Column("A, b", "c1", 1), new Column("D", "c2", 2)),
                List.of(new Column("S", "c3", 1), new Column("C", "c4", 1)),
                List.of(new Row(List.of(Value.ZERO, Value.of(2, 3)), List.of(Value.ONE, Value.HIGH_IMPEDANCE), true),
                        new Row(List.of(Value.ONE, Value.of(2, 0)), List.of(Value.ZERO, Value.ONE), false)),
                false);
    }

    @Test
    void oneBitCellsAreZeroOneZAndXForAConflict() {
        assertThat(TruthTableText.cell(Value.ZERO)).isEqualTo("0");
        assertThat(TruthTableText.cell(Value.ONE)).isEqualTo("1");
        assertThat(TruthTableText.cell(Value.HIGH_IMPEDANCE)).isEqualTo("Z");
        assertThat(TruthTableText.cell(CONFLICT_ONE)).isEqualTo("X");
    }

    @Test
    void busCellsAreHexWithZAndQuestionMarkDigitsLikeBusWires() {
        assertThat(TruthTableText.cell(Value.of(2, 3))).isEqualTo("0x3");
        assertThat(TruthTableText.cell(Value.of(8, 0xA5))).isEqualTo("0xA5");
        assertThat(TruthTableText.cell(Value.highImpedance(4))).isEqualTo("0xZ");
        assertThat(TruthTableText.cell(new Value(8, 0x05, 0x30, false))).isEqualTo("0x?5");
        assertThat(TruthTableText.cell(Value.of(8, 1).withConflict())).isEqualTo("X");
    }

    @Test
    void theOutputsOfARowThatNeverSettledAreATilde() {
        assertThat(TruthTableText.header(table())).containsExactly("A, b", "D", "S", "C");
        assertThat(TruthTableText.cells(table().rows().get(0))).containsExactly("0", "0x3", "1", "Z");
        assertThat(TruthTableText.cells(table().rows().get(1))).containsExactly("1", "0x0", "~", "~");
    }

    @Test
    void tabSeparatedTextHasAHeaderAndOneLinePerRow() {
        assertThat(TruthTableText.tabSeparated(table()))
                .isEqualTo("A, b\tD\tS\tC\n0\t0x3\t1\tZ\n1\t0x0\t~\t~\n");
    }

    @Test
    void tabsAndLineBreaksInNamesBecomeSpacesInTheCopiedText() {
        TruthTable table = new TruthTable(List.of(new Column("a\tb", "c1", 1)),
                List.of(new Column("y\nz", "c2", 1)), List.of(), false);
        assertThat(TruthTableText.tabSeparated(table)).isEqualTo("a b\ty z\n");
    }

    @Test
    void csvQuotesFieldsWithCommasQuotesOrLineBreaks() {
        assertThat(TruthTableText.csv(table())).isEqualTo("\"A, b\",D,S,C\n0,0x3,1,Z\n1,0x0,~,~\n");
        TruthTable quoted = new TruthTable(List.of(new Column("say \"hi\"", "c1", 1)),
                List.of(new Column("two\nlines", "c2", 1)), List.of(), false);
        assertThat(TruthTableText.csv(quoted)).isEqualTo("\"say \"\"hi\"\"\",\"two\nlines\"\n");
    }

    @Test
    void recordsCopyTheirListsAndCheckTheColumnWidth() {
        assertThatThrownBy(() -> new Column("A", "c1", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Column("A", "c1", 65)).isInstanceOf(IllegalArgumentException.class);
        ArrayList<Value> values = new ArrayList<>(List.of(Value.ONE));
        Row row = new Row(values, List.of(), true);
        values.clear();
        assertThat(row.inputs()).containsExactly(Value.ONE);
    }
}
