package cl.almejo.vsim.core.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A truth table written by the user (wizard or import): named one-bit inputs and outputs and, per output, one value
 * per row. Row {@code r} is the input combination whose binary digits are the inputs in order, the first input being
 * the most significant bit (row 5 with inputs A, B, C is A = 1, B = 0, C = 1).
 *
 * @param title       the table's title (frame title and proposed component name)
 * @param inputNames  1..{@value #MAXIMUM_INPUTS} names, non-blank, unique ignoring case together with the outputs
 * @param outputNames 1..{@value #MAXIMUM_OUTPUTS} names
 * @param outputs     per output, exactly {@code 2^inputCount} values
 */
public record TruthTableSpec(String title, List<String> inputNames, List<String> outputNames,
                             List<List<LogicValue>> outputs) {

    /**
     * The import's limit: a 10-input table (1 024 rows) is minimized, built and checked in about 3 s at worst within
     * {@link TableImport#MAXIMUM_GATES}. The wizard and the expressions have their own, smaller, limits.
     */
    public static final int MAXIMUM_INPUTS = 10;
    /** The import's limit (a decoder of 4 inputs has 16 outputs); the wizard and the expressions have their own. */
    public static final int MAXIMUM_OUTPUTS = 16;

    public TruthTableSpec {
        title = title == null ? "" : title;
        inputNames = List.copyOf(inputNames);
        outputNames = List.copyOf(outputNames);
        if (inputNames.isEmpty() || inputNames.size() > MAXIMUM_INPUTS) {
            throw new IllegalArgumentException("inputs must be 1.." + MAXIMUM_INPUTS + ": " + inputNames.size());
        }
        if (outputNames.isEmpty() || outputNames.size() > MAXIMUM_OUTPUTS) {
            throw new IllegalArgumentException("outputs must be 1.." + MAXIMUM_OUTPUTS + ": " + outputNames.size());
        }
        Set<String> seen = new HashSet<>();
        for (String name : concatenated(inputNames, outputNames)) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("blank name");
            }
            if (!seen.add(name.strip().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("repeated name: " + name);
            }
        }
        if (outputs.size() != outputNames.size()) {
            throw new IllegalArgumentException("one value list per output expected");
        }
        int rows = 1 << inputNames.size();
        List<List<LogicValue>> copied = new ArrayList<>();
        for (List<LogicValue> column : outputs) {
            if (column.size() != rows) {
                throw new IllegalArgumentException("each output needs " + rows + " values: " + column.size());
            }
            copied.add(List.copyOf(column));
        }
        outputs = List.copyOf(copied);
    }

    /** A table with every output at 0. */
    public static TruthTableSpec zeros(String title, List<String> inputNames, List<String> outputNames) {
        List<List<LogicValue>> outputs = new ArrayList<>();
        for (int output = 0; output < outputNames.size(); output++) {
            outputs.add(Collections.nCopies(1 << inputNames.size(), LogicValue.ZERO));
        }
        return new TruthTableSpec(title, inputNames, outputNames, outputs);
    }

    public int inputCount() {
        return inputNames.size();
    }

    public int outputCount() {
        return outputNames.size();
    }

    public int rowCount() {
        return 1 << inputNames.size();
    }

    public LogicValue value(int output, int row) {
        return outputs.get(output).get(row);
    }

    /** The value of input {@code input} (0 = first, the most significant bit) in row {@code row}. */
    public boolean inputValue(int row, int input) {
        return (row >> (inputCount() - 1 - input) & 1) == 1;
    }

    public TruthTableSpec withValue(int output, int row, LogicValue value) {
        List<List<LogicValue>> changed = new ArrayList<>(outputs);
        List<LogicValue> column = new ArrayList<>(outputs.get(output));
        column.set(row, value);
        changed.set(output, column);
        return new TruthTableSpec(title, inputNames, outputNames, changed);
    }

    private static List<String> concatenated(List<String> first, List<String> second) {
        List<String> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }
}
