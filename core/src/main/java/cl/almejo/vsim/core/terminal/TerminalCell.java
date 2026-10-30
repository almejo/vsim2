package cl.almejo.vsim.core.terminal;

import java.util.Arrays;
import java.util.Objects;

/**
 * One character on the screen with what the cell tooltip shows (spec §8.4): the bytes that made it and the simulation
 * time of its last byte. A blank cell has a space, no bytes and {@link #NEVER_WRITTEN} as its time.
 *
 * @param codePoint the character (wide characters also take one cell, spec §16 decision 9)
 * @param style     the look it was written with
 * @param bytes     the bytes that made it, in arrival order; empty for blanks and for the {@code ?} of an undefined
 *                  byte (copied in and out)
 * @param time      the simulation time its last byte arrived, or {@link #NEVER_WRITTEN}
 */
public record TerminalCell(int codePoint, CellStyle style, byte[] bytes, long time) {

    /** The time of a cell no byte has written. */
    public static final long NEVER_WRITTEN = -1;
    private static final byte[] NO_BYTES = {};
    /** An empty cell with the default style. */
    public static final TerminalCell BLANK = new TerminalCell(' ', CellStyle.PLAIN, NO_BYTES, NEVER_WRITTEN);

    public TerminalCell {
        if (!Character.isValidCodePoint(codePoint)) {
            throw new IllegalArgumentException("not a code point: " + codePoint);
        }
        Objects.requireNonNull(style, "style");
        bytes = bytes.clone();
        if (time < NEVER_WRITTEN) {
            throw new IllegalArgumentException("time must be a simulation time or NEVER_WRITTEN: " + time);
        }
    }

    /** An erased cell: a blank with the given style (only its background matters). */
    static TerminalCell blank(CellStyle style) {
        return style.equals(CellStyle.PLAIN) ? BLANK : new TerminalCell(' ', style, NO_BYTES, NEVER_WRITTEN);
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    /** Whether a byte wrote this cell (the tooltip and the last-character glow only show for written cells). */
    public boolean written() {
        return time != NEVER_WRITTEN;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TerminalCell cell && codePoint == cell.codePoint && time == cell.time
                && style.equals(cell.style) && Arrays.equals(bytes, cell.bytes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(codePoint, style, Arrays.hashCode(bytes), time);
    }

    @Override
    public String toString() {
        return "TerminalCell[" + new String(Character.toChars(codePoint)) + ", " + style + ", "
                + Arrays.toString(bytes) + ", " + time + "]";
    }
}
