package cl.almejo.vsim.core.memory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link MutableMemoryContents#fill}: whole pages, never a boxed entry per address. */
class MutableMemoryContentsFillTest {

    private static final long SIXTEEN_MEGA = 16_777_216L;

    @Test
    void fillingASixteenMegaMemoryWithZeroIsInstantAndLeavesItEmpty() {
        MutableMemoryContents contents = new MutableMemoryContents(MemoryContents.empty().with(5, 9).with(SIXTEEN_MEGA - 1, 3));

        long start = System.nanoTime();
        contents.fill(0, SIXTEEN_MEGA - 1, 0);
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(millis).isLessThan(50);
        assertThat(contents.snapshot()).isEqualTo(MemoryContents.empty());
    }

    @Test
    void fillingWritesTheValueInTheRangeOnlyAcrossPartialAndWholePages() {
        MutableMemoryContents contents = new MutableMemoryContents(MemoryContents.empty().with(0, 1).with(2000, 2));

        contents.fill(10, 1000, 0xAB);

        assertThat(contents.word(9)).isZero();
        assertThat(contents.word(10)).isEqualTo(0xAB);
        assertThat(contents.word(500)).isEqualTo(0xAB);
        assertThat(contents.word(1000)).isEqualTo(0xAB);
        assertThat(contents.word(1001)).isZero();
        assertThat(contents.word(0)).isEqualTo(1);
        assertThat(contents.word(2000)).isEqualTo(2);
    }

    @Test
    void zeroInsideTheRangeClearsWordsAndKeepsTheRest() {
        MutableMemoryContents contents = new MutableMemoryContents(
                MemoryContents.empty().with(1, 1).with(300, 2).with(600, 3).with(900, 4));

        contents.fill(250, 700, 0);

        assertThat(contents.snapshot()).isEqualTo(MemoryContents.empty().with(1, 1).with(900, 4));
    }

    @Test
    void aFillDoesNotChangeASnapshotTakenBefore() {
        MutableMemoryContents contents = new MutableMemoryContents(MemoryContents.empty().with(300, 2));
        MemoryContents before = contents.snapshot();

        contents.fill(0, 600, 7);

        assertThat(before).isEqualTo(MemoryContents.empty().with(300, 2));
        assertThat(contents.word(300)).isEqualTo(7);
    }

    @Test
    void anInvertedOrNegativeRangeIsRefused() {
        MutableMemoryContents contents = new MutableMemoryContents();

        assertThatThrownBy(() -> contents.fill(5, 4, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> contents.fill(-1, 4, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
