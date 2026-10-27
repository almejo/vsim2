package cl.almejo.vsim.core.memory;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryContentsTest {

    @Test
    void emptyReadsZeroEverywhere() {
        MemoryContents empty = MemoryContents.empty();

        assertThat(empty.word(0)).isZero();
        assertThat(empty.word(16_777_215)).isZero();
        assertThat(empty.highestAddress()).isEqualTo(-1);
        assertThat(empty.count()).isZero();
        assertThat(empty.widestBits()).isZero();
        assertThat(empty.encode()).isEmpty();
        assertThat(MemoryContents.decode("")).isEqualTo(empty);
    }

    @Test
    void aSixteenMegaSparseMemoryIsSmallAndFast() {
        MemoryContents contents = MemoryContents.empty().with(0, 0x3A10).with(8_388_608, 0xFFFFFFFFFFFFFFFFL)
                .with(16_777_215, 1);

        String text = contents.encode();
        long start = System.nanoTime();
        MemoryContents back = MemoryContents.decode(text);
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(text.length()).isLessThan(100);
        assertThat(millis).isLessThan(50);
        assertThat(back).isEqualTo(contents);
        assertThat(back.word(8_388_608)).isEqualTo(-1L);
    }

    @Test
    void settingZeroRemovesTheWord() {
        MemoryContents contents = MemoryContents.empty().with(300, 5);

        MemoryContents cleared = contents.with(300, 0);

        assertThat(cleared).isEqualTo(MemoryContents.empty());
        assertThat(cleared.count()).isZero();
        assertThat(contents.word(300)).as("immutable").isEqualTo(5);
    }

    @Test
    void highestAddressCountAndWidestBits() {
        MemoryContents contents = MemoryContents.empty().with(3, 1).with(700, 0x1FF).with(2, 0);

        assertThat(contents.highestAddress()).isEqualTo(700);
        assertThat(contents.count()).isEqualTo(2);
        assertThat(contents.widestBits()).isEqualTo(9);
        assertThat(MemoryContents.empty().with(0, -1L).widestBits()).isEqualTo(64);
    }

    @Test
    void equalityIgnoresConstructionOrder() {
        MemoryContents forward = MemoryContents.empty().with(1, 1).with(1000, 2).with(5, 3);
        MemoryContents backward = MemoryContents.empty().with(5, 3).with(1000, 2).with(1, 1).with(77, 9).with(77, 0);

        assertThat(forward).isEqualTo(backward).hasSameHashCodeAs(backward);
        assertThat(forward).isNotEqualTo(forward.with(1, 2));
    }

    @Test
    void withAllAddsSeveralWordsAtOnce() {
        TreeMap<Long, Long> words = new TreeMap<>();
        words.put(10L, 1L);
        words.put(11L, 2L);
        words.put(5L, 0L);

        MemoryContents contents = MemoryContents.empty().with(5, 9).withAll(words);

        assertThat(contents.word(5)).isZero();
        assertThat(contents.word(10)).isEqualTo(1);
        assertThat(contents.word(11)).isEqualTo(2);
    }

    @Test
    void forEachNonZeroGoesInAscendingOrder() {
        MemoryContents contents = MemoryContents.empty().with(900, 1).with(2, 2).with(300, 3);
        List<Long> addresses = new ArrayList<>();

        contents.forEachNonZero((address, value) -> addresses.add(address));

        assertThat(addresses).containsExactly(2L, 300L, 900L);
    }

    @Test
    void encodeIsCompactRunsAndRoundTrips() {
        MemoryContents contents = MemoryContents.empty().with(0, 0x3A10).with(1, 0xC601).with(0x10, 5);

        assertThat(contents.encode()).isEqualTo("@0 3A10 C601 @10 5");
        assertThat(MemoryContents.decode(contents.encode())).isEqualTo(contents);
    }

    @Test
    void decodeRejectsGarbage() {
        assertThatThrownBy(() -> MemoryContents.decode("@0 XYZ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MemoryContents.decode("12")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MemoryContents.decode("@-1 1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mutableCopyDoesNotAffectTheOriginal() {
        MemoryContents original = MemoryContents.empty().with(1, 1);
        MutableMemoryContents mutable = new MutableMemoryContents(original);

        mutable.set(1, 0);
        mutable.set(2, 7);

        assertThat(original.word(1)).isEqualTo(1);
        assertThat(mutable.word(1)).isZero();
        assertThat(mutable.snapshot()).isEqualTo(MemoryContents.empty().with(2, 7));
    }
}
