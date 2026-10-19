package cl.almejo.vsim.core.logic;

/**
 * One term of a minimized expression over {@code n} inputs, written with the row-index bit convention of
 * {@link TruthTableSpec} (bit {@code n - 1 - i} of a row index is input {@code i}).
 * <p>
 * A bit set in {@code mask} is a variable present in the term; its bit in {@code value} says whether it appears plain
 * (1) or negated (0) in a product. In a sum term of a product of sums the same pair describes the group of 0s it
 * comes from (the sum is the complement: a variable with value 1 appears negated). {@code mask == 0} is the term
 * covering every row.
 *
 * @param mask  the variables present
 * @param value their values inside the group ({@code value & ~mask == 0})
 */
public record Term(long mask, long value) {

    public Term {
        if ((value & ~mask) != 0) {
            throw new IllegalArgumentException("value has bits outside the mask");
        }
    }

    /** Whether row {@code row} is one of the cells of this term's group. */
    public boolean covers(int row) {
        return (row & mask) == value;
    }

    public int literalCount() {
        return Long.bitCount(mask);
    }

    /** Whether input {@code input} (0 = first) of {@code inputCount} appears in the term. */
    public boolean uses(int input, int inputCount) {
        return (mask >> (inputCount - 1 - input) & 1) == 1;
    }

    /** The value input {@code input} keeps inside the group; only meaningful when {@link #uses} is true. */
    public boolean valueOf(int input, int inputCount) {
        return (value >> (inputCount - 1 - input) & 1) == 1;
    }
}
