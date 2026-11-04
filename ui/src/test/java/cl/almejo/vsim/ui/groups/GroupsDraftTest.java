package cl.almejo.vsim.ui.groups;

import cl.almejo.vsim.core.component.ParameterProblem;
import cl.almejo.vsim.core.component.standard.BitGroups;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The «Grupos» window's editable groups: pins of ranges that may be wrong for a while, and what is wrong with them. */
class GroupsDraftTest {

    private static GroupsDraft draft(String text, int width) {
        return GroupsDraft.of(BitGroups.parse(text, width));
    }

    private static List<String> keys(GroupsDraft draft) {
        return draft.problems().stream().map(ParameterProblem::messageKey).toList();
    }

    @Test
    void aDraftShowsThePinsOfItsGroupsAsRanges() {
        GroupsDraft draft = draft("7-11,25-31|15-19", 32);

        assertThat(draft.pinCount()).isEqualTo(2);
        assertThat(draft.ranges(0)).containsExactly(new BitRange(7, 11), new BitRange(25, 31));
        assertThat(draft.label(0)).isEqualTo("7-11,25-31");
        assertThat(draft.bitsOf(1)).containsExactly(15, 16, 17, 18, 19);
        assertThat(draft.text()).isEqualTo("7-11,25-31|15-19");
        assertThat(draft.unusedBits()).startsWith(0, 1, 2, 3, 4, 5, 6, 12, 13, 14).hasSize(15);
        assertThat(draft.groups()).contains(BitGroups.parse("7-11,25-31|15-19", 32));
    }

    @Test
    void oneBitPerPinShowsEveryPinInTheText() {
        GroupsDraft draft = draft("", 4);

        assertThat(draft.text()).isEqualTo("0|1|2|3");
        assertThat(draft.groups().orElseThrow().canonical()).isEmpty();
    }

    @Test
    void addingAPinGivesItTheFirstUnusedBitAndSelectsIt() {
        GroupsDraft draft = draft("0-2|4-7", 8);

        draft.addPin();

        assertThat(draft.pinCount()).isEqualTo(3);
        assertThat(draft.ranges(2)).containsExactly(new BitRange(3, 3));
        assertThat(draft.selectedPin()).isEqualTo(2);
        assertThat(draft.text()).isEqualTo("0-2|4-7|3");
    }

    @Test
    void removingAPinLeavesItsBitsUnusedAndKeepsASelectedPin() {
        GroupsDraft draft = draft("0-3|4-7", 8);
        draft.select(1);

        draft.removePin(1);

        assertThat(draft.pinCount()).isEqualTo(1);
        assertThat(draft.selectedPin()).isZero();
        assertThat(draft.unusedBits()).containsExactly(4, 5, 6, 7);
    }

    @Test
    void theLastPinCannotBeRemoved() {
        GroupsDraft draft = draft("0-7", 8);

        assertThat(draft.canRemovePin()).isFalse();
        draft.removePin(0);

        assertThat(draft.pinCount()).isEqualTo(1);
    }

    @Test
    void aRangeIsAddedEditedAndRemoved() {
        GroupsDraft draft = draft("7-11|15-19", 32);

        draft.addRange(0);
        assertThat(draft.ranges(0)).containsExactly(new BitRange(7, 11), new BitRange(0, 0));
        draft.setRange(0, 1, new BitRange(25, 31));
        assertThat(draft.label(0)).isEqualTo("7-11,25-31");
        draft.removeRange(0, 0);

        assertThat(draft.ranges(0)).containsExactly(new BitRange(25, 31));
    }

    @Test
    void aRangeTypedBackwardsCountsFromItsLowEnd() {
        GroupsDraft draft = draft("0-7", 8);

        draft.setRange(0, 0, new BitRange(5, 2));

        assertThat(draft.bitsOf(0)).containsExactly(2, 3, 4, 5);
        assertThat(draft.text()).isEqualTo("2-5");
    }

    @Test
    void aRangeIsKeptInsideTheBus() {
        GroupsDraft draft = draft("0-7", 8);

        draft.setRange(0, 0, new BitRange(-3, 40));

        assertThat(draft.ranges(0)).containsExactly(new BitRange(0, 7));
    }

    @Test
    void clickingABitMovesItToTheSelectedPinOrTakesItOut() {
        GroupsDraft draft = draft("0-3|4-7", 8);
        draft.select(0);

        draft.toggleBit(5);
        assertThat(draft.text()).isEqualTo("0-3,5|4,6-7");

        draft.toggleBit(5);
        assertThat(draft.text()).isEqualTo("0-3|4,6-7");
        assertThat(draft.unusedBits()).containsExactly(5);
    }

    @Test
    void aBitInTwoPinsIsAProblemThatBlocksTheGroups() {
        GroupsDraft draft = draft("0-3|4-7", 8);

        draft.setRange(1, 0, new BitRange(3, 7));

        assertThat(draft.ownersOf(3)).containsExactly(0, 1);
        assertThat(draft.problems()).containsExactly(new ParameterProblem("groups", "groups.bitInTwoPins",
                List.of(3)));
        assertThat(draft.groups()).isEmpty();
    }

    @Test
    void severalBitsInTwoPinsAreToldTogether() {
        GroupsDraft draft = draft("0-3|4-7", 8);

        draft.setRange(1, 0, new BitRange(2, 7));

        assertThat(draft.problems()).containsExactly(new ParameterProblem("groups", "groups.bitsInTwoPins",
                List.of("2-3")));
    }

    @Test
    void anEmptyPinIsAProblem() {
        GroupsDraft draft = draft("0-3|4-7", 8);

        assertThat(draft.readText("0-3||4-7")).isTrue();

        assertThat(draft.problems()).containsExactly(new ParameterProblem("groups", "groups.emptyPin",
                List.of(2)));
        assertThat(draft.groups()).isEmpty();
    }

    @Test
    void aBitBeyondTheBusTypedInTheTextIsAProblem() {
        GroupsDraft draft = draft("0-7", 8);

        assertThat(draft.readText("0-3|4-9")).isTrue();

        assertThat(keys(draft)).containsExactly("groups.bitOutOfRange");
        assertThat(draft.problems().get(0).arguments()).containsExactly(9, 8);
    }

    @Test
    void moreThanThirtyTwoPinsIsAProblem() {
        GroupsDraft draft = draft("", 32);

        draft.addPin();

        assertThat(keys(draft)).contains("groups.tooManyPins");
    }

    @Test
    void readableTextReplacesThePinsAndUnreadableTextChangesNothing() {
        GroupsDraft draft = draft("0-7", 8);

        assertThat(draft.readText(" 4 - 7 , 0 | 1-3 ")).isTrue();
        assertThat(draft.text()).isEqualTo("0,4-7|1-3");
        assertThat(draft.selectedPin()).isZero();

        assertThat(draft.readText("a|3-")).isFalse();
        assertThat(draft.text()).isEqualTo("0,4-7|1-3");
    }

    /** Fix round 3: {@code 11-7|3-0} read right but the table kept «3 – 0». */
    @Test
    void rangesWrittenFromTheHighEndAreReadLowToHigh() {
        GroupsDraft draft = draft("0-15", 16);

        assertThat(draft.readText("11-7|3-0")).isTrue();

        assertThat(draft.ranges(0)).containsExactly(new BitRange(7, 11));
        assertThat(draft.ranges(1)).containsExactly(new BitRange(0, 3));
        assertThat(draft.text()).isEqualTo("7-11|0-3");
    }

    @Test
    void blankTextIsOneBitPerPin() {
        GroupsDraft draft = draft("0-7", 8);

        assertThat(draft.readText("  ")).isTrue();

        assertThat(draft.pinCount()).isEqualTo(8);
    }
}
