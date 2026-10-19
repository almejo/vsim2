package cl.almejo.vsim.core.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * The raw cells of an imported table, before they are understood as a truth table.
 *
 * @param header    the cells of the first non-blank row (the names); empty when the file has no content
 * @param rows      the rows after the header, as written (blank rows included so row numbers stay right)
 * @param headerRow the 1-based number of the header row as the user sees it in the spreadsheet; row {@code index} of
 *                  {@code rows} is number {@code headerRow + 1 + index}
 */
public record ImportedGrid(List<String> header, List<List<String>> rows, int headerRow) {

    public ImportedGrid {
        header = List.copyOf(header);
        List<List<String>> copied = new ArrayList<>();
        for (List<String> row : rows) {
            copied.add(List.copyOf(row));
        }
        rows = List.copyOf(copied);
    }

    /** A grid whose header is the first row of the file. */
    public ImportedGrid(List<String> header, List<List<String>> rows) {
        this(header, rows, 1);
    }
}
