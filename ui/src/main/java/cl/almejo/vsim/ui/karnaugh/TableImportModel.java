package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.ImportProblem;
import cl.almejo.vsim.core.logic.ImportResult;
import cl.almejo.vsim.core.logic.ImportedGrid;
import cl.almejo.vsim.core.logic.TableImport;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the import dialog edits (spec §6), on the EDT: the grid read from a file or the clipboard (or why it could not
 * be read), how many leading columns are inputs, the title and the options. {@link #result()} is the table made of
 * them, recomputed after every change; its problems are shown cell by cell («Celda D4: …»).
 */
final class TableImportModel {

    /** The most problems listed; the rest are counted in one more line. */
    static final int MAXIMUM_PROBLEM_LINES = 20;

    private final List<Runnable> listeners = new ArrayList<>();
    private ImportedGrid grid;
    /** The grid's columns that make the table ({@link TableImport#columns}): a blank spacer column is not one. */
    private List<Integer> columns = List.of();
    private ImportProblem readProblem;
    private String source = "";
    private String title = "";
    private int inputCount = 1;
    private WizardOptions options = WizardOptions.defaults();
    private ImportResult result;

    /** A grid was read from {@code source} (a file name, or the clipboard): the inputs are proposed from its names. */
    void load(ImportedGrid newGrid, String newSource, String newTitle) {
        grid = newGrid;
        columns = TableImport.columns(newGrid);
        readProblem = null;
        source = newSource;
        title = newTitle;
        inputCount = TableImport.proposedInputCount(names(0, columns.size()));
        changed();
    }

    /** {@code source} could not be read at all: {@code problem} says why; the previous table is gone. */
    void loadFailed(ImportProblem problem, String newSource) {
        grid = null;
        columns = List.of();
        readProblem = problem;
        source = newSource;
        changed();
    }

    boolean hasTable() {
        return grid != null;
    }

    String source() {
        return source;
    }

    String title() {
        return title;
    }

    void setTitle(String newTitle) {
        if (!newTitle.equals(title)) {
            title = newTitle;
            changed();
        }
    }

    int inputCount() {
        return inputCount;
    }

    void setInputCount(int count) {
        int clamped = Math.clamp(count, 1, Math.max(1, columnCount() - 1));
        if (clamped != inputCount) {
            inputCount = clamped;
            changed();
        }
    }

    /** The table's columns: up to the last name written, without the blank spacer columns. */
    int columnCount() {
        return columns.size();
    }

    List<String> inputNames() {
        return grid == null ? List.of() : names(0, inputCount);
    }

    List<String> outputNames() {
        return grid == null ? List.of() : names(Math.min(inputCount, columnCount()), columnCount());
    }

    private List<String> names(int from, int to) {
        List<String> names = new ArrayList<>();
        for (int column : columns.subList(from, to)) {
            names.add(column < grid.header().size() ? grid.header().get(column).strip() : "");
        }
        return names;
    }

    WizardOptions options() {
        return options;
    }

    void setOptions(WizardOptions newOptions) {
        if (!newOptions.equals(options)) {
            options = newOptions;
            fire();
        }
    }

    /** The table, when it was read and has no problems and a title. */
    Optional<TruthTableSpec> spec() {
        return result == null || title.isBlank() ? Optional.empty() : result.spec();
    }

    /**
     * What is wrong, one line each, in the order found: «Celda D4: …», «Fila 5: …» or a line about the file; past
     * {@value #MAXIMUM_PROBLEM_LINES} lines, one more counts the rest («… y 12 problemas más.»).
     */
    List<String> problemLines() {
        List<String> lines = new ArrayList<>();
        if (readProblem != null) {
            lines.add(describe(readProblem));
        }
        if (result != null) {
            if (title.isBlank()) {
                lines.add(Messages.text("karnaugh.problem.title"));
            }
            result.problems().forEach(problem -> lines.add(describe(problem)));
        }
        if (lines.size() > MAXIMUM_PROBLEM_LINES) {
            int rest = lines.size() - MAXIMUM_PROBLEM_LINES;
            List<String> shown = new ArrayList<>(lines.subList(0, MAXIMUM_PROBLEM_LINES));
            shown.add(Messages.format("tableImport.moreProblems", rest, String.valueOf(rest)));
            return shown;
        }
        return lines;
    }

    /** «Faltaban 12 combinaciones: se tomaron como X.», when the table is usable and the file skipped some. */
    Optional<String> missingNotice() {
        if (result == null || result.spec().isEmpty() || result.missingRows() == 0) {
            return Optional.empty();
        }
        return Optional.of(Messages.format("tableImport.missing", result.missingRows(),
                String.valueOf(result.missingRows())));
    }

    /** A problem as the user reads it, with its cell or row when it has one. */
    static String describe(ImportProblem problem) {
        String message = Messages.format(problem.messageKey(), problem.arguments().toArray());
        if (problem.row() == 0) {
            return message;
        }
        return Messages.format(problem.column() == 0 ? "tableImport.atRow" : "tableImport.atCell",
                problem.cellName(), message);
    }

    void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void changed() {
        result = grid == null ? null : TableImport.toSpec(grid, inputCount, title.strip());
        fire();
    }

    private void fire() {
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }
}
