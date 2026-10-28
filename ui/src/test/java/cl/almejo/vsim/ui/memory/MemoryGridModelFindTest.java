package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.memory.MemoryContents;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryGridModelFindTest {

    private static MemoryGridModel model(String contents, int width, long words, MemoryRadix radix) {
        return new MemoryGridModel(MemoryContents.decode(contents), width, words, radix);
    }

    @Test
    void anEditedCellShowsItsNewInitialValueWhileTheGridShowsLiveWordsAndTheTooltipHasBoth() {
        MemoryGridModel grid = model("@0 1 2", 8, 16, MemoryRadix.HEX);
        grid.showLive(MemoryContents.decode("@0 A B"));
        assertThat(grid.getValueAt(0, 1)).isEqualTo("0A");
        grid.setCell(0, "ff");
        assertThat(grid.getValueAt(0, 1)).isEqualTo("FF");
        assertThat(grid.isEdited(0)).isTrue();
        assertThat(grid.getValueAt(0, 2)).isEqualTo("0B");
        assertThat(grid.isEdited(1)).isFalse();
        assertThat(grid.cellToolTip(0, 1)).contains("FF").contains("0A").contains("inicial");
        assertThat(grid.cellToolTip(0, 2)).isNull();
    }

    @Test
    void resettingTheMarksForgetsTheEdits() {
        MemoryGridModel grid = model("@0 1", 8, 16, MemoryRadix.HEX);
        grid.setCell(0, "5");
        grid.resetEditedMarks();
        assertThat(grid.isEdited(0)).isFalse();
    }

    @Test
    void onlyDigitsOfTheRadixAreAcceptedAtMostTheDigitsOfTheWidth() {
        MemoryGridModel hex = model("", 12, 16, MemoryRadix.HEX);
        assertThat(hex.maxDigits()).isEqualTo(3);
        assertThat(hex.digitsOnly("a1g-z", 0)).isEqualTo("A1");
        assertThat(hex.digitsOnly("abcd", 0)).isEqualTo("ABC");
        assertThat(hex.digitsOnly("abc", 2)).isEqualTo("A");
        MemoryGridModel decimal = model("", 8, 16, MemoryRadix.DECIMAL);
        assertThat(decimal.maxDigits()).isEqualTo(3);
        assertThat(decimal.digitsOnly("1a2", 0)).isEqualTo("12");
        MemoryGridModel binary = model("", 4, 16, MemoryRadix.BINARY);
        assertThat(binary.maxDigits()).isEqualTo(4);
        assertThat(binary.digitsOnly("1021", 0)).isEqualTo("101");
    }

    @Test
    void findsTheWordsHoldingAValueWithTheirRank() {
        MemoryGridModel grid = model("@1 FF @5 FF @9 FF 3", 8, 16, MemoryRadix.HEX);
        MemorySearch search = grid.search(0xFF);
        assertThat(search.count()).isEqualTo(3);
        assertThat(search.next(-1)).isEqualTo(OptionalLong.of(1));
        assertThat(search.next(5)).isEqualTo(OptionalLong.of(9));
        assertThat(search.next(9)).isEqualTo(OptionalLong.of(1));
        assertThat(search.previous(1)).isEqualTo(OptionalLong.of(9));
        assertThat(search.previous(9)).isEqualTo(OptionalLong.of(5));
        assertThat(search.rank(5)).isEqualTo(2);
    }

    @Test
    void searchesTheLiveWordsWhenShownAndTheEditedCellsInstead() {
        MemoryGridModel grid = model("@0 1", 8, 16, MemoryRadix.HEX);
        grid.showLive(MemoryContents.decode("@3 7 7"));
        assertThat(grid.search(7).count()).isEqualTo(2);
        assertThat(grid.search(1).count()).isEqualTo(0);
        grid.setCell(4, "9");
        assertThat(grid.search(7).count()).isEqualTo(1);
        assertThat(grid.search(9).next(-1)).isEqualTo(OptionalLong.of(4));
    }

    @Test
    void searchingZeroCountsTheUnsetWordsAndSkipsTheNonZeroOnes() {
        MemoryGridModel grid = model("@0 1 2 3 @5 4", 8, 8, MemoryRadix.HEX);
        MemorySearch search = grid.search(0);
        assertThat(search.count()).isEqualTo(4);
        assertThat(search.next(-1)).isEqualTo(OptionalLong.of(3));
        assertThat(search.next(3)).isEqualTo(OptionalLong.of(4));
        assertThat(search.next(4)).isEqualTo(OptionalLong.of(6));
        assertThat(search.next(7)).isEqualTo(OptionalLong.of(3));
        assertThat(search.previous(3)).isEqualTo(OptionalLong.of(7));
        assertThat(search.rank(6)).isEqualTo(3);
    }

    @Test
    void aSixteenMillionWordMemorySearchesQuicklyForBothAValueAndZero() {
        TreeMap<Long, Long> words = new TreeMap<>();
        for (long address = 0; address < 5000; address++) {
            words.put(address * 3000, address % 2 == 0 ? 0xABL : 0xCDL);
        }
        MemoryGridModel grid = new MemoryGridModel(MemoryContents.empty().withAll(words), 8, 1L << 24,
                MemoryRadix.HEX);
        long start = System.nanoTime();
        MemorySearch value = grid.search(0xAB);
        MemorySearch zero = grid.search(0);
        assertThat(value.count()).isEqualTo(2500);
        assertThat(zero.count()).isEqualTo((1L << 24) - 5000);
        assertThat(zero.next(0)).isEqualTo(OptionalLong.of(1));
        assertThat(zero.previous(0)).isEqualTo(OptionalLong.of((1L << 24) - 1));
        assertThat(value.next(-1)).isEqualTo(OptionalLong.of(0));
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2000);
    }

    @Test
    void aValueThatIsNotANumberOrDoesNotFitHasNoSearch() {
        MemoryGridModel grid = model("", 8, 16, MemoryRadix.HEX);
        assertThat(grid.parseSearch("zz")).isEmpty();
        assertThat(grid.parseSearch("100")).isEmpty();
        assertThat(grid.parseSearch(" ff ")).isEqualTo(OptionalLong.of(255));
    }
}
