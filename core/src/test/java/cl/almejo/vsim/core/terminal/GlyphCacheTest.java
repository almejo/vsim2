package cl.almejo.vsim.core.terminal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The glyphs a Terminal read from its character ROM (spec §8.8): 8 × 8 bitmaps, bit 7 of a row the leftmost pixel. */
class GlyphCacheTest {

    /** An «H»: two stems and a bar, one PIX byte per row, row 0 first. */
    private static final int[] LETTER_H = {0b1000_0010, 0b1000_0010, 0b1000_0010, 0b1111_1110, 0b1000_0010,
            0b1000_0010, 0b1000_0010, 0b0000_0000};

    @Test
    void anEmptyCacheHasNoGlyph() {
        assertThat(GlyphCache.EMPTY.has('H')).isFalse();
        assertThat(GlyphCache.EMPTY.size()).isZero();
    }

    @Test
    void aGlyphKeepsItsRowsAndBitSevenIsTheLeftmostPixel() {
        GlyphCache cache = GlyphCache.EMPTY.with('H', GlyphCache.bitmapOf(LETTER_H));

        assertThat(cache.has('H')).isTrue();
        assertThat(cache.has('O')).isFalse();
        assertThat(cache.size()).isEqualTo(1);
        for (int row = 0; row < GlyphCache.GLYPH_ROWS; row++) {
            assertThat(cache.row('H', row)).as("row " + row).isEqualTo(LETTER_H[row]);
        }
        assertThat(cache.pixel('H', 0, 0)).as("bit 7 is the leftmost pixel").isTrue();
        assertThat(cache.pixel('H', 0, 1)).isFalse();
        assertThat(cache.pixel('H', 0, 6)).as("bit 1").isTrue();
        assertThat(cache.pixel('H', 0, 7)).as("bit 0 is the rightmost pixel").isFalse();
        assertThat(cache.pixel('H', 3, 3)).as("the bar").isTrue();
        assertThat(cache.pixel('H', 7, 0)).isFalse();
    }

    @Test
    void itIsImmutableAndEveryChangeIsANewGeneration() {
        GlyphCache first = GlyphCache.EMPTY.with('H', GlyphCache.bitmapOf(LETTER_H));
        GlyphCache second = first.with('O', 0L);
        GlyphCache cleared = second.cleared();

        assertThat(first.has('O')).as("the older cache does not change").isFalse();
        assertThat(second.has('H')).isTrue();
        assertThat(second.has('O')).as("a blank glyph is a glyph").isTrue();
        assertThat(cleared.size()).isZero();
        assertThat(cleared.has('H')).isFalse();
        assertThat(first.generation()).isGreaterThan(GlyphCache.EMPTY.generation());
        assertThat(second.generation()).isGreaterThan(first.generation());
        assertThat(cleared.generation()).as("clearing is a change too").isGreaterThan(second.generation());
    }

    @Test
    void onlyEightBitCodesHaveGlyphs() {
        assertThat(GlyphCache.EMPTY.has(-1)).isFalse();
        assertThat(GlyphCache.EMPTY.has(GlyphCache.CODES)).isFalse();
        assertThat(GlyphCache.EMPTY.has('ñ')).as("241 fits in CODE").isFalse();
        assertThatThrownBy(() -> GlyphCache.EMPTY.with(GlyphCache.CODES, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(GlyphCache.EMPTY.with('ñ', 1L).has('ñ')).isTrue();
    }
}
