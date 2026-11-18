package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.io.MachineEntries.TestEntry;
import cl.almejo.vsim.core.machine.MachinePin;
import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.MachineTests;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.core.machine.TestColumn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import static cl.almejo.vsim.core.io.CircuitFormatException.invalid;

/**
 * A machine's tests in a file: one string per pin id, one character per column, {@code 0}/{@code 1} for an input
 * and {@code 0}/{@code 1}/{@code -} («no importa») for an expected output, e.g.
 * {@code "inputs": {"x": "01101100"}, "expected": {"Z": "--1-0---"}}. Pins in pin order, then the ids of pins the
 * machine no longer has (sorted; kept, and ignored by the run); a pin that is all 0 (input) or all {@code -}
 * (expected) is left out, and so is an empty map.
 */
final class MachineTestEntries {

    private static final char ZERO = '0';
    private static final char ONE = '1';
    private static final char DONT_CARE = '-';

    private MachineTestEntries() {
    }

    static List<TestEntry> entries(StateMachine machine) {
        return machine.tests().stream().map(test -> new TestEntry(test.id(), test.name(), test.columns().size(),
                written(test, machine.inputs(), TestColumn::inputs, MachineTestEntries::inputCharacter),
                written(test, machine.outputs(), TestColumn::expected, MachineTestEntries::expectedCharacter)))
                .toList();
    }

    private interface CellCharacter {
        char of(TestColumn column, String pinId);
    }

    private static char inputCharacter(TestColumn column, String pinId) {
        return column.input(pinId) ? ONE : ZERO;
    }

    private static char expectedCharacter(TestColumn column, String pinId) {
        return switch (column.expected(pinId)) {
            case ZERO -> ZERO;
            case ONE -> ONE;
            case DONT_CARE -> DONT_CARE;
        };
    }

    private static Map<String, String> written(MachineTest test, List<MachinePin> pins,
                                               Function<TestColumn, Map<String, Boolean>> side,
                                               CellCharacter character) {
        Set<String> pinIds = new LinkedHashSet<>();
        pins.forEach(pin -> pinIds.add(pin.id()));
        Set<String> others = new TreeSet<>();
        test.columns().forEach(column -> side.apply(column).keySet().stream()
                .filter(pinId -> !pinIds.contains(pinId)).forEach(others::add));
        pinIds.addAll(others);
        Map<String, String> values = new LinkedHashMap<>();
        for (String pinId : pinIds) {
            boolean anyValue = test.columns().stream().anyMatch(column -> side.apply(column).containsKey(pinId));
            if (anyValue) {
                StringBuilder text = new StringBuilder();
                test.columns().forEach(column -> text.append(character.of(column, pinId)));
                values.put(pinId, text.toString());
            }
        }
        return values;
    }

    /**
     * The tests of {@code entries}. An entry with only a name (how hitos 1–3 wrote the placeholder) reads as one empty
     * column with a fresh id; a value of the wrong length or with another character is an invalid machine.
     */
    static List<MachineTest> tests(List<TestEntry> entries, String definitionId) throws CircuitFormatException {
        Set<String> takenIds = new HashSet<>();
        for (TestEntry entry : entries) {
            CircuitFormat.requireEntry(entry, "tests of " + definitionId);
            if (entry.id() != null) {
                takenIds.add(entry.id());
            }
        }
        List<MachineTest> tests = new ArrayList<>();
        for (TestEntry entry : entries) {
            String id = entry.id() != null ? entry.id() : freshId(takenIds);
            tests.add(test(entry, id, definitionId));
        }
        return tests;
    }

    private static String freshId(Set<String> takenIds) {
        String id = MachineTests.freeId(takenIds);
        takenIds.add(id);
        return id;
    }

    private static MachineTest test(TestEntry entry, String id, String definitionId) throws CircuitFormatException {
        int count = entry.columns() == null ? 1 : entry.columns();
        if (count < 1 || count > MachineTests.MAXIMUM_COLUMNS) {
            throw invalid("machine of " + definitionId + ": test " + id + " has " + count + " columns");
        }
        List<Map<String, Boolean>> inputs = cells(count);
        List<Map<String, Boolean>> expected = cells(count);
        String where = "machine of " + definitionId + ": test " + id;
        for (Map.Entry<String, String> value : values(entry.inputs())) {
            for (int column = 0; column < count; column++) {
                char cell = cell(value, column, count, where, ZERO, ONE);
                inputs.get(column).put(value.getKey(), cell == ONE);
            }
        }
        for (Map.Entry<String, String> value : values(entry.expected())) {
            for (int column = 0; column < count; column++) {
                char cell = cell(value, column, count, where, ZERO, ONE, DONT_CARE);
                if (cell != DONT_CARE) {
                    expected.get(column).put(value.getKey(), cell == ONE);
                }
            }
        }
        List<TestColumn> columns = new ArrayList<>();
        for (int column = 0; column < count; column++) {
            columns.add(new TestColumn(inputs.get(column), expected.get(column)));
        }
        return new MachineTest(id, entry.name(), columns);
    }

    private static Set<Map.Entry<String, String>> values(Map<String, String> byPin) {
        return byPin == null ? Set.of() : byPin.entrySet();
    }

    private static List<Map<String, Boolean>> cells(int count) {
        List<Map<String, Boolean>> cells = new ArrayList<>();
        for (int column = 0; column < count; column++) {
            cells.add(new LinkedHashMap<>());
        }
        return cells;
    }

    private static char cell(Map.Entry<String, String> value, int column, int count, String where,
                             char... allowed) throws CircuitFormatException {
        String text = value.getValue();
        if (text == null || text.length() != count) {
            throw invalid(where + ": the values of " + value.getKey() + " need " + count + " characters");
        }
        char cell = text.charAt(column);
        for (char character : allowed) {
            if (cell == character) {
                return cell;
            }
        }
        throw invalid(where + ": " + value.getKey() + " has the value " + cell + " in column " + (column + 1));
    }
}
