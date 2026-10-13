package cl.almejo.vsim.core.truthtable;

import cl.almejo.vsim.core.value.Value;

import java.util.ArrayList;
import java.util.List;

/** How truth-table cells are written: on screen, copied as tab-separated text and exported as CSV. */
public final class TruthTableText {

    /** The output cells of a row that never settled. */
    public static final String UNSETTLED = "~";
    /** A shorted value, whose bits mean nothing (as on bus wires). */
    public static final String CONFLICT = "X";

    private TruthTableText() {
    }

    /**
     * {@code 0}, {@code 1} or {@code Z} for one bit; {@code 0x} and the hex digits for a bus ({@code Z} for a digit
     * all in Z, {@code ?} for one partly in Z); {@link #CONFLICT} when the drivers disagree.
     */
    public static String cell(Value value) {
        if (value.conflict()) {
            return CONFLICT;
        }
        if (value.width() == 1) {
            return switch (value.bit(0)) {
                case ZERO -> "0";
                case ONE -> "1";
                case HIGH_IMPEDANCE -> "Z";
            };
        }
        return "0x" + value.hex();
    }

    /** The column names, inputs first. */
    public static List<String> header(TruthTable table) {
        List<String> names = new ArrayList<>();
        table.inputs().forEach(column -> names.add(column.name()));
        table.outputs().forEach(column -> names.add(column.name()));
        return names;
    }

    /** The cells of {@code row}, inputs first; the outputs of a row that never settled are {@link #UNSETTLED}. */
    public static List<String> cells(Row row) {
        List<String> cells = new ArrayList<>();
        row.inputs().forEach(value -> cells.add(cell(value)));
        row.outputs().forEach(value -> cells.add(row.settled() ? cell(value) : UNSETTLED));
        return cells;
    }

    /**
     * A header line and one line per row, cells separated by tabs and every line ended by {@code \n}: pastes into a
     * spreadsheet as columns. Tabs and line breaks inside names become spaces.
     */
    public static String tabSeparated(TruthTable table) {
        StringBuilder text = new StringBuilder();
        appendLine(text, header(table).stream().map(name -> name.replaceAll("[\\t\\r\\n]", " ")).toList(), '\t');
        table.rows().forEach(row -> appendLine(text, cells(row), '\t'));
        return text.toString();
    }

    /**
     * Comma-separated values (RFC 4180): a header line and one line per row, every line ended by {@code \n}; a field
     * with a comma, a quote or a line break is quoted, its quotes doubled.
     */
    public static String csv(TruthTable table) {
        StringBuilder text = new StringBuilder();
        appendLine(text, header(table).stream().map(TruthTableText::csvField).toList(), ',');
        table.rows().forEach(row -> appendLine(text, cells(row), ','));
        return text.toString();
    }

    private static String csvField(String field) {
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    private static void appendLine(StringBuilder text, List<String> fields, char separator) {
        text.append(String.join(String.valueOf(separator), fields)).append('\n');
    }
}
