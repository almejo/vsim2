package cl.almejo.vsim.core.terminal;

/** A cell on the screen, counted from 0 at the top-left corner. */
public record CellPosition(int row, int column) {

    public CellPosition {
        if (row < 0 || column < 0) {
            throw new IllegalArgumentException("a cell position is never negative: " + row + ", " + column);
        }
    }
}
