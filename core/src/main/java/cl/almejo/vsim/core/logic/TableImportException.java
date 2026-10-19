package cl.almejo.vsim.core.logic;

import java.io.IOException;

/** A file that cannot be read as a table at all (not an {@code .xlsx}, too large…), with its problem. */
public class TableImportException extends IOException {

    private static final long serialVersionUID = 1L;

    private final transient ImportProblem problem;

    public TableImportException(ImportProblem problem, Throwable cause) {
        super(problem.messageKey(), cause);
        this.problem = problem;
    }

    public ImportProblem problem() {
        return problem;
    }
}
