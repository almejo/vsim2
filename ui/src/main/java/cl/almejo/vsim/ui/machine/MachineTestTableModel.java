package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.machine.MachinePin;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.MachineTestRun;
import cl.almejo.vsim.core.machine.MachineTests;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.waveform.WaveformPainter;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * What the test table shows of a machine at one moment (hito 4, task 5): the tests (the machine's, or an unwritten
 * empty «Prueba 1» while it has none), the chosen one run on the machine, the rows made from its pins (in the shape of
 * the wave strip: inputs, «estado», then each output with what was expected of it, and the ✓/✗ row) and where each
 * cell is. Immutable; the table makes a new one after every change of the session.
 * <p>
 * Column k is the cycle before edge k: the state during it, the inputs given, and the outputs just before the edge
 * (decision 3). After the last column a dimmed ghost column says where the last edge leads.
 */
final class MachineTestTableModel {

    /** The width of one column (one edge). */
    static final int COLUMN_WIDTH = 44;
    /** The rows that hold a wave, a value or a verdict are as tall as the wave strip's rows. */
    static final int ROW_HEIGHT = WaveformPainter.ROW_HEIGHT;
    /** The «Entradas» / «Salidas» bars and a bus's header are lower. */
    static final int GROUP_HEIGHT = 18;
    /** The width of a state code in the state row (64 states fit in 6 bits). */
    static final int STATE_CODE_WIDTH = 8;

    /** What a row shows. */
    enum Kind {
        GROUP, BUS, INPUT, STATE, OUTPUT, EXPECTED, VERDICT;

        /** Whether a click or a key changes its cells. */
        boolean editable() {
            return this == INPUT || this == EXPECTED;
        }
    }

    /** One row: its kind, its name, the pin it belongs to (by id) and where it is. */
    record Row(Kind kind, String label, Optional<String> pinId, int top, int height) {
    }

    private final StateMachine machine;
    private final List<MachineTest> tests;
    private final boolean unwritten;
    private final int testIndex;
    private final int chosenColumn;
    private final List<MachineTestRun.TestResult> results;
    private final List<Row> rows;

    private MachineTestTableModel(StateMachine machine, int chosenTest, int chosenColumn,
                                  List<MachineTestRun.TestResult> results) {
        this.machine = machine;
        this.unwritten = machine.tests().isEmpty();
        this.tests = shown(machine.tests());
        this.testIndex = clamp(chosenTest, tests.size());
        this.results = results;
        this.chosenColumn = clamp(chosenColumn, test().columns().size());
        this.rows = rowsOf(machine);
    }

    static MachineTestTableModel of(StateMachine machine, int chosenTest, int chosenColumn) {
        return new MachineTestTableModel(machine, chosenTest, chosenColumn,
                MachineTestRun.runAll(machine.withTests(shown(machine.tests()))));
    }

    /**
     * As {@link #of}, reusing {@code previous}'s run when the machine is the same instance: choosing a test or a
     * column runs nothing again (a run of 16 tests × 64 columns costs milliseconds on the EDT).
     */
    static MachineTestTableModel of(StateMachine machine, int chosenTest, int chosenColumn,
                                    MachineTestTableModel previous) {
        if (previous.machine != machine) {
            return of(machine, chosenTest, chosenColumn);
        }
        return new MachineTestTableModel(machine, chosenTest, chosenColumn, previous.results);
    }

    /**
     * The tests a table shows for {@code tests}: those, or one empty «Prueba 1» while there are none (decision 8: it is
     * written only when something in it changes).
     */
    static List<MachineTest> shown(List<MachineTest> tests) {
        if (!tests.isEmpty()) {
            return tests;
        }
        return List.of(MachineTest.empty(MachineTests.freeId(List.of()), defaultName(List.of()),
                MachineTests.DEFAULT_COLUMNS));
    }

    /** «Prueba n» for a new test: n one more than the tests there are, skipping a name already taken. */
    static String defaultName(List<MachineTest> tests) {
        for (int number = tests.size() + 1; ; number++) {
            String name = Messages.format("machine.test.defaultName", number);
            if (tests.stream().noneMatch(test -> test.name().equals(name))) {
                return name;
            }
        }
    }

    private static int clamp(int index, int size) {
        return Math.max(0, Math.min(index, size - 1));
    }

    private static List<Row> rowsOf(StateMachine machine) {
        List<Row> rows = new ArrayList<>();
        Consumer<Row> add = rows::add;
        if (!machine.inputs().isEmpty()) {
            add.accept(new Row(Kind.GROUP, Messages.text("machine.test.row.inputs"), Optional.empty(), 0,
                    GROUP_HEIGHT));
            if (machine.inputsAsBus()) {
                add.accept(new Row(Kind.BUS, MachinePieceRows.inputsText(machine), Optional.empty(), 0,
                        GROUP_HEIGHT));
            }
            for (MachinePin pin : machine.inputs()) {
                add.accept(new Row(Kind.INPUT, pin.name(), Optional.of(pin.id()), 0, ROW_HEIGHT));
            }
        }
        add.accept(new Row(Kind.STATE, Messages.text("machine.test.row.state"), Optional.empty(), 0, ROW_HEIGHT));
        if (!machine.outputs().isEmpty()) {
            add.accept(new Row(Kind.GROUP, Messages.text("machine.test.row.outputs"), Optional.empty(), 0,
                    GROUP_HEIGHT));
            if (machine.outputsAsBus()) {
                add.accept(new Row(Kind.BUS, MachinePieceRows.outputsText(machine), Optional.empty(), 0,
                        GROUP_HEIGHT));
            }
            for (MachinePin pin : machine.outputs()) {
                add.accept(new Row(Kind.OUTPUT, pin.name(), Optional.of(pin.id()), 0, ROW_HEIGHT));
                add.accept(new Row(Kind.EXPECTED, Messages.format("machine.test.row.expected", pin.name()),
                        Optional.of(pin.id()), 0, ROW_HEIGHT));
            }
        }
        add.accept(new Row(Kind.VERDICT, Messages.text("machine.test.row.verdict"), Optional.empty(), 0,
                ROW_HEIGHT));
        List<Row> placed = new ArrayList<>();
        int top = 0;
        for (Row row : rows) {
            placed.add(new Row(row.kind(), row.label(), row.pinId(), top, row.height()));
            top += row.height();
        }
        return List.copyOf(placed);
    }

    StateMachine machine() {
        return machine;
    }

    /** The tests shown, one per tab. */
    List<MachineTest> tests() {
        return tests;
    }

    /** True while the machine has no test and the table shows the empty «Prueba 1» it has not written yet. */
    boolean unwritten() {
        return unwritten;
    }

    int testIndex() {
        return testIndex;
    }

    MachineTest test() {
        return tests.get(testIndex);
    }

    MachineTestRun.TestResult result() {
        return results.get(testIndex);
    }

    int chosenColumn() {
        return chosenColumn;
    }

    List<Row> rows() {
        return rows;
    }

    /** The rows a click or a key can change, in order. */
    List<Row> editableRows() {
        return rows.stream().filter(row -> row.kind().editable()).toList();
    }

    int columnCount() {
        return test().columns().size();
    }

    /** The columns and the ghost column after them. */
    int width() {
        return (columnCount() + 1) * COLUMN_WIDTH;
    }

    int height() {
        Row last = rows.getLast();
        return last.top() + last.height();
    }

    /** The cell of {@code row} in {@code column}; {@link #columnCount()} is the ghost column. */
    Rectangle cell(int row, int column) {
        Row found = rows.get(row);
        return new Rectangle(column * COLUMN_WIDTH, found.top(), COLUMN_WIDTH, found.height());
    }

    /** The real column under {@code x} (not the ghost). */
    OptionalInt columnAt(int x) {
        int column = Math.floorDiv(x, COLUMN_WIDTH);
        return x >= 0 && column < columnCount() ? OptionalInt.of(column) : OptionalInt.empty();
    }

    /** The row under {@code y}. */
    OptionalInt rowAt(int y) {
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            if (y >= row.top() && y < row.top() + row.height()) {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    boolean input(int column, String pinId) {
        return test().columns().get(column).input(pinId);
    }

    LogicValue expected(int column, String pinId) {
        return test().columns().get(column).expected(pinId);
    }

    /** The run's column, when the test could run. */
    Optional<MachineTestRun.ColumnResult> columnResult(int column) {
        List<MachineTestRun.ColumnResult> columns = result().columns();
        return column < columns.size() ? Optional.of(columns.get(column)) : Optional.empty();
    }

    /** The index of a state in the machine (its code in the state row), or -1. */
    int stateIndex(String stateId) {
        List<MachineState> states = machine.states();
        for (int index = 0; index < states.size(); index++) {
            if (states.get(index).id().equals(stateId)) {
                return index;
            }
        }
        return -1;
    }

    String stateName(String stateId) {
        return machine.state(stateId).map(MachineState::name).orElse(stateId);
    }

    /** «→ S2»: where the last edge leads; none for a test that cannot run. */
    Optional<String> ghostText() {
        List<MachineTestRun.ColumnResult> columns = result().columns();
        if (columns.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Messages.format("machine.test.ghost", stateName(columns.getLast().nextStateId())));
    }

    boolean canAddColumn() {
        return columnCount() < MachineTests.MAXIMUM_COLUMNS;
    }

    boolean canDeleteColumn() {
        return columnCount() > 1;
    }

    boolean canAddTest() {
        return tests.size() < MachineTests.MAXIMUM_TESTS;
    }

    /** A tab's title: the test's name and ✓ or ✗ when it has a verdict. */
    String tabTitle(int index) {
        String name = tests.get(index).name();
        return switch (results.get(index).verdict()) {
            case PASS -> Messages.format("machine.test.tab.passing", name);
            case FAIL -> Messages.format("machine.test.tab.failing", name);
            case NOTHING_EXPECTED -> name;
        };
    }

    /** «Prueba «Dos 1 seguidos»: 3 ✓, 1 ✗ · en el flanco 2 salió Z = 0». */
    String summary() {
        String name = test().name();
        MachineTestRun.TestResult result = result();
        if (result.stop().isPresent()) {
            return Messages.format("machine.test.summary.noInitial", name);
        }
        long passing = result.columns().stream().filter(column -> column.verdict() == MachineTestRun.Verdict.PASS)
                .count();
        long failing = result.columns().stream().filter(column -> column.verdict() == MachineTestRun.Verdict.FAIL)
                .count();
        if (passing == 0 && failing == 0) {
            return Messages.format("machine.test.summary.nothingExpected", name);
        }
        String counts = Messages.format("machine.test.summary.counts", name, passing, failing);
        for (int column = 0; column < result.columns().size(); column++) {
            MachineTestRun.ColumnResult found = result.columns().get(column);
            Optional<String> wrong = firstMismatch(found);
            if (wrong.isPresent()) {
                return counts + Messages.format("machine.test.summary.failure", column + 1, pinName(wrong.get()),
                        bit(found.outputs().get(wrong.get())));
            }
        }
        return counts;
    }

    private static Optional<String> firstMismatch(MachineTestRun.ColumnResult column) {
        return column.matches().entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey)
                .findFirst();
    }

    private String pinName(String pinId) {
        return machine.outputs().stream().filter(pin -> pin.id().equals(pinId)).map(MachinePin::name).findFirst()
                .orElse(pinId);
    }

    private static int bit(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    /** Whether the output {@code pinId} came out other than expected in {@code column}. */
    boolean mismatch(int column, String pinId) {
        return columnResult(column).map(result -> Boolean.FALSE.equals(result.matches().get(pinId))).orElse(false);
    }

    /** What the pointer over a cell is told: why a result is wrong, how to change a cell, a state's whole name. */
    Optional<String> tooltip(int row, int column) {
        Row found = rows.get(row);
        if (column == columnCount()) {
            return found.kind() == Kind.STATE && !result().columns().isEmpty()
                    ? Optional.of(Messages.format("machine.test.ghostTip",
                    stateName(result().columns().getLast().nextStateId())))
                    : Optional.empty();
        }
        if (column < 0 || column > columnCount()) {
            return Optional.empty();
        }
        return switch (found.kind()) {
            case INPUT -> Optional.of(Messages.text("machine.test.cell.inputTip"));
            case EXPECTED -> Optional.of(Messages.text("machine.test.cell.expectedTip"));
            case OUTPUT -> mismatch(column, found.pinId().orElseThrow())
                    ? Optional.of(mismatchText(column, found.pinId().get())) : Optional.empty();
            case STATE -> columnResult(column).map(result -> stateName(result.stateId()));
            case VERDICT -> columnResult(column).filter(result -> result.verdict() == MachineTestRun.Verdict.FAIL)
                    .map(result -> result.matches().entrySet().stream().filter(entry -> !entry.getValue())
                            .map(entry -> Messages.format("machine.test.cell.mismatchNamed", pinName(entry.getKey()),
                                    1 - bit(result.outputs().get(entry.getKey())),
                                    bit(result.outputs().get(entry.getKey()))))
                            .collect(Collectors.joining(" · ")));
            case GROUP, BUS -> Optional.of(found.label());
        };
    }

    private String mismatchText(int column, String pinId) {
        int came = bit(columnResult(column).orElseThrow().outputs().get(pinId));
        return Messages.format("machine.test.cell.mismatch", 1 - came, came);
    }
}
