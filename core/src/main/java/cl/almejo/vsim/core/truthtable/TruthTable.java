package cl.almejo.vsim.core.truthtable;

import cl.almejo.vsim.core.value.Value;

import java.util.List;
import java.util.OptionalInt;

/**
 * The truth table of a combinational circuit: one row per input combination, counting in binary with the first input
 * column as the most significant bit. {@code hasFeedback} is true when the circuit has a loop without flip-flops.
 */
public record TruthTable(List<Column> inputs, List<Column> outputs, List<Row> rows, boolean hasFeedback) {

    public TruthTable {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        rows = List.copyOf(rows);
    }

    /**
     * The index of the row whose inputs are {@code inputValues}, one value per input column and as wide as it: their
     * bits read as one binary number, the first column the most significant. Empty when a value has a Z bit, a
     * conflict or another width, or the count differs (spec §5.4: then no row is highlighted).
     */
    public OptionalInt rowOf(List<Value> inputValues) {
        if (inputValues.size() != inputs.size()) {
            return OptionalInt.empty();
        }
        long index = 0;
        for (int column = 0; column < inputs.size(); column++) {
            Value value = inputValues.get(column);
            int width = inputs.get(column).width();
            if (value.width() != width || !value.isFullyDefined()) {
                return OptionalInt.empty();
            }
            index = (index << width) | value.bits();
        }
        return index >= 0 && index < rows.size() ? OptionalInt.of((int) index) : OptionalInt.empty();
    }
}
