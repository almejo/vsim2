package cl.almejo.vsim.core.terminal;

import cl.almejo.vsim.core.terminal.ColorReference.PaletteIndex;
import cl.almejo.vsim.core.terminal.ColorReference.Rgb;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static cl.almejo.vsim.core.terminal.ScreenWriter.rows;
import static cl.almejo.vsim.core.terminal.ScreenWriter.teletype;
import static cl.almejo.vsim.core.terminal.ScreenWriter.vt;
import static cl.almejo.vsim.core.terminal.ScreenWriter.write;
import static org.assertj.core.api.Assertions.assertThat;

/** VT100-xterm mode: the control bytes of spec §5 and the ECMA-48 / xterm sequences of spec §6. */
class ControlSequenceTest {

    private static final String ESCAPE = "\u001b";
    private static final String CONTROL_SEQUENCE = ESCAPE + "[";

    private static TerminalScreen filled() {
        return write(vt(4, 6), "aaaaaa" + "bbbbbb" + "cccccc" + "dddddd");
    }

    private static int cursorRow(TerminalScreen screen) {
        return screen.frame().cursorRow();
    }

    private static int cursorColumn(TerminalScreen screen) {
        return screen.frame().cursorColumn();
    }

    private static CellStyle styleAt(TerminalScreen screen, int row, int column) {
        return screen.frame().cell(row, column).style();
    }

    @Test
    void theLastColumnWaitsForTheNextCharacterBeforeWrapping() {
        TerminalScreen screen = write(vt(2, 5), "abcde");
        assertThat(cursorRow(screen)).isZero();
        assertThat(cursorColumn(screen)).isEqualTo(4);
        write(screen, "\rx");
        assertThat(rows(screen)).containsExactly("xbcde", "");
        write(screen, "bcdef");
        assertThat(rows(screen)).containsExactly("xbcde", "f");
    }

    @Test
    void withoutWrappingCharactersPastTheLastColumnAreDropped() {
        TerminalSettings settings = new TerminalSettings(TerminalMode.VT, 2, 5, 0, false, 8, TerminalEncoding.UTF8,
                true, false, true, 8);
        TerminalScreen screen = write(new TerminalScreen(settings), "abcdefgh");
        assertThat(rows(screen)).containsExactly("abcde", "");
    }

    @Test
    void lineFeedAlsoReturnsWithLineFeedReturns() {
        assertThat(rows(write(vt(3, 10), "ab\ncd"))).containsExactly("ab", "cd", "");
    }

    @Test
    void lineFeedOnlyMovesDownWithoutLineFeedReturns() {
        TerminalSettings settings = new TerminalSettings(TerminalMode.VT, 3, 10, 0, true, 8, TerminalEncoding.UTF8,
                false, false, true, 8);
        assertThat(rows(write(new TerminalScreen(settings), "ab\ncd\r\nef")))
                .containsExactly("ab", "  cd", "ef");
    }

    @Test
    void carriageReturnGoesBackToTheStartOfTheRow() {
        assertThat(rows(write(vt(2, 10), "abc\rx"))).containsExactly("xbc", "");
    }

    @Test
    void backspaceMovesLeftWithoutErasing() {
        TerminalScreen screen = write(vt(1, 10), "abc\b\bx");
        assertThat(rows(screen)).containsExactly("axc");
    }

    @Test
    void backspaceOnAFullRowStepsBackOntoTheLastCharacter() {
        assertThat(rows(write(vt(1, 5), "abcde\bX"))).containsExactly("abcdX");
    }

    @Test
    void backspaceErasesWithBackspaceErases() {
        TerminalSettings settings = new TerminalSettings(TerminalMode.VT, 1, 10, 0, true, 8, TerminalEncoding.UTF8,
                true, true, true, 8);
        TerminalScreen screen = write(new TerminalScreen(settings), "abc\b");
        assertThat(rows(screen)).containsExactly("ab");
        assertThat(cursorColumn(screen)).isEqualTo(2);
    }

    @Test
    void formFeedClearsWithFormFeedClears() {
        assertThat(rows(write(vt(2, 10), "ab\ncd\fx"))).containsExactly("x", "");
    }

    @Test
    void formFeedIsALineFeedWithoutFormFeedClears() {
        assertThat(rows(write(teletype(3, 10), "ab\fcd"))).containsExactly("ab", "cd", "");
    }

    @Test
    void tabGoesToTheNextStopAndStopsAtTheLastColumn() {
        TerminalScreen screen = write(vt(1, 20), "ab\tc\t\t\td");
        assertThat(rows(screen)).containsExactly("ab      c          d");
    }

    @Test
    void tabStopsFollowTheTabWidth() {
        TerminalSettings settings = new TerminalSettings(TerminalMode.VT, 1, 20, 0, true, 8, TerminalEncoding.UTF8,
                true, false, true, 4);
        assertThat(rows(write(new TerminalScreen(settings), "a\tb\tc"))).containsExactly("a   b   c");
    }

    @Test
    void bellCountsAndChangesTheVersion() {
        TerminalScreen screen = vt(1, 10);
        long version = screen.version();
        write(screen, "\u0007\u0007");
        assertThat(screen.bellCount()).isEqualTo(2);
        assertThat(screen.frame().bellCount()).isEqualTo(2);
        assertThat(screen.version()).isNotEqualTo(version);
        assertThat(rows(screen)).containsExactly("");
    }

    @Test
    void cursorUpDownForwardAndBackMoveAndStopAtTheEdges() {
        TerminalScreen screen = write(vt(5, 10), CONTROL_SEQUENCE + "3B" + CONTROL_SEQUENCE + "4C");
        assertThat(cursorRow(screen)).isEqualTo(3);
        assertThat(cursorColumn(screen)).isEqualTo(4);
        write(screen, CONTROL_SEQUENCE + "A" + CONTROL_SEQUENCE + "2D");
        assertThat(cursorRow(screen)).isEqualTo(2);
        assertThat(cursorColumn(screen)).isEqualTo(2);
        write(screen, CONTROL_SEQUENCE + "99A" + CONTROL_SEQUENCE + "99D");
        assertThat(cursorRow(screen)).isZero();
        assertThat(cursorColumn(screen)).isZero();
        write(screen, CONTROL_SEQUENCE + "99B" + CONTROL_SEQUENCE + "99C");
        assertThat(cursorRow(screen)).isEqualTo(4);
        assertThat(cursorColumn(screen)).isEqualTo(9);
        write(screen, CONTROL_SEQUENCE + "0A");
        assertThat(cursorRow(screen)).as("a zero count moves one").isEqualTo(3);
    }

    @Test
    void cursorPositionIsOneBasedAndDefaultsToHome() {
        TerminalScreen screen = write(vt(5, 10), CONTROL_SEQUENCE + "3;7H");
        assertThat(cursorRow(screen)).isEqualTo(2);
        assertThat(cursorColumn(screen)).isEqualTo(6);
        write(screen, CONTROL_SEQUENCE + "2;4f");
        assertThat(cursorRow(screen)).isEqualTo(1);
        assertThat(cursorColumn(screen)).isEqualTo(3);
        write(screen, CONTROL_SEQUENCE + "H");
        assertThat(cursorRow(screen)).isZero();
        assertThat(cursorColumn(screen)).isZero();
        write(screen, CONTROL_SEQUENCE + ";5H");
        assertThat(cursorColumn(screen)).isEqualTo(4);
        write(screen, CONTROL_SEQUENCE + "99;99H");
        assertThat(cursorRow(screen)).isEqualTo(4);
        assertThat(cursorColumn(screen)).isEqualTo(9);
    }

    @Test
    void eraseInDisplayFromTheCursorToTheEnd() {
        TerminalScreen screen = write(filled(), CONTROL_SEQUENCE + "2;3H" + CONTROL_SEQUENCE + "J");
        assertThat(rows(screen)).containsExactly("aaaaaa", "bb", "", "");
        assertThat(cursorRow(screen)).isEqualTo(1);
        assertThat(cursorColumn(screen)).isEqualTo(2);
    }

    @Test
    void eraseInDisplayFromTheStartToTheCursor() {
        TerminalScreen screen = write(filled(), CONTROL_SEQUENCE + "2;3H" + CONTROL_SEQUENCE + "1J");
        assertThat(rows(screen)).containsExactly("", "   bbb", "cccccc", "dddddd");
    }

    @Test
    void eraseInDisplayTheWholeScreenKeepsTheCursorAndTheHistory() {
        TerminalScreen screen = write(filled(), "\n" + CONTROL_SEQUENCE + "2J");
        assertThat(rows(screen)).containsExactly("", "", "", "");
        assertThat(cursorRow(screen)).isEqualTo(3);
        assertThat(screen.history(10)).hasSize(1);
    }

    @Test
    void eraseInDisplayThreeAlsoClearsTheHistory() {
        TerminalScreen screen = write(filled(), "\n" + CONTROL_SEQUENCE + "3J");
        assertThat(rows(screen)).containsExactly("", "", "", "");
        assertThat(screen.history(10)).isEmpty();
    }

    @Test
    void eraseInLineToTheEndToTheStartAndWhole() {
        TerminalScreen screen = write(filled(), CONTROL_SEQUENCE + "1;3H" + CONTROL_SEQUENCE + "K"
                + CONTROL_SEQUENCE + "2;3H" + CONTROL_SEQUENCE + "1K"
                + CONTROL_SEQUENCE + "3;3H" + CONTROL_SEQUENCE + "2K");
        assertThat(rows(screen)).containsExactly("aa", "   bbb", "", "dddddd");
    }

    @Test
    void selectGraphicRenditionSetsAndResetsTheAttributes() {
        TerminalScreen screen = write(vt(1, 10), CONTROL_SEQUENCE + "1;4;7ma" + CONTROL_SEQUENCE + "22;24;27mb"
                + CONTROL_SEQUENCE + "2mc" + CONTROL_SEQUENCE + "0md");
        assertThat(styleAt(screen, 0, 0)).isEqualTo(new CellStyle(Optional.empty(), Optional.empty(), true, false,
                true, true));
        assertThat(styleAt(screen, 0, 1)).isEqualTo(CellStyle.PLAIN);
        assertThat(styleAt(screen, 0, 2).faint()).isTrue();
        assertThat(styleAt(screen, 0, 3)).isEqualTo(CellStyle.PLAIN);
    }

    @Test
    void anEmptySelectGraphicRenditionResets() {
        TerminalScreen screen = write(vt(1, 10), CONTROL_SEQUENCE + "1;31m" + CONTROL_SEQUENCE + "ma");
        assertThat(styleAt(screen, 0, 0)).isEqualTo(CellStyle.PLAIN);
    }

    @Test
    void theSixteenColoursAreStoredAsPaletteIndexes() {
        TerminalScreen screen = write(vt(1, 10), CONTROL_SEQUENCE + "31;42ma" + CONTROL_SEQUENCE + "97;105mb"
                + CONTROL_SEQUENCE + "39mc" + CONTROL_SEQUENCE + "49md");
        assertThat(styleAt(screen, 0, 0).foreground()).contains(new PaletteIndex(1));
        assertThat(styleAt(screen, 0, 0).background()).contains(new PaletteIndex(2));
        assertThat(styleAt(screen, 0, 1).foreground()).contains(new PaletteIndex(15));
        assertThat(styleAt(screen, 0, 1).background()).contains(new PaletteIndex(13));
        assertThat(styleAt(screen, 0, 2).foreground()).isEmpty();
        assertThat(styleAt(screen, 0, 2).background()).contains(new PaletteIndex(13));
        assertThat(styleAt(screen, 0, 3).background()).isEmpty();
    }

    @Test
    void boldDoesNotChangeTheStoredColourReference() {
        TerminalScreen screen = write(vt(1, 10), CONTROL_SEQUENCE + "1;34ma");
        assertThat(styleAt(screen, 0, 0).foreground()).contains(new PaletteIndex(4));
        assertThat(styleAt(screen, 0, 0).bold()).isTrue();
    }

    @Test
    void indexedAndTrueColoursAreStored() {
        TerminalScreen screen = write(vt(1, 10), CONTROL_SEQUENCE + "38;5;208;48;5;17ma"
                + CONTROL_SEQUENCE + "38;2;255;128;0;48;2;1;2;3mb");
        assertThat(styleAt(screen, 0, 0).foreground()).contains(new PaletteIndex(208));
        assertThat(styleAt(screen, 0, 0).background()).contains(new PaletteIndex(17));
        assertThat(styleAt(screen, 0, 1).foreground()).contains(new Rgb(0xFF8000));
        assertThat(styleAt(screen, 0, 1).background()).contains(new Rgb(0x010203));
    }

    @Test
    void erasingFillsWithTheCurrentBackground() {
        TerminalScreen screen = write(vt(1, 4), CONTROL_SEQUENCE + "44m" + CONTROL_SEQUENCE + "2K");
        assertThat(styleAt(screen, 0, 3).background()).contains(new PaletteIndex(4));
        assertThat(screen.frame().cell(0, 3).written()).isFalse();
    }

    @Test
    void saveAndRestoreTheCursorWithBothForms() {
        TerminalScreen screen = write(vt(5, 10), CONTROL_SEQUENCE + "2;3H" + ESCAPE + "7"
                + CONTROL_SEQUENCE + "5;5H" + ESCAPE + "8");
        assertThat(cursorRow(screen)).isEqualTo(1);
        assertThat(cursorColumn(screen)).isEqualTo(2);
        write(screen, CONTROL_SEQUENCE + "4;6H" + CONTROL_SEQUENCE + "s" + CONTROL_SEQUENCE + "H"
                + CONTROL_SEQUENCE + "u");
        assertThat(cursorRow(screen)).isEqualTo(3);
        assertThat(cursorColumn(screen)).isEqualTo(5);
    }

    @Test
    void hideAndShowTheCursor() {
        TerminalScreen screen = write(vt(1, 10), CONTROL_SEQUENCE + "?25l");
        assertThat(screen.frame().cursorVisible()).isFalse();
        write(screen, CONTROL_SEQUENCE + "?25h");
        assertThat(screen.frame().cursorVisible()).isTrue();
    }

    @Test
    void resetToInitialStateClearsTheScreenStyleAndCursor() {
        TerminalScreen screen = write(vt(2, 10), "ab" + CONTROL_SEQUENCE + "?25l" + CONTROL_SEQUENCE + "31m"
                + ESCAPE + "cx");
        assertThat(rows(screen)).containsExactly("x", "");
        assertThat(styleAt(screen, 0, 0)).isEqualTo(CellStyle.PLAIN);
        assertThat(screen.frame().cursorVisible()).isTrue();
    }

    @Test
    void unknownSequencesAreConsumedAndDropped() {
        TerminalScreen screen = write(vt(1, 20), "a" + CONTROL_SEQUENCE + "6n" + CONTROL_SEQUENCE + ">1c"
                + ESCAPE + "(B" + ESCAPE + "=b");
        assertThat(rows(screen)).containsExactly("ab");
    }

    @Test
    void operatingSystemCommandsEndWithBellOrStringTerminator() {
        TerminalScreen screen = write(vt(1, 20), "a" + ESCAPE + "]0;título\u0007b" + ESCAPE + "]2;x" + ESCAPE
                + "\\c");
        assertThat(rows(screen)).containsExactly("abc");
        assertThat(screen.bellCount()).as("the bell ends the command, it does not ring").isZero();
    }

    @Test
    void aSequenceLongerThanSixtyFourBytesIsAbandoned() {
        TerminalScreen screen = write(vt(1, 40), "a" + CONTROL_SEQUENCE + "1;".repeat(40) + "b");
        assertThat(rows(screen).get(0)).startsWith("a").endsWith("b").doesNotContain("[");
        TerminalScreen operatingSystem = write(vt(1, 40), ESCAPE + "]" + "x".repeat(70) + "yz");
        assertThat(rows(operatingSystem).get(0)).endsWith("xyz").hasSizeLessThan(10);
    }

    @Test
    void controlBytesInsideASequenceAreExecuted() {
        TerminalScreen screen = write(vt(3, 10), "a" + CONTROL_SEQUENCE + "\n2Cb");
        assertThat(rows(screen)).containsExactly("a", "  b", "");
    }

    @Test
    void cancelAbandonsASequence() {
        assertThat(rows(write(vt(1, 10), "a" + CONTROL_SEQUENCE + "3\u0018b"))).containsExactly("ab");
    }

    @Test
    void teletypeIgnoresUpwardMovesButMovesDown() {
        TerminalScreen screen = write(teletype(5, 10), "\n\n" + CONTROL_SEQUENCE + "2A" + CONTROL_SEQUENCE + "1;4H");
        assertThat(cursorRow(screen)).isEqualTo(2);
        assertThat(cursorColumn(screen)).isZero();
        write(screen, CONTROL_SEQUENCE + "5;4H");
        assertThat(cursorRow(screen)).isEqualTo(4);
        assertThat(cursorColumn(screen)).isEqualTo(3);
        write(screen, CONTROL_SEQUENCE + "5;1H");
        assertThat(cursorColumn(screen)).as("the same row is not upward").isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u001b[2;3H\u001b[1;31mX", "\u001b[3B\u001b[2CY", "\u001b]0;título\u0007Z",
            "\u001b7\u001b[4;4H\u001b8W", "ab\u001b[1Kc", "\u001b[38;2;10;20;30mV", "\u001b[?25lU"})
    void aSequenceArrivingOneByteAtATimeGivesTheSameScreen(String text) {
        TerminalScreen atOnce = write(vt(5, 10), text);
        TerminalScreen byteByByte = vt(5, 10);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        for (int index = 0; index < bytes.length; index++) {
            byteByByte.write(bytes[index] & 0xFF, index);
            assertThat(byteByByte.frame().text()).doesNotContain("[", "]");
        }
        assertThat(byteByByte.frame().text()).isEqualTo(atOnce.frame().text());
        assertThat(byteByByte.frame().cursorRow()).isEqualTo(atOnce.frame().cursorRow());
        assertThat(byteByByte.frame().cursorColumn()).isEqualTo(atOnce.frame().cursorColumn());
        assertThat(byteByByte.frame().cursorVisible()).isEqualTo(atOnce.frame().cursorVisible());
    }
}
