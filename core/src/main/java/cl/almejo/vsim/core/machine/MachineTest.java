package cl.almejo.vsim.core.machine;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A saved test of a machine («Modo prueba»): the name the student gives it and its columns, one per clock edge, run
 * from the initial state ({@link MachineTestRun}). The {@code id} is a slug ({@code p1}, {@code p2}…) that stays when
 * the test is renamed. A test lives only in the circuit file, never in «Mis componentes» (the user's rule,
 * 2026-11-17; {@link MachineDefinitions#forLibrary}).
 */
public record MachineTest(String id, String name, List<TestColumn> columns) {

    public MachineTest {
        StateMachine.requireId(id, "test");
        Objects.requireNonNull(name, "name");
        columns = List.copyOf(columns);
        if (columns.isEmpty() || columns.size() > MachineTests.MAXIMUM_COLUMNS) {
            throw new IllegalArgumentException("a test has 1 to " + MachineTests.MAXIMUM_COLUMNS + " columns, not "
                    + columns.size());
        }
    }

    /** A new test with {@code columns} columns, every input 0 and nothing expected: «Prueba 1» before any change. */
    public static MachineTest empty(String id, String name, int columns) {
        return new MachineTest(id, name, Collections.nCopies(columns, TestColumn.EMPTY));
    }

    public MachineTest withName(String newName) {
        return new MachineTest(id, newName, columns);
    }

    public MachineTest withColumns(List<TestColumn> newColumns) {
        return new MachineTest(id, name, newColumns);
    }

    /** This test under another id: a duplicate in the same machine. */
    public MachineTest withId(String newId) {
        return new MachineTest(newId, name, columns);
    }
}
