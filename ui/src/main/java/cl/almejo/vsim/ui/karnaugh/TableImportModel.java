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

    /**
     * The most problems listed; the rest are counted in one more line (the preview flags every one of them, and a long
     * list would leave it no room).
     */
    static final int MAXIMUM_PROBLEM_LINES = 6;

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

    /**
     * A grid was read from {@code source} (a file name, or the clipboard): the inputs are proposed from its names and
     * rows.
     */
    void load(ImportedGrid newGrid, String newSource, String newTitle) {
        grid = newGrid;
        columns = TableImport.columns(newGrid);
        readProblem = null;
        source = newSource;
        title = newTitle;
        inputCount = TableImport.proposedInputCount(newGrid);
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
     * Whether the chosen split fits the Karnaugh wizard: {@value KarnaughModel#MINIMUM_INPUTS}..{@value
     * KarnaughModel#MAXIMUM_INPUTS} inputs and up to {@value KarnaughModel#MAXIMUM_OUTPUTS} outputs. False without a
     * table.
     */
    boolean splitFitsTheWizard() {
        int outputCount = columnCount() - inputCount;
        return hasTable() && inputCount >= KarnaughModel.MINIMUM_INPUTS && inputCount <= KarnaughModel.MAXIMUM_INPUTS
                && outputCount >= KarnaughModel.MINIMUM_OUTPUTS && outputCount <= KarnaughModel.MAXIMUM_OUTPUTS;
    }

    /** Whether «Abrir en el asistente» can take the table: it fits the wizard and has no problems. */
    boolean canOpenInWizard() {
        return splitFitsTheWizard() && spec().isPresent();
    }

    /**
     * What is wrong with the data (page «Datos»), one line each, in the order found: «Celda D4: …», «Fila 5: …» or a
     * line about the file; past {@value #MAXIMUM_PROBLEM_LINES} lines, one more counts the rest («… y 12 problemas
     * más.»). A blank title is not here: it is said on page «Crear», where the title is ({@link #titleProblem}).
     */
    List<String> problemLines() {
        List<String> lines = new ArrayList<>();
        if (readProblem != null) {
            lines.add(describe(readProblem));
        }
        if (result != null) {
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

    /** «Escribe un título para la tabla.» when a table is read and the title is blank. */
    Optional<String> titleProblem() {
        return result != null && title.isBlank() ? Optional.of(Messages.text("karnaugh.problem.title"))
                : Optional.empty();
    }

    /** Whether the data reads without problems with this split: «Siguiente» can go to page «Crear». */
    boolean dataReady() {
        return result != null && result.spec().isPresent();
    }

    /** The grid read, or null when there is none. */
    ImportedGrid grid() {
        return grid;
    }

    /** The grid's columns that make the table. */
    List<Integer> tableColumns() {
        return columns;
    }

    /** The problems of the table made of the grid (none without a grid). */
    List<ImportProblem> tableProblems() {
        return result == null ? List.of() : result.problems();
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
        Object[] arguments = problem.arguments().stream()
                .map(argument -> argument instanceof List<?> names ? listed(names) : argument)
                .toArray();
        String message = Messages.format(problem.messageKey(), arguments);
        if (problem.row() == 0) {
            return message;
        }
        return Messages.format(problem.column() == 0 ? "tableImport.atRow" : "tableImport.atCell",
                problem.cellName(), message);
    }

    /** «F», «F y G», «F, G y H». */
    private static String listed(List<?> names) {
        List<String> texts = names.stream().map(String::valueOf).toList();
        if (texts.size() < 2) {
            return String.join("", texts);
        }
        return Messages.format("karnaugh.why.list", String.join(", ", texts.subList(0, texts.size() - 1)),
                texts.getLast());
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
