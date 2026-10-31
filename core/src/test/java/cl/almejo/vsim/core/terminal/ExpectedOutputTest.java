package cl.almejo.vsim.core.terminal;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Salida esperada» (spec §8.9): what arrived against what was expected. The window and the AI's
 * {@code vsim_read_terminal} both use this comparison, so they never disagree.
 */
class ExpectedOutputTest {

    @Test
    void theSameTextIsEqualAlsoWithDifferentTrailingSpaceOrLineEnds() {
        assertThat(ExpectedOutput.compare("? 5\nfact(5)=120\n", "? 5\nfact(5)=120\n"))
                .isEqualTo(new ExpectedOutput.Equal());
        assertThat(ExpectedOutput.compare("? 5\nfact(5)=120", "? 5\nfact(5)=120\n "))
                .as("trailing white space does not count, as for the AI").isEqualTo(new ExpectedOutput.Equal());
        assertThat(ExpectedOutput.compare("uno\r\ndos", "uno\ndos")).isEqualTo(new ExpectedOutput.Equal());
        assertThat(ExpectedOutput.matches("ab \n", "ab")).isTrue();
    }

    @Test
    void aBeginningOfTheExpectedTextIsGoingWell() {
        assertThat(ExpectedOutput.compare("? 5\nfact(5)=120\n!", "? 5\nfact"))
                .isEqualTo(new ExpectedOutput.GoingWell(8, 17));
        assertThat(ExpectedOutput.matches("? 5\nfact(5)=120\n!", "? 5\nfact")).isFalse();
    }

    @Test
    void theFirstDifferenceIsCountedFromOne() {
        assertThat(ExpectedOutput.compare("fact(5)=120", "fact(5)=220"))
                .isEqualTo(new ExpectedOutput.Differs(9, OptionalInt.of('1'), OptionalInt.of('2')));
        assertThat(ExpectedOutput.compare("a", "ab"))
                .isEqualTo(new ExpectedOutput.Differs(2, OptionalInt.empty(), OptionalInt.of('b')));
    }

    @Test
    void charactersAreCountedNotUtf16Units() {
        assertThat(ExpectedOutput.compare("ñ😀x", "ñ😀")).isEqualTo(new ExpectedOutput.GoingWell(2, 3));
        assertThat(ExpectedOutput.compare("😀a", "😀b"))
                .isEqualTo(new ExpectedOutput.Differs(2, OptionalInt.of('a'), OptionalInt.of('b')));
    }
}
