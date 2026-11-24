package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.MachineTests;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The test table's edits of the tests themselves: a cell advanced as a click, a test added or duplicated. */
class MachineTestTableEditsTest {

    private static final MachineTestTableModel.Row INPUT_ROW = new MachineTestTableModel.Row(
            MachineTestTableModel.Kind.INPUT, "x", Optional.of("x"), 0, 20);
    private static final MachineTestTableModel.Row EXPECTED_ROW = new MachineTestTableModel.Row(
            MachineTestTableModel.Kind.EXPECTED, "Z", Optional.of("Z"), 20, 20);

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @Test
    void anInputCellTogglesAndAnExpectedCellGoesZeroOneDontCare() {
        MachineTest test = MachineTest.empty("t1", "Prueba 1", 2);

        MachineTest toggled = MachineTestTable.advanced(test, INPUT_ROW, 1);
        MachineTest expectedOnce = MachineTestTable.advanced(test, EXPECTED_ROW, 0);
        MachineTest expectedTwice = MachineTestTable.advanced(expectedOnce, EXPECTED_ROW, 0);

        assertThat(toggled.columns().get(1).input("x")).isTrue();
        assertThat(toggled.columns().get(0).input("x")).isFalse();
        assertThat(MachineTestTable.advanced(toggled, INPUT_ROW, 1).columns().get(1).input("x")).isFalse();
        assertThat(test.columns().get(0).expected("Z")).isEqualTo(LogicValue.DONT_CARE);
        assertThat(expectedOnce.columns().get(0).expected("Z")).isEqualTo(LogicValue.ZERO);
        assertThat(expectedTwice.columns().get(0).expected("Z")).isEqualTo(LogicValue.ONE);
    }

    @Test
    void anAddedTestIsEmptyWithTheDefaultColumnsAfterTheOthers() {
        List<MachineTest> tests = List.of(MachineTest.empty("t1", "Prueba 1", 3));

        List<MachineTest> added = MachineTestTable.withEmptyTestAdded(tests);

        assertThat(added).hasSize(2);
        assertThat(added.getFirst()).isEqualTo(tests.getFirst());
        assertThat(added.get(1).id()).isNotEqualTo("t1");
        assertThat(added.get(1).name()).isEqualTo("Prueba 2");
        assertThat(added.get(1).columns()).hasSize(MachineTests.DEFAULT_COLUMNS);
    }

    @Test
    void aDuplicatedTestComesRightAfterItsOriginalWithAFreeIdAndACopyName() {
        MachineTest first = MachineTest.empty("t1", "Prueba 1", 3);
        MachineTest second = MachineTest.empty("t2", "Prueba 2", 4);

        List<MachineTest> copied = MachineTestTable.withTestDuplicated(List.of(first, second), 0);

        assertThat(copied).hasSize(3);
        assertThat(copied.get(0)).isEqualTo(first);
        assertThat(copied.get(2)).isEqualTo(second);
        assertThat(copied.get(1).id()).isNotIn("t1", "t2");
        assertThat(copied.get(1).name()).isEqualTo("Prueba 1 (copia)");
        assertThat(copied.get(1).columns()).isEqualTo(first.columns());
    }
}
