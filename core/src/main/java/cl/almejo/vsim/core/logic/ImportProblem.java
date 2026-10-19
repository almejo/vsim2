package cl.almejo.vsim.core.logic;

import java.util.List;

/**
 * Something wrong in an imported table. The message key lives in the UI bundles and is formatted with
 * {@code arguments}.
 *
 * @param messageKey the bundle key, one of the constants of this record
 * @param arguments  the values for the message's placeholders
 * @param row        1-based row as the user sees it in the spreadsheet, 0 when the problem is not about one row
 * @param column     1-based column (A = 1), 0 when the problem is not about one cell
 */
public record ImportProblem(String messageKey, List<Object> arguments, int row, int column) {

    /** The file has no names and no rows. */
    public static final String EMPTY_FILE = "tableImport.problem.emptyFile";
    /** The file has the names but no row of values. */
    public static final String NO_ROWS = "tableImport.problem.noRows";
    /** The file is not an Excel workbook that can be read ({@code .xlsx}). */
    public static final String NOT_A_SPREADSHEET = "tableImport.problem.notASpreadsheet";
    /** The file is larger than VSim reads. Arguments: the limit in megabytes. */
    public static final String TOO_LARGE = "tableImport.problem.tooLarge";
    /** Zero input columns were chosen. */
    public static final String NO_INPUTS = "tableImport.problem.noInputs";
    /** More inputs than allowed. Arguments: the count, the limit. */
    public static final String TOO_MANY_INPUTS = "tableImport.problem.tooManyInputs";
    /** No column is left for the outputs. */
    public static final String NO_OUTPUTS = "tableImport.problem.noOutputs";
    /** More outputs than allowed. Arguments: the count, the limit. */
    public static final String TOO_MANY_OUTPUTS = "tableImport.problem.tooManyOutputs";
    /** A column without a name. */
    public static final String BLANK_NAME = "tableImport.problem.blankName";
    /** A name used twice (ignoring case). Arguments: the second name. */
    public static final String REPEATED_NAME = "tableImport.problem.repeatedName";
    /** A row with more cells than the table has columns. Arguments: the cells in the row, the columns. */
    public static final String WRONG_CELL_COUNT = "tableImport.problem.wrongCellCount";
    /** An input cell without a value. */
    public static final String EMPTY_INPUT = "tableImport.problem.emptyInput";
    /** An input cell that is not 0 or 1. Arguments: the text. */
    public static final String INPUT_NOT_BINARY = "tableImport.problem.inputNotBinary";
    /** An output cell that is not 0, 1 or X. Arguments: the text. */
    public static final String INVALID_VALUE = "tableImport.problem.invalidValue";
    /**
     * The same combination appears twice with different outputs; a problem about the file (it names both rows).
     * Arguments: the combination as written ({@code "0 1 1"}), the names of the outputs that differ (a
     * {@code List<String>}), how many they are, the earlier row and the later row.
     */
    public static final String CONFLICTING_ROW = "tableImport.problem.conflictingRow";

    /** Every message key, for the bundle completeness test. */
    public static final List<String> MESSAGE_KEYS = List.of(EMPTY_FILE, NO_ROWS, NOT_A_SPREADSHEET, TOO_LARGE,
            NO_INPUTS, TOO_MANY_INPUTS, NO_OUTPUTS, TOO_MANY_OUTPUTS, BLANK_NAME, REPEATED_NAME, WRONG_CELL_COUNT,
            EMPTY_INPUT, INPUT_NOT_BINARY, INVALID_VALUE, CONFLICTING_ROW);

    public ImportProblem {
        arguments = List.copyOf(arguments);
    }

    /** The cell's name as a spreadsheet shows it ({@code D4}), {@code 4} for a whole row, empty for the file. */
    public String cellName() {
        if (row == 0) {
            return "";
        }
        return column == 0 ? Integer.toString(row) : columnLetters(column) + row;
    }

    /** The spreadsheet letters of a 1-based column: 1 → A, 27 → AA. */
    public static String columnLetters(int column) {
        StringBuilder letters = new StringBuilder();
        for (int remaining = column; remaining > 0; remaining = (remaining - 1) / 26) {
            letters.insert(0, (char) ('A' + (remaining - 1) % 26));
        }
        return letters.toString();
    }

    /** A problem about the whole file. */
    public static ImportProblem general(String messageKey, Object... arguments) {
        return new ImportProblem(messageKey, List.of(arguments), 0, 0);
    }
}
