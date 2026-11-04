package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ParameterProblem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The groups of bits of a «Separador de bus» / «Unión de bus»: text, pins, labels, uniform groups and problems. */
class BitGroupsTest {

    private static final String SW_IMMEDIATE = "7-11,25-31";

    @Test
    void anEmptyTextIsOneBitPerPinWithTodaysNames() {
        BitGroups groups = BitGroups.parse("", 8);

        assertThat(groups.pinCount()).isEqualTo(8);
        assertThat(groups.bitsOf(5)).containsExactly(5);
        assertThat(groups.pinName(5)).isEqualTo("b5");
        assertThat(groups.label(5)).isEqualTo("5");
        assertThat(groups.canonical()).isEmpty();
        assertThat(groups).isEqualTo(BitGroups.onePerPin(8));
    }

    @Test
    void aTextOfOneBitPerPinInOrderIsCanonicallyEmpty() {
        assertThat(BitGroups.parse("0|1|2|3", 4).canonical()).isEmpty();
        assertThat(BitGroups.parse(" 0 | 1|2 |3 ", 4)).isEqualTo(BitGroups.onePerPin(4));
    }

    @Test
    void oneBitPerPinOutOfOrderOrMissingBitsIsNotEmpty() {
        assertThat(BitGroups.parse("1|0", 2).canonical()).isEqualTo("1|0");
        assertThat(BitGroups.parse("0|1|2|3", 8).canonical()).isEqualTo("0|1|2|3");
    }

    @Test
    void theCanonicalTextSortsAndMergesTheRangesOfEachPin() {
        BitGroups groups = BitGroups.parse(" 25-31 , 7-9,10-11 | 15-19 |20,21-24", 32);

        assertThat(groups.canonical()).isEqualTo(SW_IMMEDIATE + "|15-19|20-24");
        assertThat(BitGroups.parse(groups.canonical(), 32)).isEqualTo(groups);
    }

    @Test
    void theBitsOfAPinGoFromTheLowestToTheHighestBusBit() {
        BitGroups groups = BitGroups.parse("25-31,7-11", 32);

        assertThat(groups.bitsOf(0)).containsExactly(7, 8, 9, 10, 11, 25, 26, 27, 28, 29, 30, 31);
        assertThat(groups.widthOf(0)).isEqualTo(12);
        assertThat(groups.label(0)).isEqualTo(SW_IMMEDIATE);
        assertThat(groups.pinName(0)).isEqualTo("b" + SW_IMMEDIATE);
    }

    @Test
    void labelsNameASingleBitARangeAndSeveralRanges() {
        BitGroups groups = BitGroups.parse("5|0-3|" + SW_IMMEDIATE, 32);

        assertThat(List.of(groups.label(0), groups.label(1), groups.label(2)))
                .containsExactly("5", "0-3", SW_IMMEDIATE);
        assertThat(List.of(groups.pinName(0), groups.pinName(1), groups.pinName(2)))
                .containsExactly("b5", "b0-3", "b" + SW_IMMEDIATE);
    }

    @Test
    void uniformGroupsSplitTheBusInEqualContiguousPins() {
        BitGroups nibbles = BitGroups.uniform(8, 4);

        assertThat(nibbles.canonical()).isEqualTo("0-3|4-7");
        assertThat(nibbles.uniformSize()).isEqualTo(4);
        assertThat(BitGroups.parse("0-3|4-7", 8).uniformSize()).isEqualTo(4);
        assertThat(BitGroups.onePerPin(8).uniformSize()).isEqualTo(1);
        assertThat(BitGroups.uniform(8, 1)).isEqualTo(BitGroups.onePerPin(8));
    }

    @Test
    void aSizeThatDoesNotDivideTheWidthLeavesTheRemainderOnTheLastPin() {
        BitGroups groups = BitGroups.uniform(8, 3);

        assertThat(groups.canonical()).isEqualTo("0-2|3-5|6-7");
        assertThat(groups.uniformSize()).isEqualTo(3);
    }

    @Test
    void theRiscVInstructionFieldsAreNotUniform() {
        assertThat(BitGroups.parse("0-6|7-11|12-14|15-19|20-24|25-31", 32).uniformSize()).isZero();
        assertThat(BitGroups.parse("4-7|0-3", 8).uniformSize()).isZero();
        assertThat(BitGroups.parse("0-3", 8).uniformSize()).isZero();
    }

    @Test
    void bitsInNoPinAreUnused() {
        assertThat(BitGroups.parse(SW_IMMEDIATE, 32).unusedBits())
                .containsExactly(0, 1, 2, 3, 4, 5, 6, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24);
        assertThat(BitGroups.onePerPin(8).unusedBits()).isEmpty();
    }

    @Test
    void resizingUniformGroupsKeepsTheirSize() {
        assertThat(BitGroups.uniform(8, 4).resized(16).canonical()).isEqualTo("0-3|4-7|8-11|12-15");
        assertThat(BitGroups.onePerPin(8).resized(4)).isEqualTo(BitGroups.onePerPin(4));
        assertThat(BitGroups.uniform(8, 4).resized(2)).isEqualTo(BitGroups.uniform(2, 2));
    }

    @Test
    void resizingCustomGroupsDropsTheBitsBeyondTheWidthAndEmptyPins() {
        BitGroups groups = BitGroups.parse("12-14|0-6|" + SW_IMMEDIATE, 32);

        assertThat(groups.resized(10).canonical()).isEqualTo("0-6|7-9");
        assertThat(groups.resized(10).width()).isEqualTo(10);
        assertThat(BitGroups.parse("6-7", 8).resized(4)).isEqualTo(BitGroups.onePerPin(4));
    }

    @Test
    void aBitBeyondTheWidthIsAProblem() {
        assertThat(BitGroups.problem("0-3|4-8", 8)).contains(problem("groups.bitOutOfRange", 8, 8));
    }

    @Test
    void aBitInTwoPinsIsAProblem() {
        assertThat(BitGroups.problem("0-4|4-7", 8)).contains(problem("groups.bitInTwoPins", 4));
    }

    @Test
    void anEmptyPinIsAProblem() {
        assertThat(BitGroups.problem("0-3||4-7", 8)).contains(problem("groups.emptyPin", 2));
        assertThat(BitGroups.problem("0-3|", 8)).contains(problem("groups.emptyPin", 2));
    }

    @Test
    void moreThanThirtyTwoPinsIsAProblem() {
        String thirtyThree = "0|".repeat(32) + "0";

        assertThat(BitGroups.problem(thirtyThree, 32)).contains(problem("groups.tooManyPins", 32));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "0-", "-3", "0,,1", "1-2-3", "99999999999", "0;1"})
    void unreadableTextIsAProblem(String text) {
        assertThat(BitGroups.problem(text, 8)).contains(problem("groups.invalidText"));
    }

    @Test
    void aRangeWrittenFromItsHighestBitIsTheSameRange() {
        assertThat(BitGroups.parse("11-7,31-25", 32)).isEqualTo(BitGroups.parse(SW_IMMEDIATE, 32));
        assertThat(BitGroups.normalize("3-0|7-4")).isEqualTo("0-3|4-7");
    }

    @Test
    void aWidthOutsideTheBusesIsRefusedWhenAskingForProblemsToo() {
        assertThatThrownBy(() -> BitGroups.problem("0", 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("width");
        assertThatThrownBy(() -> BitGroups.problem("0", -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theSmallerLastPinOfEqualPinsIsTold() {
        assertThat(BitGroups.parse("0-6|7", 8).lastPinRemainder()).isEqualTo(1);
        assertThat(BitGroups.uniform(8, 3).lastPinRemainder()).isEqualTo(2);
        assertThat(BitGroups.parse("0-3|4-7", 8).lastPinRemainder()).isZero();
        assertThat(BitGroups.parse("0-3|5-7", 8).lastPinRemainder()).isZero();
    }

    @Test
    void validTextHasNoProblem() {
        assertThat(BitGroups.problem("", 8)).isEmpty();
        assertThat(BitGroups.problem(SW_IMMEDIATE + "|0-6", 32)).isEmpty();
        assertThat(BitGroups.problem("0-3,2", 8)).isEmpty();
    }

    @Test
    void parsingInvalidTextThrowsWithTheProblemKey() {
        assertThatThrownBy(() -> BitGroups.parse("0-4|4-7", 8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("groups.bitInTwoPins");
    }

    @Test
    void uniformRefusesASizeOutsideTheWidth() {
        assertThatThrownBy(() -> BitGroups.uniform(8, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BitGroups.uniform(8, 9)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theStoredTextIsCanonicalWithoutKnowingTheWidth() {
        assertThat(BitGroups.normalize(" 4-7 | 3,0-2 ")).isEqualTo("4-7|0-3");
        assertThat(BitGroups.normalize("")).isEmpty();
        assertThatThrownBy(() -> BitGroups.normalize("0|0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BitGroups.normalize("32")).isInstanceOf(IllegalArgumentException.class);
    }

    private static ParameterProblem problem(String key, Object... arguments) {
        return new ParameterProblem(StandardComponentTypes.GROUPS, key, List.of(arguments));
    }
}
