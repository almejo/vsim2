package cl.almejo.vsim.core.terminal;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.List;

import static cl.almejo.vsim.core.terminal.ScreenWriter.logisim;
import static cl.almejo.vsim.core.terminal.ScreenWriter.rows;
import static cl.almejo.vsim.core.terminal.ScreenWriter.vt;
import static cl.almejo.vsim.core.terminal.ScreenWriter.write;
import static cl.almejo.vsim.core.terminal.ScreenWriter.writeBytes;
import static org.assertj.core.api.Assertions.assertThat;

/** The screen as a whole: cells, history, transcript, byte log, versions, frames and adoption. */
class TerminalScreenTest {

    private static String rowText(TerminalCell[] row) {
        StringBuilder text = new StringBuilder();
        for (TerminalCell cell : row) {
            text.appendCodePoint(cell.codePoint());
        }
        return text.toString().stripTrailing();
    }

    private static TerminalSettings withScrollback(int rows, int scrollbackLines) {
        return new TerminalSettings(TerminalMode.VT, rows, 10, scrollbackLines, true, 8, TerminalEncoding.UTF8,
                true, false, true, 8);
    }

    @Test
    void aCellKeepsItsBytesAndTheTimeOfItsLastByte() {
        TerminalScreen screen = vt(1, 10);
        screen.write(0xC3, 40);
        screen.write(0xB1, 41);
        TerminalCell cell = screen.frame().cell(0, 0);
        assertThat(cell.codePoint()).isEqualTo('ñ');
        assertThat(cell.bytes()).containsExactly(0xC3, 0xB1);
        assertThat(cell.time()).isEqualTo(41);
        assertThat(cell.written()).isTrue();
        assertThat(screen.frame().cell(0, 1).written()).isFalse();
    }

    @Test
    void wideCharactersTakeOneCell() {
        TerminalScreen screen = write(vt(1, 10), "a😀b");
        assertThat(rows(screen)).containsExactly("a😀b");
        assertThat(screen.frame().cursorColumn()).isEqualTo(3);
    }

    @Test
    void aBadUtf8SequenceShowsTheReplacementCharacter() {
        TerminalScreen screen = writeBytes(vt(1, 10), HexFormat.of().parseHex("41e28242"));
        assertThat(rows(screen)).containsExactly("A�B");
        assertThat(screen.frame().cell(0, 1).bytes()).containsExactly(0xE2, 0x82);
    }

    @Test
    void latinOneMapsEveryByteToItsCharacter() {
        TerminalSettings settings = new TerminalSettings(TerminalMode.VT, 1, 10, 0, true, 8, TerminalEncoding.LATIN1,
                true, false, true, 8);
        TerminalScreen screen = writeBytes(new TerminalScreen(settings), HexFormat.of().parseHex("f1e9a9"));
        assertThat(rows(screen)).containsExactly("ñé©");
    }

    @Test
    void sevenBitDataMasksBeforeDecoding() {
        TerminalSettings settings = new TerminalSettings(TerminalMode.VT, 1, 10, 0, true, 7, TerminalEncoding.UTF8,
                true, false, true, 8);
        TerminalScreen screen = writeBytes(new TerminalScreen(settings), HexFormat.of().parseHex("c3b1"));
        assertThat(rows(screen)).containsExactly("C1");
    }

    @Test
    void scrolledRowsGoToTheHistoryUpToItsLimitOldestFirst() {
        TerminalScreen screen = write(new TerminalScreen(withScrollback(2, 3)), "l0\nl1\nl2\nl3\nl4\nl5");
        assertThat(screen.history(10)).extracting(TerminalScreenTest::rowText).containsExactly("l1", "l2", "l3");
        assertThat(screen.history(2)).extracting(TerminalScreenTest::rowText).containsExactly("l2", "l3");
        assertThat(rows(screen)).containsExactly("l4", "l5");
    }

    @Test
    void noScrollbackKeepsNoHistory() {
        TerminalScreen screen = write(logisim(1, 10), "a\nb\nc");
        assertThat(screen.history(10)).isEmpty();
    }

    @Test
    void clearEmptiesTheScreenAndTheHistory() {
        TerminalScreen screen = write(new TerminalScreen(withScrollback(2, 10)), "a\nb\nc");
        screen.clear();
        assertThat(rows(screen)).containsExactly("", "");
        assertThat(screen.history(10)).isEmpty();
    }

    @Test
    void theTranscriptKeepsPrintedTextAndNewLinesAndBackspaceRemovesTheLastCharacter() {
        TerminalScreen screen = write(vt(2, 4), "ab\rcdefg\n\bh\bij\u001b[31mk");
        assertThat(screen.transcript()).isEqualTo("abcdefg\nijk");
    }

    @Test
    void theTranscriptKeepsTheLastFourThousandCharacters() {
        TerminalScreen screen = write(vt(1, 10), "x".repeat(5000) + "end");
        assertThat(screen.transcript()).hasSize(TerminalScreen.TRANSCRIPT_LIMIT).endsWith("xend");
    }

    @Test
    void everyByteGoesToTheByteLogIncludingControlsAndUndefinedOnes() {
        TerminalScreen screen = write(vt(1, 10), "a\u001b");
        screen.writeUndefined(7);
        assertThat(screen.byteLog().since(0)).extracting(ByteLog.Entry::value)
                .containsExactly((int) 'a', 0x1B, ByteLog.UNDEFINED);
        assertThat(screen.byteLog().since(0).get(2).time()).isEqualTo(7);
    }

    @Test
    void theFrameIsACopyThatTheScreenNeverChanges() {
        TerminalScreen screen = write(vt(2, 4), "ab");
        TerminalFrame frame = screen.frame();
        write(screen, "cd\nxy");
        assertThat(frame.text()).isEqualTo("ab\n");
        List<TerminalCell[]> cells = frame.cells();
        cells.get(0)[0] = TerminalCell.BLANK;
        assertThat(frame.cell(0, 0).codePoint()).isEqualTo('a');
        assertThat(screen.frame().cell(0, 0).codePoint()).isEqualTo('a');
    }

    @Test
    void theHistoryIsACopy() {
        TerminalScreen screen = write(new TerminalScreen(withScrollback(1, 5)), "a\nb");
        screen.history(5).get(0)[0] = TerminalCell.BLANK;
        assertThat(rowText(screen.history(5).get(0))).isEqualTo("a");
    }

    @Test
    void theFrameIsBuiltOncePerVersion() {
        TerminalScreen screen = write(vt(2, 4), "ab");
        assertThat(screen.frame()).isSameAs(screen.frame());
        TerminalFrame before = screen.frame();
        write(screen, "c");
        assertThat(screen.frame()).isNotSameAs(before);
        assertThat(screen.frame().version()).isEqualTo(screen.version()).isGreaterThan(before.version());
    }

    @Test
    void anIgnoredByteDoesNotChangeTheVersion() {
        TerminalScreen logisim = write(logisim(1, 10), "a");
        long logisimVersion = logisim.version();
        write(logisim, "\u0001\u001b\t");
        assertThat(logisim.version()).isEqualTo(logisimVersion);

        TerminalScreen vt = write(vt(1, 10), "a");
        long vtVersion = vt.version();
        write(vt, "\u001b[31");
        vt.write(0xC3, 9);
        assertThat(vt.version()).as("half a sequence and half a character show nothing yet").isEqualTo(vtVersion);
    }

    @Test
    void theLastWrittenCellIsTrackedAndMovesUpWhenTheScreenScrolls() {
        TerminalScreen screen = write(vt(2, 4), "ab");
        assertThat(screen.frame().lastWritten()).contains(new CellPosition(0, 1));
        assertThat(screen.frame().lastWrittenSerial()).isEqualTo(2);
        write(screen, "\nc\n");
        assertThat(screen.frame().lastWritten()).contains(new CellPosition(0, 0));
        write(screen, "\n");
        assertThat(screen.frame().lastWritten()).isEmpty();
        assertThat(screen.printedCount()).isEqualTo(3);
        assertThat(screen.frame().printedCount()).isEqualTo(3);
    }

    @Test
    void adoptionKeepsTheTextHistoryTranscriptAndLogAndChangesTheVersion() {
        TerminalScreen screen = write(new TerminalScreen(withScrollback(2, 10)), "a\nb\nc");
        TerminalScreen adopted = screen.adoptedWith(new TerminalSettings(TerminalMode.LOGISIM, 3, 10, 100, true, 7,
                TerminalEncoding.UTF8, true, true, true, 8));
        assertThat(rows(adopted)).containsExactly("b", "c", "");
        assertThat(adopted.history(10)).extracting(TerminalScreenTest::rowText).containsExactly("a");
        assertThat(adopted.transcript()).isEqualTo("a\nb\nc");
        assertThat(adopted.byteLog().lastSequence()).isEqualTo(5);
        assertThat(adopted.version()).isGreaterThan(screen.version());
        assertThat(adopted.settings().mode()).isEqualTo(TerminalMode.LOGISIM);
    }

    @Test
    void rowsRemovedByAdoptionGoToTheHistory() {
        TerminalScreen screen = write(new TerminalScreen(withScrollback(4, 10)), "a\nb\nc\nd");
        TerminalScreen adopted = screen.adoptedWith(withScrollback(2, 10));
        assertThat(rows(adopted)).containsExactly("c", "d");
        assertThat(adopted.history(10)).extracting(TerminalScreenTest::rowText).containsExactly("a", "b");
    }

    @Test
    void aMillionPrintedBytesFinishQuickly() {
        TerminalScreen screen = new TerminalScreen(withScrollback(24, 1000));
        long start = System.nanoTime();
        for (int index = 0; index < 1_000_000; index++) {
            screen.write(index % 81 == 80 ? '\n' : 'a' + index % 26, index);
        }
        long elapsedMilliseconds = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMilliseconds).as("a loose guard against a grid copy per byte").isLessThan(10_000);
        assertThat(screen.printedCount()).isGreaterThan(900_000);
    }

    @Test
    void scrollingAndClearingTheWholeScreenCountAsJumpsButTypingAndPartialErasesDoNot() {
        TerminalScreen screen = vt(2, 10);
        write(screen, "ab\r\ncd\b\u001b[K");
        assertThat(screen.frame().scrollOrClearCount()).as("typing, a new line on screen, an erased line end")
                .isZero();
        write(screen, "\r\n");
        assertThat(screen.frame().scrollOrClearCount()).as("a scroll").isEqualTo(1);
        write(screen, "\u000c");
        assertThat(screen.frame().scrollOrClearCount()).as("form feed").isEqualTo(2);
        write(screen, "x\u001b[2J");
        assertThat(screen.frame().scrollOrClearCount()).as("ED 2").isEqualTo(3);
        screen.clear();
        assertThat(screen.frame().scrollOrClearCount()).as("CLR").isEqualTo(4);
        write(screen, "\u001bc");
        assertThat(screen.frame().scrollOrClearCount()).as("RIS").isEqualTo(5);
        assertThat(screen.adoptedWith(TerminalSettings.vt(3, 12)).frame().scrollOrClearCount())
                .as("kept by adoption").isEqualTo(5);
    }
}
