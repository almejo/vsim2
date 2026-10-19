package cl.almejo.vsim.core.logic;

import java.util.List;
import java.util.Optional;

/**
 * What {@link TableImport#toSpec} made of a grid.
 *
 * @param spec        the table, present only when there are no problems
 * @param problems    what is wrong, in the order found (counts and names first, then row by row)
 * @param missingRows how many input combinations the file did not list (they were taken as X)
 */
public record ImportResult(Optional<TruthTableSpec> spec, List<ImportProblem> problems, int missingRows) {

    public ImportResult {
        problems = List.copyOf(problems);
    }
}
