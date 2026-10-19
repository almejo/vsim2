package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.ImportProblem;
import cl.almejo.vsim.core.logic.ImportedGrid;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.logic.TableImport;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The import dialog's preview: the grid as VSim reads it, one table row per non-blank file row. Column 0 is the row
 * number as the spreadsheet shows it (the numbering of the problem messages), then one column per table column
 * ({@link TableImport#columns}: a blank spacer column is left out). A cell shows the value it means (0, 1 or X), or
 * its text when it means nothing; the text of the file goes in the tooltip when it differs. Cells, rows and column
 * headers named by a problem are flagged, with the problem as tooltip. Cells are computed when asked for, so a large
 * table costs nothing until it is painted.
 */
final class TablePreviewModel extends AbstractTableModel {

    private ImportedGrid grid;
    private List<Integer> columns = List.of();
    private int inputCount;
    /** The indexes in the grid's rows of the rows shown (blank rows are left out). */
    private final List<Integer> shownRows = new ArrayList<>();
    /** Problems of one cell, by file row and 1-based file column. */
    private final Map<Long, List<String>> cellProblems = new HashMap<>();
    /** Problems of a whole row, by file row. */
    private final Map<Integer, List<String>> rowProblems = new LinkedHashMap<>();

    /** Shows {@code grid} split after {@code count} inputs, flagging {@code problems}. */
    void show(ImportedGrid newGrid, List<Integer> newColumns, int count, List<ImportProblem> problems) {
        grid = newGrid;
        columns = List.copyOf(newColumns);
        inputCount = count;
        shownRows.clear();
        for (int index = 0; index < newGrid.rows().size(); index++) {
            if (!newGrid.rows().get(index).stream().allMatch(String::isBlank)) {
                shownRows.add(index);
            }
        }
        cellProblems.clear();
        rowProblems.clear();
        for (ImportProblem problem : problems) {
            String message = TableImportModel.describe(problem);
            if (problem.messageKey().equals(ImportProblem.CONFLICTING_ROW)) {
                List<Object> arguments = problem.arguments();
                for (Object row : arguments.subList(arguments.size() - 2, arguments.size())) {
                    rowProblems.computeIfAbsent((Integer) row, key -> new ArrayList<>()).add(message);
                }
            } else if (problem.row() != 0 && problem.column() == 0) {
                rowProblems.computeIfAbsent(problem.row(), key -> new ArrayList<>()).add(message);
            } else if (problem.row() != 0) {
                cellProblems.computeIfAbsent(key(problem.row(), problem.column()), key -> new ArrayList<>())
                        .add(message);
            }
        }
        fireTableStructureChanged();
    }

    /** Nothing read (yet, or the file could not be read). */
    void clear() {
        grid = null;
        columns = List.of();
        shownRows.clear();
        cellProblems.clear();
        rowProblems.clear();
        fireTableStructureChanged();
    }

    boolean isEmpty() {
        return grid == null;
    }

    @Override
    public int getRowCount() {
        return shownRows.size();
    }

    @Override
    public int getColumnCount() {
        return grid == null ? 0 : columns.size() + 1;
    }

    @Override
    public String getColumnName(int column) {
        if (column == 0) {
            return Messages.text("tableImport.preview.row");
        }
        return cell(grid.header(), columns.get(column - 1)).strip();
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return column == 0 ? Integer.class : String.class;
    }

    @Override
    public Object getValueAt(int row, int column) {
        return column == 0 ? fileRow(row) : shown(row, column);
    }

    /** The row number of preview row {@code row} as the spreadsheet shows it. */
    int fileRow(int row) {
        return grid.headerRow() + 1 + shownRows.get(row);
    }

    /** What the cell shows: 0, 1 or X, the file's text when it means none of them, empty for an empty input. */
    String shown(int row, int column) {
        String text = text(row, column);
        LogicValue value = TableImport.valueOf(text);
        if (value == null) {
            return text;
        }
        if (text.isEmpty() && isInput(column)) {
            return "";
        }
        return switch (value) {
            case ONE -> "1";
            case ZERO -> "0";
            default -> "X";
        };
    }

    /** An empty output cell: it is taken as X, shown muted. */
    boolean isBlankCell(int row, int column) {
        return column > 0 && isOutput(column) && text(row, column).isEmpty();
    }

    /**
     * The cell's problems, or what the file says when it differs from what is shown, or null.
     */
    String tooltip(int row, int column) {
        List<String> problems = problems(row, column);
        if (!problems.isEmpty()) {
            return joined(problems);
        }
        if (column == 0) {
            return null;
        }
        String text = text(row, column);
        if (text.isEmpty()) {
            return isOutput(column) ? Messages.text("tableImport.preview.empty") : null;
        }
        return text.equalsIgnoreCase(shown(row, column)) ? null
                : Messages.format("tableImport.preview.original", text);
    }

    boolean isProblem(int row, int column) {
        return !problems(row, column).isEmpty();
    }

    private List<String> problems(int row, int column) {
        List<String> problems = new ArrayList<>(rowProblems.getOrDefault(fileRow(row), List.of()));
        if (column > 0) {
            problems.addAll(cellProblems.getOrDefault(key(fileRow(row), columns.get(column - 1) + 1), List.of()));
        }
        return problems;
    }

    /** The first preview row with a problem, or -1. */
    int firstProblemRow() {
        for (int row = 0; row < getRowCount(); row++) {
            int fileRow = fileRow(row);
            if (rowProblems.containsKey(fileRow)) {
                return row;
            }
            for (int column : columns) {
                if (cellProblems.containsKey(key(fileRow, column + 1))) {
                    return row;
                }
            }
        }
        return -1;
    }

    /** Whether the name of preview column {@code column} has a problem (blank or repeated). */
    boolean isHeaderProblem(int column) {
        return column > 0 && cellProblems.containsKey(key(grid.headerRow(), columns.get(column - 1) + 1));
    }

    /** The name's problem, or «Entrada» / «Salida». */
    String headerTooltip(int column) {
        if (isHeaderProblem(column)) {
            return joined(cellProblems.get(key(grid.headerRow(), columns.get(column - 1) + 1)));
        }
        if (column == 0) {
            return null;
        }
        return Messages.text(isInput(column) ? "tableImport.preview.input" : "tableImport.preview.output");
    }

    boolean isInput(int column) {
        return column > 0 && column <= inputCount;
    }

    boolean isOutput(int column) {
        return column > inputCount && column < getColumnCount();
    }

    /** The first output column: a thicker line separates it from the inputs. */
    boolean isFirstOutput(int column) {
        return column == inputCount + 1 && isOutput(column);
    }

    /** How many of the preview's columns (after the row number) are inputs. */
    int inputCount() {
        return inputCount;
    }

    private String text(int row, int column) {
        return cell(grid.rows().get(shownRows.get(row)), columns.get(column - 1)).strip();
    }

    private static String cell(List<String> row, int column) {
        return column < row.size() ? row.get(column) : "";
    }

    private static long key(int row, int column) {
        return (long) row << 32 | column;
    }

    /** One problem as it is; several on lines of their own. */
    private static String joined(List<String> messages) {
        if (messages.size() == 1) {
            return messages.getFirst();
        }
        StringBuilder html = new StringBuilder("<html>");
        for (String message : messages) {
            html.append(html.length() > 6 ? "<br>" : "").append(message.replace("&", "&amp;").replace("<", "&lt;"));
        }
        return html.append("</html>").toString();
    }
}
