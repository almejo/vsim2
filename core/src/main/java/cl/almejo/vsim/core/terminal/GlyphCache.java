package cl.almejo.vsim.core.terminal;

/**
 * The glyphs a Terminal read from its character ROM («Generador de caracteres: Memoria externa», spec §8.8): an 8 × 8
 * bitmap for each code 0–255 resolved so far. A glyph is a {@code long}: row {@code r} (0 = the top) is the PIX byte
 * read for it, in bits {@code 8r} to {@code 8r + 7}; in a row bit 7 is the leftmost pixel. Immutable: each change gives
 * a new cache with a greater {@link #generation()}, so a painter can tell two caches apart without comparing them.
 */
public final class GlyphCache {

    /** CODE is 8 bits wide: codes 0–255 can have a glyph, any other character keeps the internal font. */
    public static final int CODES = 256;
    /** ROW is 3 bits wide: eight rows per glyph. */
    public static final int GLYPH_ROWS = 8;
    /** PIX is 8 bits wide: eight pixels per row. */
    public static final int GLYPH_COLUMNS = 8;
    /** No glyph yet. */
    public static final GlyphCache EMPTY = new GlyphCache(new long[CODES], new boolean[CODES], 0, 0);

    private static final int ROW_MASK = 0xFF;
    private static final int LEFTMOST_BIT = GLYPH_COLUMNS - 1;

    private final long[] bitmaps;
    private final boolean[] present;
    private final int size;
    private final long generation;

    private GlyphCache(long[] bitmaps, boolean[] present, int size, long generation) {
        this.bitmaps = bitmaps;
        this.present = present;
        this.size = size;
        this.generation = generation;
    }

    /** The glyph whose row {@code r} is {@code rows[r]} (its low 8 bits), as {@link #with} takes it. */
    public static long bitmapOf(int[] rows) {
        if (rows.length != GLYPH_ROWS) {
            throw new IllegalArgumentException("a glyph has " + GLYPH_ROWS + " rows: " + rows.length);
        }
        long bitmap = 0L;
        for (int row = 0; row < GLYPH_ROWS; row++) {
            bitmap |= (long) (rows[row] & ROW_MASK) << (Byte.SIZE * row);
        }
        return bitmap;
    }

    /** Whether the ROM gave a glyph for {@code codePoint} (always false outside 0–255). */
    public boolean has(int codePoint) {
        return codePoint >= 0 && codePoint < CODES && present[codePoint];
    }

    /** How many codes have a glyph. */
    public int size() {
        return size;
    }

    /** The PIX byte of row {@code row} of {@code codePoint}'s glyph; 0 without a glyph. */
    public int row(int codePoint, int row) {
        if (!has(codePoint)) {
            return 0;
        }
        return (int) (bitmaps[codePoint] >>> (Byte.SIZE * row)) & ROW_MASK;
    }

    /** Whether the pixel at {@code row}, {@code column} (0 = the leftmost, bit 7) of {@code codePoint}'s glyph is on. */
    public boolean pixel(int codePoint, int row, int column) {
        return (row(codePoint, row) >>> (LEFTMOST_BIT - column) & 1) == 1;
    }

    /** Grows with every change; caches of one Terminal with the same generation hold the same glyphs. */
    public long generation() {
        return generation;
    }

    /** This cache with {@code bitmap} (see {@link #bitmapOf}) as {@code codePoint}'s glyph. */
    public GlyphCache with(int codePoint, long bitmap) {
        if (codePoint < 0 || codePoint >= CODES) {
            throw new IllegalArgumentException("only codes 0–255 have glyphs: " + codePoint);
        }
        long[] nextBitmaps = bitmaps.clone();
        boolean[] nextPresent = present.clone();
        nextBitmaps[codePoint] = bitmap;
        nextPresent[codePoint] = true;
        return new GlyphCache(nextBitmaps, nextPresent, present[codePoint] ? size : size + 1, generation + 1);
    }

    /** A cache with no glyph that is newer than this one. */
    public GlyphCache cleared() {
        return new GlyphCache(new long[CODES], new boolean[CODES], 0, generation + 1);
    }

    @Override
    public String toString() {
        return "GlyphCache[" + size + " glyphs, generation " + generation + "]";
    }
}
