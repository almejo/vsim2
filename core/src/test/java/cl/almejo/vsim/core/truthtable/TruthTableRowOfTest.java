package cl.almejo.vsim.core.truthtable;

import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link TruthTable#rowOf}: which row the live input values select (spec §5.4, the highlighted row). */
class TruthTableRowOfTest {

    /** A table over {@code inputs} with every row, its outputs left empty. */
    private static TruthTable table(List<Column> inputs) {
        int bits = inputs.stream().mapToInt(Column::width).sum();
        List<Row> rows = new ArrayList<>();
        for (int index = 0; index < 1 << bits; index++) {
            rows.add(new Row(TruthTableEvaluator.inputValues(inputs, index), List.of(), true));
        }
        return new TruthTable(inputs, List.of(new Column("Y", "y", 1)), rows, false);
    }

    private final TruthTable threeBits = table(List.of(new Column("A", "a", 1), new Column("B", "b", 1),
            new Column("C", "c", 1)));

    @Test
    void rowOfReadsTheFirstInputAsTheMostSignificantBit() {
        assertThat(threeBits.rowOf(List.of(Value.ONE, Value.ZERO, Value.ONE))).hasValue(5);
        assertThat(threeBits.rowOf(List.of(Value.ZERO, Value.ZERO, Value.ONE))).hasValue(1);
        for (int index = 0; index < threeBits.rows().size(); index++) {
            assertThat(threeBits.rowOf(threeBits.rows().get(index).inputs())).hasValue(index);
        }
    }

    @Test
    void rowOfIsEmptyWithAZInputAConflictOrValuesThatDoNotFit() {
        assertThat(threeBits.rowOf(List.of(Value.ONE, Value.HIGH_IMPEDANCE, Value.ZERO))).isEmpty();
        assertThat(threeBits.rowOf(List.of(Value.ONE.withConflict(), Value.ZERO, Value.ZERO))).isEmpty();
        assertThat(threeBits.rowOf(List.of(Value.ONE, Value.ZERO))).as("one value per column").isEmpty();
        assertThat(threeBits.rowOf(List.of(Value.of(2, 1), Value.ZERO, Value.ZERO))).as("as wide as its column")
                .isEmpty();
    }

    @Test
    void aBusInputCountsAllItsBits() {
        TruthTable bus = table(List.of(new Column("D", "d", 2), new Column("E", "e", 1)));

        assertThat(bus.rowOf(List.of(Value.of(2, 3), Value.ONE))).hasValue(7);
        assertThat(bus.rowOf(List.of(Value.of(2, 2), Value.ZERO))).hasValue(4);
        assertThat(bus.rowOf(List.of(new Value(2, 1, 2, false), Value.ZERO))).as("one bit in Z").isEmpty();
    }
}
