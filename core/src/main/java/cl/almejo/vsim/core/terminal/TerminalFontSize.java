package cl.almejo.vsim.core.terminal;

/** The size of a Terminal's cells; each has a fixed cell size in pixels at 100 % so the part's size needs no fonts. */
public enum TerminalFontSize {
    SMALL(6, 12),
    NORMAL(8, 16),
    LARGE(10, 20);

    private final int cellWidth;
    private final int cellHeight;

    TerminalFontSize(int cellWidth, int cellHeight) {
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
    }

    /** A cell's width in pixels at 100 %. */
    public int cellWidth() {
        return cellWidth;
    }

    /** A cell's height in pixels at 100 %. */
    public int cellHeight() {
        return cellHeight;
    }
}
