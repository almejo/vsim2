package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.logic.LogicValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * The edits of a machine's tests (the test table of «Modo prueba»): pure functions that return a new test or list.
 * Cells are addressed by column index and pin id (a pin's id survives a rename, a move and «Como un bus»).
 */
public final class MachineTests {

    /** How many columns a new test starts with (decision 5 of hito 4). */
    public static final int DEFAULT_COLUMNS = 8;
    /** At most this many columns per test (decision 5 of hito 4): every test re-runs on each draft edit. */
    public static final int MAXIMUM_COLUMNS = 64;
    /** At most this many tests per machine (decision 5 of hito 4). */
    public static final int MAXIMUM_TESTS = 16;

    private static final String ID_PREFIX = "p";

    private MachineTests() {
    }

    /** {@code test} with the input {@code pinId} at {@code value} in {@code column}. */
    public static MachineTest withInput(MachineTest test, int column, String pinId, boolean value) {
        return withColumn(test, column, cell -> cell.withInput(pinId, value));
    }

    /** {@code test} expecting {@code value} of the output {@code pinId} in {@code column} (DONT_CARE clears it). */
    public static MachineTest withExpected(MachineTest test, int column, String pinId, LogicValue value) {
        return withColumn(test, column, cell -> cell.withExpected(pinId, value));
    }

    private static MachineTest withColumn(MachineTest test, int column, UnaryOperator<TestColumn> change) {
        List<TestColumn> columns = new ArrayList<>(test.columns());
        Objects.checkIndex(column, columns.size());
        columns.set(column, change.apply(columns.get(column)));
        return test.withColumns(columns);
    }

    /**
     * {@code test} with a new column at {@code index} (0 to its column count) holding the inputs of the column before
     * it, the common case of an input held over several edges, and expecting nothing; at index 0 every input is 0.
     *
     * @throws IllegalArgumentException when the test already has {@link #MAXIMUM_COLUMNS}
     */
    public static MachineTest withColumnInserted(MachineTest test, int index) {
        List<TestColumn> columns = new ArrayList<>(test.columns());
        Objects.checkIndex(index, columns.size() + 1);
        if (columns.size() >= MAXIMUM_COLUMNS) {
            throw new IllegalArgumentException("a test has at most " + MAXIMUM_COLUMNS + " columns");
        }
        columns.add(index, index == 0 ? TestColumn.EMPTY : columns.get(index - 1).holdingInputs());
        return test.withColumns(columns);
    }

    /**
     * {@code test} without the column {@code index}.
     *
     * @throws IllegalArgumentException when it is the test's only column
     */
    public static MachineTest withColumnRemoved(MachineTest test, int index) {
        List<TestColumn> columns = new ArrayList<>(test.columns());
        Objects.checkIndex(index, columns.size());
        if (columns.size() == 1) {
            throw new IllegalArgumentException("a test keeps at least one column");
        }
        columns.remove(index);
        return test.withColumns(columns);
    }

    /** The first of {@code p1}, {@code p2}… that no test of {@code tests} has: the id of a new or duplicated test. */
    public static String freeId(List<MachineTest> tests) {
        Set<String> taken = new HashSet<>();
        tests.forEach(test -> taken.add(test.id()));
        return freeId(taken);
    }

    /** The first of {@code p1}, {@code p2}… not in {@code takenIds} (a file's test without an id). */
    public static String freeId(Set<String> takenIds) {
        int number = 1;
        while (takenIds.contains(ID_PREFIX + number)) {
            number++;
        }
        return ID_PREFIX + number;
    }

    /**
     * {@code tests} with only the values of pins {@code machine} has: inputs of its input pins, expected values of its
     * output pins, by id. What is written to the real machine after «Dejar como estaba» (the draft may have pins the
     * machine lacks) and what a pin removed or merged away leaves.
     */
    public static List<MachineTest> fittedTo(StateMachine machine, List<MachineTest> tests) {
        Set<String> inputIds = ids(machine.inputs());
        Set<String> outputIds = ids(machine.outputs());
        return tests.stream().map(test -> test.withColumns(test.columns().stream()
                .map(column -> column.retaining(inputIds::contains, outputIds::contains)).toList())).toList();
    }

    private static Set<String> ids(List<MachinePin> pins) {
        Set<String> ids = new HashSet<>();
        pins.forEach(pin -> ids.add(pin.id()));
        return ids;
    }
}
