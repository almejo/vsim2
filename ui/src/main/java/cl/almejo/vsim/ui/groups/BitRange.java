package cl.almejo.vsim.ui.groups;

/**
 * One «tramo» of a pin in the «Grupos» window: the bus bits from {@code first} to {@code last}, both included, as the
 * user typed them (in either order, as {@link cl.almejo.vsim.core.component.standard.BitGroups} reads a range too;
 * {@link #low()} and {@link #high()} give the ends).
 */
record BitRange(int first, int last) {

    int low() {
        return Math.min(first, last);
    }

    int high() {
        return Math.max(first, last);
    }

    /** A range of one bit. */
    static BitRange of(int bit) {
        return new BitRange(bit, bit);
    }
}
