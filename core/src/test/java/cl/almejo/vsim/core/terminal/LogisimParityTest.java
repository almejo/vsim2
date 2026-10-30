package cl.almejo.vsim.core.terminal;

import org.junit.jupiter.api.Test;

import static cl.almejo.vsim.core.terminal.ScreenWriter.logisim;
import static cl.almejo.vsim.core.terminal.ScreenWriter.rows;
import static cl.almejo.vsim.core.terminal.ScreenWriter.write;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Logisim mode gives exactly the text of Logisim 2.7's {@code TtyState} (std/io/TtyState.java): every expectation
 * here was worked out by following that class by hand on the same bytes.
 */
class LogisimParityTest {

    @Test
    void printableTextIsAppendedOnTheFirstRow() {
        TerminalScreen screen = write(logisim(3, 10), "hola");
        assertThat(rows(screen)).containsExactly("hola", "", "");
        assertThat(screen.frame().cursorRow()).isZero();
        assertThat(screen.frame().cursorColumn()).isEqualTo(4);
    }

    @Test
    void carriageReturnAndLineFeedBothStartANewLine() {
        assertThat(rows(write(logisim(4, 10), "a\rb\nc"))).containsExactly("a", "b", "c", "");
    }

    @Test
    void carriageReturnThenLineFeedLeavesAnEmptyLine() {
        assertThat(rows(write(logisim(4, 10), "a\r\nb"))).containsExactly("a", "", "b", "");
    }

    @Test
    void backspaceErasesTheLastCharacterOfTheRow() {
        TerminalScreen screen = write(logisim(2, 10), "abc\b");
        assertThat(rows(screen)).containsExactly("ab", "");
        assertThat(screen.frame().cursorColumn()).isEqualTo(2);
    }

    @Test
    void backspaceAtTheStartOfARowNeverGoesUp() {
        assertThat(rows(write(logisim(3, 10), "ab\n\b\bc"))).containsExactly("ab", "c", "");
    }

    @Test
    void formFeedClearsTheScreenAndHomesTheCursor() {
        TerminalScreen screen = write(logisim(3, 10), "abc\ndef\fx");
        assertThat(rows(screen)).containsExactly("x", "", "");
        assertThat(screen.frame().cursorRow()).isZero();
        assertThat(screen.frame().cursorColumn()).isEqualTo(1);
    }

    @Test
    void theEightyFirstCharacterInEightyColumnsGoesToTheNextRow() {
        TerminalScreen screen = write(logisim(3, 80), "x".repeat(81));
        assertThat(rows(screen)).containsExactly("x".repeat(80), "x", "");
        assertThat(screen.frame().cursorRow()).isEqualTo(1);
        assertThat(screen.frame().cursorColumn()).isEqualTo(1);
    }

    @Test
    void aFullRowThenANewLineDoesNotLeaveAnEmptyRow() {
        assertThat(rows(write(logisim(3, 5), "abcde\nf"))).containsExactly("abcde", "f", "");
    }

    @Test
    void backspaceOnAFullRowErasesItsLastCharacter() {
        TerminalScreen screen = write(logisim(2, 5), "abcde\b");
        assertThat(rows(screen)).containsExactly("abcd", "");
        assertThat(screen.frame().cursorColumn()).isEqualTo(4);
    }

    @Test
    void twentyFiveLinesInTwentyFourRowsScrollTheFirstAway() {
        StringBuilder text = new StringBuilder();
        for (int line = 0; line < 25; line++) {
            text.append(line == 0 ? "" : "\n").append("line").append(line);
        }
        TerminalScreen screen = write(logisim(24, 80), text.toString());
        assertThat(rows(screen).get(0)).isEqualTo("line1");
        assertThat(rows(screen).get(23)).isEqualTo("line24");
        assertThat(screen.frame().cursorRow()).isEqualTo(23);
    }

    @Test
    void escapeIsIgnoredAndTheRestOfTheSequenceShowsAsText() {
        assertThat(rows(write(logisim(1, 20), "\u001b[31mred"))).containsExactly("[31mred");
    }

    @Test
    void tabsBellsAndOtherControlBytesAreIgnored() {
        TerminalScreen screen = write(logisim(1, 20), "a\tb\u0001c\u007fd\u0007e");
        assertThat(rows(screen)).containsExactly("abcde");
    }

    @Test
    void anUndefinedByteWritesAQuestionMark() {
        TerminalScreen screen = write(logisim(1, 10), "a");
        screen.writeUndefined(5);
        assertThat(rows(screen)).containsExactly("a?");
    }

    @Test
    void sevenBitDataIgnoresTheEighthBit() {
        TerminalScreen screen = logisim(1, 10);
        screen.write(0xC1, 0);
        assertThat(rows(screen)).containsExactly("A");
    }

    @Test
    void clearEmptiesTheScreen() {
        TerminalScreen screen = write(logisim(2, 10), "ab\ncd");
        screen.clear();
        assertThat(rows(screen)).containsExactly("", "");
        assertThat(screen.frame().cursorColumn()).isZero();
    }

    @Test
    void addedRowsKeepTheTextAndTheCursor() {
        TerminalScreen screen = write(logisim(2, 10), "a\nb").adoptedWith(TerminalSettings.logisim(4, 10));
        assertThat(rows(screen)).containsExactly("a", "b", "", "");
        write(screen, "c\nd");
        assertThat(rows(screen)).containsExactly("a", "bc", "d", "");
    }

    @Test
    void removedRowsKeepTheTopWhenTheFilledRowsFit() {
        TerminalScreen screen = write(logisim(4, 10), "a").adoptedWith(TerminalSettings.logisim(2, 10));
        assertThat(rows(screen)).containsExactly("a", "");
        assertThat(screen.frame().cursorRow()).isZero();
    }

    @Test
    void removedRowsKeepTheBottomRowsWhenTheCursorIsLower() {
        TerminalScreen screen = write(logisim(4, 10), "a\nb\nc\nd").adoptedWith(TerminalSettings.logisim(2, 10));
        assertThat(rows(screen)).containsExactly("c", "d");
        assertThat(screen.frame().cursorRow()).isEqualTo(1);
        assertThat(screen.frame().cursorColumn()).isEqualTo(1);
    }

    @Test
    void removedColumnsTrimLongRowsAndTheNextCharacterGoesToTheNextRow() {
        TerminalScreen screen = write(logisim(3, 10), "abcdefgh\nabcdefgh")
                .adoptedWith(TerminalSettings.logisim(3, 5));
        assertThat(rows(screen)).containsExactly("abcde", "abcde", "");
        write(screen, "x");
        assertThat(rows(screen)).containsExactly("abcde", "abcde", "x");
    }

    @Test
    void addedColumnsLetAFullRowGrow() {
        TerminalScreen screen = write(logisim(2, 10), "abcdefghij").adoptedWith(TerminalSettings.logisim(2, 15));
        write(screen, "k");
        assertThat(rows(screen)).containsExactly("abcdefghijk", "");
    }
}
