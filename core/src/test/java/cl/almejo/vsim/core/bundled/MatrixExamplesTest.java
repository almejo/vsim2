package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** «Matriz LED» (a heart from eight switches) and «Marquesina» (a scrolling text from a ROM) are wired, explained and work. */
class MatrixExamplesTest {

    /** The clock of the marquee: 50 up, 50 down. */
    private static final long PERIOD = 100;
    private static final int ROWS = 8;
    /** A little after the counter changes, so the ROM's word has reached the matrix. */
    private static final long SETTLE = 20;

    private static List<Value> shown(ExampleSimulation simulation) {
        Component matrix = simulation.component("Matriz", StandardComponentTypes.LED_MATRIX);
        return ((VisibleState.LedMatrixState) simulation.engine().snapshot().componentState(matrix.id().value()))
                .rows();
    }

    private static long counterValue(ExampleSimulation simulation) {
        Component counter = simulation.component("Contador", StandardComponentTypes.COUNTER);
        return ((VisibleState.StoredValueState) simulation.engine().snapshot()
                .componentState(counter.id().value())).value().bits();
    }

    /** Runs the marquee clock a step at a time until its counter holds {@code address}. */
    private static ExampleSimulation marqueeAt(long address) {
        ExampleSimulation simulation = new ExampleSimulation(MatrixExamples.marquee(), Map.of());
        long time = 0;
        while (counterValue(simulation) != address) {
            time += PERIOD / 2;
            assertThat(time).as("the counter reaches %d", address).isLessThan(PERIOD * 1000);
            simulation.runUntil(time);
        }
        return simulation.runUntil(time + SETTLE);
    }

    @Test
    void bothExamplesAreWiredAndExplainedCleanly() {
        ExampleChecks.assertExample(MatrixExamples.heart());
        ExampleChecks.assertExample(MatrixExamples.marquee());
    }

    @Test
    void theHeartShowsEachSwitchAsItsRowWithTheLeftBitOnTheLeft() {
        ExampleSimulation simulation = new ExampleSimulation(MatrixExamples.heart(), Map.of()).settle();
        assertThat(shown(simulation)).hasSize(ROWS);
        for (int row = 0; row < ROWS; row++) {
            assertThat(shown(simulation).get(row)).as("row %d", row).isEqualTo(Value.of(8, MatrixExamples.HEART[row]));
        }
    }

    @Test
    void clickingABitOfASwitchChangesThatLedOfThatRowOnly() {
        ExampleSimulation simulation = new ExampleSimulation(MatrixExamples.heart(), Map.of()).settle();
        Component rowSwitch = simulation.component("F1", StandardComponentTypes.SWITCH);
        simulation.engine().interactBit(rowSwitch.id().value(), 0);
        assertThat(shown(simulation).get(1)).isEqualTo(Value.of(8, MatrixExamples.HEART[1] | 1));
        assertThat(shown(simulation).get(2)).isEqualTo(Value.of(8, MatrixExamples.HEART[2]));
    }

    @Test
    void theTextFitsTheStripWithAGapBeforeItWrapsRound() {
        boolean[][] picture = MatrixExamples.marqueePicture();
        int lastColumn = -1;
        for (boolean[] row : picture) {
            for (int column = 0; column < row.length; column++) {
                if (row[column]) {
                    lastColumn = Math.max(lastColumn, column);
                }
            }
        }
        assertThat(lastColumn).isBetween(50, MatrixExamples.STRIP_COLUMNS - 4);
        assertThat(picture[3][0]).as("the H is a bar from its first column").isTrue();
        assertThat(picture[3][4]).isTrue();
        assertThat(picture[0][2]).as("and has no top in its middle").isFalse();
    }

    @Test
    void theRomHoldsOneWindowPerRowAndStartingColumn() {
        boolean[][] picture = MatrixExamples.marqueePicture();
        assertThat(MatrixExamples.marqueeContents().word(0))
                .isEqualTo(MatrixExamples.windowWord(picture[0], 0));
        assertThat(MatrixExamples.marqueeContents().word(8 * 5 + 3))
                .isEqualTo(MatrixExamples.windowWord(picture[3], 5));
        assertThat(MatrixExamples.marqueeContents().word(8 * 63 + 7))
                .isEqualTo(MatrixExamples.windowWord(picture[7], 63));
    }

    @Test
    void afterTheFirstSweepTheMatrixShowsTheFirstWindowOfTheText() {
        ExampleSimulation simulation = marqueeAt(ROWS - 1);
        boolean[][] picture = MatrixExamples.marqueePicture();
        for (int row = 0; row < ROWS; row++) {
            assertThat(shown(simulation).get(row)).as("row %d", row)
                    .isEqualTo(Value.of(MatrixExamples.WINDOW_COLUMNS, MatrixExamples.windowWord(picture[row], 0)));
        }
        assertThat(shown(simulation).get(3).bits() >>> 27).as("the H: five lit LEDs on its middle row")
                .isEqualTo(0b11111);
    }

    @Test
    void eachSweepDrawsTheTextOneColumnFurtherAlongAndTheScanKeepsTheRowsItHasNotReached() {
        ExampleSimulation simulation = marqueeAt(8 * 5 + 3);
        boolean[][] picture = MatrixExamples.marqueePicture();
        for (int row = 0; row < ROWS; row++) {
            int offset = row <= 3 ? 5 : 4;
            assertThat(shown(simulation).get(row)).as("row %d", row)
                    .isEqualTo(Value.of(MatrixExamples.WINDOW_COLUMNS, MatrixExamples.windowWord(picture[row], offset)));
        }
    }

    @Test
    void theTextWrapsRoundAfterSixtyFourSweepsWithoutAProblem() {
        ExampleSimulation simulation = marqueeAt(8 * 63 + 7);
        simulation.runUntil(PERIOD * 8 * 70);
        assertThat(simulation.engine().snapshot().failure()).isEmpty();
        assertThat(shown(simulation)).hasSize(ROWS);
    }
}
