package cl.almejo.vsim.core.terminal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable picture of a screen, taken on the simulation thread and read by the painters and the window on the EDT.
 * It owns private copies of the rows: nothing the screen does later and nothing a reader does to the arrays it gets
 * from {@link #cells()} changes it.
 *
 * @param rows              the screen's rows
 * @param columns           the screen's columns
 * @param cells             one array of {@code columns} cells per row, top first (copied in; {@link #cells()} copies
 *                          out, so prefer {@link #cell(int, int)} when painting)
 * @param cursorRow         the cursor's row
 * @param cursorColumn      the cursor's column (on the last column while a wrap is pending)
 * @param cursorVisible     false after {@code ESC[?25l}
 * @param lastWritten       the cell the last character went to, empty once it scrolled off or the screen was cleared
 * @param lastWrittenSerial counts written characters, so the last-character glow restarts on the same cell
 * @param version           the screen's {@link TerminalScreen#version()} this frame shows
 * @param bellCount         BEL bytes received so far
 * @param printedCount      characters placed on the screen so far
 */
public record TerminalFrame(int rows, int columns, List<TerminalCell[]> cells, int cursorRow, int cursorColumn,
                            boolean cursorVisible, Optional<CellPosition> lastWritten, long lastWrittenSerial,
                            long version, long bellCount, long printedCount) {

    public TerminalFrame {
        Objects.requireNonNull(lastWritten, "lastWritten");
        if (cells.size() != rows) {
            throw new IllegalArgumentException("expected " + rows + " rows, got " + cells.size());
        }
        List<TerminalCell[]> copies = new ArrayList<>(rows);
        for (TerminalCell[] row : cells) {
            if (row.length != columns) {
                throw new IllegalArgumentException("expected " + columns + " columns, got " + row.length);
            }
            copies.add(row.clone());
        }
        cells = List.copyOf(copies);
        if (cursorRow < 0 || cursorRow >= rows || cursorColumn < 0 || cursorColumn >= columns) {
            throw new IllegalArgumentException("cursor outside the screen: " + cursorRow + ", " + cursorColumn);
        }
    }

    /** Fresh copies of the rows; costs a copy of the screen, so painters use {@link #cell(int, int)}. */
    @Override
    public List<TerminalCell[]> cells() {
        List<TerminalCell[]> copies = new ArrayList<>(rows);
        for (TerminalCell[] row : cells) {
            copies.add(row.clone());
        }
        return copies;
    }

    /** The cell at the given row and column, without copying. */
    public TerminalCell cell(int row, int column) {
        return cells.get(row)[column];
    }

    /** One row's characters with its trailing spaces trimmed. */
    public String rowText(int row) {
        StringBuilder text = new StringBuilder(columns);
        for (TerminalCell cell : cells.get(row)) {
            text.appendCodePoint(cell.codePoint());
        }
        return text.toString().stripTrailing();
    }

    /** The screen as text: every row with its trailing spaces trimmed, joined by {@code \n}. */
    public String text() {
        StringBuilder text = new StringBuilder(rows * (columns + 1));
        for (int row = 0; row < rows; row++) {
            if (row > 0) {
                text.append('\n');
            }
            text.append(rowText(row));
        }
        return text.toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TerminalFrame frame && rows == frame.rows && columns == frame.columns
                && cursorRow == frame.cursorRow && cursorColumn == frame.cursorColumn
                && cursorVisible == frame.cursorVisible && lastWrittenSerial == frame.lastWrittenSerial
                && version == frame.version && bellCount == frame.bellCount && printedCount == frame.printedCount
                && lastWritten.equals(frame.lastWritten) && sameCells(frame);
    }

    private boolean sameCells(TerminalFrame frame) {
        for (int row = 0; row < rows; row++) {
            if (!Arrays.equals(cells.get(row), frame.cells.get(row))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(rows, columns, cursorRow, cursorColumn, version, lastWrittenSerial);
    }

    @Override
    public String toString() {
        return "TerminalFrame[" + rows + "x" + columns + ", version " + version + ", cursor " + cursorRow + ","
                + cursorColumn + "]";
    }
}
