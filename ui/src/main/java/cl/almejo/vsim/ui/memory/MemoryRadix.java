package cl.almejo.vsim.ui.memory;

/** How the content editor shows the words: the number base of the cells. */
public enum MemoryRadix {
    HEX(16), DECIMAL(10), BINARY(2);

    private final int base;

    MemoryRadix(int base) {
        this.base = base;
    }

    public int base() {
        return base;
    }
}
