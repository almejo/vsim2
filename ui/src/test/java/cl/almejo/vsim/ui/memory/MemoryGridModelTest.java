package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.memory.MemoryContents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryGridModelTest {

    private static MemoryGridModel model(int width, long words) {
        return new MemoryGridModel(MemoryContents.empty(), width, words, MemoryRadix.HEX);
    }

    @ParameterizedTest
    @CsvSource({"1,16", "8,16", "9,8", "16,8", "17,4", "32,4", "33,2", "64,2"})
    void columnsFollowTheWordWidth(int width, int columns) {
        assertThat(model(width, 256).columns()).isEqualTo(columns);
    }

    @Test
    void anAsciiColumnOnlyForWordsOfUpToEightBits() {
        assertThat(model(8, 256).hasAsciiColumn()).isTrue();
        assertThat(model(8, 256).getColumnCount()).isEqualTo(1 + 16 + 1);
        assertThat(model(9, 256).hasAsciiColumn()).isFalse();
        assertThat(model(16, 256).getColumnCount()).isEqualTo(1 + 8);
    }

    @Test
    void rowsRoundUpAndCellsPastTheEndAreEmpty() {
        MemoryGridModel grid = model(8, 2);
        assertThat(grid.getRowCount()).isEqualTo(1);
        assertThat(grid.getValueAt(0, 3)).isEqualTo("");
        assertThat(grid.isCellEditable(0, 3)).isFalse();
        assertThat(grid.isCellEditable(0, 1)).isTrue();
    }

    @Test
    void setCellRejectsNonHexAndTooWideValuesWithKeys() {
        MemoryGridModel grid = model(8, 256);
        assertThat(grid.setCell(0, "G1")).contains(MemoryGridModel.NOT_A_NUMBER);
        assertThat(grid.setCell(0, "")).contains(MemoryGridModel.NOT_A_NUMBER);
        assertThat(grid.setCell(0, "-1")).contains(MemoryGridModel.NOT_A_NUMBER);
        assertThat(grid.setCell(0, "1FF")).contains(MemoryGridModel.TOO_WIDE);
        assertThat(grid.setCell(0, "FFFFFFFFFFFFFFFFF")).contains(MemoryGridModel.NOT_A_NUMBER);
        assertThat(grid.setCell(256, "1")).contains(MemoryGridModel.OUT_OF_RANGE);
        assertThat(grid.edited().count()).isZero();
        assertThat(grid.setCell(0, "ff")).isEmpty();
        assertThat(grid.edited().word(0)).isEqualTo(0xFF);
    }

    @Test
    void aSixtyFourBitWordTakesAnyHexValue() {
        MemoryGridModel grid = model(64, 16);
        assertThat(grid.setCell(1, "FFFFFFFFFFFFFFFF")).isEmpty();
        assertThat(grid.edited().word(1)).isEqualTo(-1L);
        assertThat(grid.getValueAt(0, 2)).isEqualTo("FFFFFFFFFFFFFFFF");
    }

    @Test
    void cellsAreReadAndWrittenInTheChosenBase() {
        MemoryGridModel grid = new MemoryGridModel(MemoryContents.empty(), 8, 256, MemoryRadix.DECIMAL);
        assertThat(grid.setCell(2, "200")).isEmpty();
        assertThat(grid.getValueAt(0, 3)).isEqualTo("200");
        grid.setRadix(MemoryRadix.BINARY);
        assertThat(grid.getValueAt(0, 3)).isEqualTo("11001000");
        grid.setRadix(MemoryRadix.HEX);
        assertThat(grid.getValueAt(0, 3)).isEqualTo("C8");
    }

    @Test
    void aReadOnlyModelRefusesEveryEdit() {
        MemoryGridModel grid = model(8, 256);
        grid.setReadOnly(true);
        assertThat(grid.setCell(0, "1")).contains(MemoryGridModel.READ_ONLY);
        assertThat(grid.paste(0, "1 2")).contains(MemoryGridModel.READ_ONLY);
        assertThat(grid.fill(0, 3, "1")).contains(MemoryGridModel.READ_ONLY);
        assertThat(grid.isCellEditable(0, 1)).isFalse();
    }

    @Test
    void goToUnderstandsHexAddressesAndRefusesTheOnesOutside() {
        MemoryGridModel grid = model(8, 1024);
        OptionalLong last = grid.parseAddress("3FF");
        assertThat(last).hasValue(0x3FF);
        assertThat(grid.rowOf(0x3FF)).isEqualTo(grid.getRowCount() - 1);
        assertThat(grid.columnOf(0x3FF)).isEqualTo(16);
        assertThat(grid.parseAddress("0x10")).hasValue(16);
        assertThat(grid.parseAddress("@10")).hasValue(16);
        assertThat(grid.parseAddress("400")).isEmpty();
        assertThat(grid.parseAddress("xyz")).isEmpty();
    }

    @Test
    void pastingHexTextFromACellSetsTheWordsAndHonoursJumps() {
        MemoryGridModel grid = model(8, 256);
        assertThat(grid.paste(0, "3A 10 @08 FF")).isEmpty();
        assertThat(grid.edited().word(0)).isEqualTo(0x3A);
        assertThat(grid.edited().word(1)).isEqualTo(0x10);
        assertThat(grid.edited().word(8)).isEqualTo(0xFF);
        assertThat(grid.edited().count()).isEqualTo(3);
    }

    @Test
    void pastingStartsWhereTheCellIsAndIsAllOrNothing() {
        MemoryGridModel grid = model(8, 16);
        assertThat(grid.paste(4, "1 2\n3 # comment\n4")).isEmpty();
        assertThat(grid.edited().word(4)).isEqualTo(1);
        assertThat(grid.edited().word(7)).isEqualTo(4);
        assertThat(grid.paste(0, "5 6 XY")).contains(MemoryGridModel.NOT_A_NUMBER);
        assertThat(grid.paste(14, "1 2 3")).contains(MemoryGridModel.OUT_OF_RANGE);
        assertThat(grid.paste(0, "100")).contains(MemoryGridModel.TOO_WIDE);
        assertThat(grid.edited().word(0)).isZero();
        assertThat(grid.edited().word(14)).isZero();
    }

    @Test
    void copyingATwoByTwoSelectionGivesTwoLinesOfHex() {
        MemoryGridModel grid = new MemoryGridModel(
                MemoryContents.decode("@0 0A 0B 0C 0D @10 1A 1B 1C 1D"), 8, 256, MemoryRadix.HEX);
        // rows 0..1, value columns 2..3 (addresses 1, 2 and 0x11, 0x12)
        assertThat(grid.copyText(0, 1, 2, 3)).isEqualTo("0B 0C\n1B 1C");
    }

    @Test
    void copyIgnoresTheAddressAndAsciiColumns() {
        MemoryGridModel grid = new MemoryGridModel(MemoryContents.decode("@0 41 42"), 8, 4, MemoryRadix.HEX);
        assertThat(grid.copyText(0, 0, 0, grid.getColumnCount() - 1)).isEqualTo("41 42 00 00");
        assertThat(grid.getValueAt(0, grid.getColumnCount() - 1)).isEqualTo("AB..");
    }

    @Test
    void theDialogsOwnUndoAndRedoWalkTheEdits() {
        MemoryGridModel grid = model(8, 256);
        grid.setCell(0, "1");
        grid.setCell(1, "2");
        assertThat(grid.canUndo()).isTrue();
        grid.undo();
        assertThat(grid.edited().word(1)).isZero();
        assertThat(grid.edited().word(0)).isEqualTo(1);
        grid.redo();
        assertThat(grid.edited().word(1)).isEqualTo(2);
        grid.undo();
        grid.undo();
        assertThat(grid.canUndo()).isFalse();
        assertThat(grid.edited()).isEqualTo(MemoryContents.empty());
        grid.setCell(3, "7");
        assertThat(grid.canRedo()).isFalse();
    }

    @Test
    void fillingTheWholeOfAHugeMemoryWithZeroIsInstantAndLeavesItEmpty() {
        MemoryGridModel grid = model(16, 1L << 24);
        grid.setCell(5, "1");
        long start = System.nanoTime();
        assertThat(grid.fill(0, (1L << 24) - 1, "0")).isEmpty();
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(500);
        assertThat(grid.edited().count()).isZero();
        grid.undo();
        assertThat(grid.edited().word(5)).isEqualTo(1);
    }

    @Test
    void fillingAPartialPageAndAcrossPagesKeepsTheRestOfTheWords() {
        MemoryGridModel grid = model(8, 1024);
        grid.setCell(0, "1");
        grid.setCell(300, "2");
        assertThat(grid.fill(10, 600, "0")).isEmpty();
        assertThat(grid.edited().word(0)).isEqualTo(1);
        assertThat(grid.edited().word(300)).isZero();
        assertThat(grid.fill(250, 260, "AA")).isEmpty();
        assertThat(grid.edited().word(249)).isZero();
        assertThat(grid.edited().word(250)).isEqualTo(0xAA);
        assertThat(grid.edited().word(260)).isEqualTo(0xAA);
        assertThat(grid.edited().word(261)).isZero();
    }

    @Test
    void fillingARangeIsOneUndoStep() {
        MemoryGridModel grid = model(8, 256);
        assertThat(grid.fill(2, 5, "AA")).isEmpty();
        assertThat(grid.edited().count()).isEqualTo(4);
        assertThat(grid.fill(5, 2, "AA")).contains(MemoryGridModel.OUT_OF_RANGE);
        assertThat(grid.fill(0, 256, "AA")).contains(MemoryGridModel.OUT_OF_RANGE);
        grid.undo();
        assertThat(grid.edited().count()).isZero();
    }

    @Test
    void wordsBeyondTheLastAddressAreKeptAndNotShown() {
        MemoryGridModel grid = new MemoryGridModel(MemoryContents.decode("@100 AB"), 8, 16, MemoryRadix.HEX);
        assertThat(grid.getRowCount()).isEqualTo(1);
        assertThat(grid.edited().word(0x100)).isEqualTo(0xAB);
        grid.setCell(0, "1");
        assertThat(grid.edited().word(0x100)).isEqualTo(0xAB);
    }

    @Test
    void theLiveWordsReplaceTheShownOnesAndTheAccessIsHighlighted() {
        MemoryGridModel grid = model(8, 256);
        grid.setCell(0, "1");
        grid.showLive(MemoryContents.decode("@0 9"));
        assertThat(grid.getValueAt(0, 1)).isEqualTo("09");
        assertThat(grid.edited().word(0)).isEqualTo(1);
        grid.hideLive();
        assertThat(grid.getValueAt(0, 1)).isEqualTo("01");
        grid.showAccess(VisibleState.MemoryAccess.WRITE, 3);
        assertThat(grid.accessAt(3)).isEqualTo(VisibleState.MemoryAccess.WRITE);
        assertThat(grid.accessAt(4)).isEqualTo(VisibleState.MemoryAccess.NONE);
    }

    @Test
    void acceptedEditsFireTableChanges() {
        MemoryGridModel grid = model(8, 256);
        int[] fired = {0};
        grid.addTableModelListener(event -> fired[0]++);
        Optional<String> refusal = grid.setCell(0, "zz");
        assertThat(refusal).isPresent();
        assertThat(fired[0]).isZero();
        grid.setCell(0, "11");
        assertThat(fired[0]).isEqualTo(1);
    }
}
