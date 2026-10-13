package cl.almejo.vsim.core.truthtable;

import java.util.List;

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
}
