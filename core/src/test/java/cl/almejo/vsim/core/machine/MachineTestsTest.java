package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.logic.LogicValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The edits of a machine's tests: cells, columns, ids, and fitting them to the machine's pins. */
class MachineTestsTest {

    private final MachineTest empty = MachineTest.empty("p1", "Prueba 1", MachineTests.DEFAULT_COLUMNS);

    @Test
    void anEmptyTestHasEightColumnsOfZerosExpectingNothing() {
        assertThat(empty.columns()).hasSize(8).allSatisfy(column -> {
            assertThat(column.input("x")).isFalse();
            assertThat(column.expected("Z")).isEqualTo(LogicValue.DONT_CARE);
        });
    }

    @Test
    void anInputCellCanBeSetAndCleared() {
        MachineTest set = MachineTests.withInput(empty, 2, "x", true);

        assertThat(set.columns().get(2).input("x")).isTrue();
        assertThat(set.columns().get(1).input("x")).isFalse();
        assertThat(MachineTests.withInput(set, 2, "x", false)).isEqualTo(empty);
    }

    @Test
    void anExpectedCellTakesZeroOneOrNoImporta() {
        MachineTest one = MachineTests.withExpected(empty, 0, "Z", LogicValue.ONE);
        MachineTest zero = MachineTests.withExpected(one, 0, "Z", LogicValue.ZERO);

        assertThat(one.columns().get(0).expected("Z")).isEqualTo(LogicValue.ONE);
        assertThat(zero.columns().get(0).expected("Z")).isEqualTo(LogicValue.ZERO);
        assertThat(MachineTests.withExpected(zero, 0, "Z", LogicValue.DONT_CARE)).isEqualTo(empty);
    }

    @Test
    void anInsertedColumnCopiesTheInputsOfTheColumnBeforeItButExpectsNothing() {
        MachineTest test = MachineTests.withExpected(MachineTests.withInput(empty, 7, "x", true), 7, "Z",
                LogicValue.ONE);

        MachineTest longer = MachineTests.withColumnInserted(test, 8);

        assertThat(longer.columns()).hasSize(9);
        assertThat(longer.columns().get(8).input("x")).isTrue();
        assertThat(longer.columns().get(8).expected("Z")).isEqualTo(LogicValue.DONT_CARE);
    }

    @Test
    void aColumnInsertedFirstHasEveryInputAtZero() {
        MachineTest test = MachineTests.withInput(empty, 0, "x", true);

        MachineTest longer = MachineTests.withColumnInserted(test, 0);

        assertThat(longer.columns().get(0)).isEqualTo(TestColumn.EMPTY);
        assertThat(longer.columns().get(1).input("x")).isTrue();
    }

    @Test
    void aTestNeverGoesBeyondItsColumnLimitNorBelowOneColumn() {
        MachineTest full = MachineTest.empty("p1", "Larga", MachineTests.MAXIMUM_COLUMNS);
        MachineTest single = MachineTest.empty("p1", "Corta", 1);

        assertThatThrownBy(() -> MachineTests.withColumnInserted(full, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MachineTests.withColumnRemoved(single, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removingAColumnShiftsTheOthers() {
        MachineTest test = MachineTests.withInput(empty, 3, "x", true);

        MachineTest shorter = MachineTests.withColumnRemoved(test, 1);

        assertThat(shorter.columns()).hasSize(7);
        assertThat(shorter.columns().get(2).input("x")).isTrue();
    }

    @Test
    void aFreeIdIsTheFirstPNumberNoTestHas() {
        assertThat(MachineTests.freeId(List.of())).isEqualTo("p1");
        assertThat(MachineTests.freeId(List.of(empty, empty.withId("p3")))).isEqualTo("p2");
        assertThat(MachineTests.freeId(List.of(empty, empty.withId("p2")))).isEqualTo("p3");
    }

    @Test
    void fittingDropsTheValuesOfPinsTheMachineDoesNotHave() {
        StateMachine detector = MachineExamples.twoOnesDetector();
        MachineTest test = MachineTests.withExpected(MachineTests.withExpected(MachineTests.withInput(
                MachineTests.withInput(empty, 0, "x", true), 0, "y", true), 0, "Z", LogicValue.ONE), 0, "W",
                LogicValue.ZERO);

        List<MachineTest> fitted = MachineTests.fittedTo(detector, List.of(test));

        assertThat(fitted).singleElement().satisfies(kept -> {
            assertThat(kept.columns().get(0).inputs()).isEqualTo(Map.of("x", true));
            assertThat(kept.columns().get(0).expected()).isEqualTo(Map.of("Z", true));
        });
    }

    @Test
    void fittingReadsEachSideOnlyFromItsOwnPins() {
        StateMachine detector = MachineExamples.twoOnesDetector();
        MachineTest crossed = MachineTests.withExpected(MachineTests.withInput(empty, 0, "Z", true), 0, "x",
                LogicValue.ONE);

        assertThat(MachineTests.fittedTo(detector, List.of(crossed))).containsExactly(empty);
    }

    @Test
    void aTestNeedsASlugIdAndOneToSixtyFourColumns() {
        assertThatThrownBy(() -> MachineTest.empty("Prueba 1", "Prueba 1", 8))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MachineTest("p1", "Vacía", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        List<TestColumn> tooMany = new ArrayList<>(Collections.nCopies(65, TestColumn.EMPTY));
        assertThatThrownBy(() -> new MachineTest("p1", "Larga", tooMany))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void twoTestsOfOneMachineCannotShareAnId() {
        StateMachine detector = MachineExamples.twoOnesDetector();

        assertThatThrownBy(() -> detector.withTests(List.of(empty, empty.withName("Otra"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
