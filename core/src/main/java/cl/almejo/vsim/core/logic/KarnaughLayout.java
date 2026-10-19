package cl.almejo.vsim.core.logic;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Karnaugh map of a table with 2..4 inputs: the first input(s) on the rows and the last ones on the columns, both
 * in Gray order (2 inputs → 2×2, rows A, columns B; 3 → 2×4, rows A, columns BC; 4 → 4×4, rows AB, columns CD).
 * Neighbouring cells, including across the edges, differ in exactly one input.
 */
public final class KarnaughLayout {

    public static final int MINIMUM_INPUTS = 2;
    public static final int MAXIMUM_INPUTS = 4;

    private final int inputCount;
    private final int rowVariableCount;
    private final int columnVariableCount;

    /** A cell of the map, 0-based from the top-left corner. */
    public record GridCell(int row, int column) {
    }

    private KarnaughLayout(int inputCount) {
        this.inputCount = inputCount;
        this.rowVariableCount = inputCount / 2;
        this.columnVariableCount = inputCount - rowVariableCount;
    }

    public static KarnaughLayout of(int inputCount) {
        if (inputCount < MINIMUM_INPUTS || inputCount > MAXIMUM_INPUTS) {
            throw new IllegalArgumentException(
                    "a Karnaugh map needs " + MINIMUM_INPUTS + ".." + MAXIMUM_INPUTS + " inputs: " + inputCount);
        }
        return new KarnaughLayout(inputCount);
    }

    public int inputCount() {
        return inputCount;
    }

    public int rows() {
        return 1 << rowVariableCount;
    }

    public int columns() {
        return 1 << columnVariableCount;
    }

    /** The inputs (0 = first) whose values label the rows, most significant first. */
    public List<Integer> rowVariables() {
        return range(0, rowVariableCount);
    }

    /** The inputs (0 = first) whose values label the columns, most significant first. */
    public List<Integer> columnVariables() {
        return range(rowVariableCount, inputCount);
    }

    /** The Gray codes of the rows, top to bottom («0, 1» or «00, 01, 11, 10»). */
    public List<String> rowCodes() {
        return grayCodes(rowVariableCount);
    }

    /** The Gray codes of the columns, left to right. */
    public List<String> columnCodes() {
        return grayCodes(columnVariableCount);
    }

    /** Where row {@code tableRow} of the truth table sits on the map. */
    public GridCell cell(int tableRow) {
        if (tableRow < 0 || tableRow >= 1 << inputCount) {
            throw new IllegalArgumentException("no such row: " + tableRow);
        }
        int columnBits = tableRow & (columns() - 1);
        int rowBits = tableRow >> columnVariableCount;
        return new GridCell(grayPosition(rowBits), grayPosition(columnBits));
    }

    /** The truth-table row shown at {@code (row, column)} of the map. */
    public int tableRow(int row, int column) {
        if (row < 0 || row >= rows() || column < 0 || column >= columns()) {
            throw new IllegalArgumentException("no such cell: " + row + ", " + column);
        }
        return gray(row) << columnVariableCount | gray(column);
    }

    /** The cells of {@code term} on this map and the rectangles that draw them. */
    public KarnaughGroup group(Term term) {
        long all = (1L << inputCount) - 1;
        if ((term.mask() & ~all) != 0) {
            throw new IllegalArgumentException("the term uses inputs beyond " + inputCount + ": " + term);
        }
        Set<GridCell> cells = new LinkedHashSet<>();
        for (int tableRow = 0; tableRow <= all; tableRow++) {
            if (term.covers(tableRow)) {
                cells.add(cell(tableRow));
            }
        }
        int columnMask = columns() - 1;
        List<Segment> rowSegments = segments(rows(), (int) (term.mask() >> columnVariableCount),
                (int) (term.value() >> columnVariableCount));
        List<Segment> columnSegments = segments(columns(), (int) term.mask() & columnMask,
                (int) term.value() & columnMask);
        List<KarnaughGroup.CellRectangle> rectangles = new ArrayList<>();
        for (Segment rowSegment : rowSegments) {
            for (Segment columnSegment : columnSegments) {
                Set<KarnaughGroup.Side> open = EnumSet.noneOf(KarnaughGroup.Side.class);
                if (rowSegment.openAtStart()) {
                    open.add(KarnaughGroup.Side.TOP);
                }
                if (rowSegment.openAtEnd()) {
                    open.add(KarnaughGroup.Side.BOTTOM);
                }
                if (columnSegment.openAtStart()) {
                    open.add(KarnaughGroup.Side.LEFT);
                }
                if (columnSegment.openAtEnd()) {
                    open.add(KarnaughGroup.Side.RIGHT);
                }
                rectangles.add(new KarnaughGroup.CellRectangle(rowSegment.start(), columnSegment.start(),
                        rowSegment.length(), columnSegment.length(), open));
            }
        }
        return new KarnaughGroup(term, cells, rectangles);
    }

    /** A run of positions along one axis; {@code openAtStart}/{@code openAtEnd}: the group continues past the edge. */
    private record Segment(int start, int length, boolean openAtStart, boolean openAtEnd) {
    }

    /**
     * The runs of positions of one axis (of {@code size} Gray positions) whose codes match {@code mask}/{@code value}.
     * A subcube in Gray order is always one cyclic run: either inside the axis or wrapping from the last position to
     * the first, which is drawn as two pieces open at the edges.
     */
    private static List<Segment> segments(int size, int mask, int value) {
        boolean[] covered = new boolean[size];
        int count = 0;
        for (int position = 0; position < size; position++) {
            if ((gray(position) & mask) == value) {
                covered[position] = true;
                count++;
            }
        }
        if (count == size) {
            return List.of(new Segment(0, size, false, false));
        }
        if (covered[0] && covered[size - 1]) {
            int headLength = 0;
            while (covered[headLength]) {
                headLength++;
            }
            int tailStart = size;
            while (covered[tailStart - 1]) {
                tailStart--;
            }
            return List.of(new Segment(0, headLength, true, false),
                    new Segment(tailStart, size - tailStart, false, true));
        }
        int start = 0;
        while (!covered[start]) {
            start++;
        }
        return List.of(new Segment(start, count, false, false));
    }

    private static int gray(int position) {
        return position ^ position >> 1;
    }

    private static int grayPosition(int code) {
        int position = code;
        for (int shifted = code >> 1; shifted != 0; shifted >>= 1) {
            position ^= shifted;
        }
        return position;
    }

    private static List<String> grayCodes(int variableCount) {
        List<String> codes = new ArrayList<>();
        for (int position = 0; position < 1 << variableCount; position++) {
            StringBuilder code = new StringBuilder(Integer.toBinaryString(gray(position)));
            while (code.length() < variableCount) {
                code.insert(0, '0');
            }
            codes.add(code.toString());
        }
        return List.copyOf(codes);
    }

    private static List<Integer> range(int from, int to) {
        List<Integer> indices = new ArrayList<>();
        for (int index = from; index < to; index++) {
            indices.add(index);
        }
        return List.copyOf(indices);
    }
}
