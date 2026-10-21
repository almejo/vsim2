package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;

import java.util.List;

/**
 * The problems of the latest published simulation of one circuit. {@code upToDate} is false while the simulation has
 * not yet caught up with the last edit (read again in a moment); {@code simulationTime} is a string so a long time
 * loses nothing in JSON.
 */
public record ProblemsView(DocumentId document, DocumentVersion version, String simulationTime, boolean upToDate,
                           List<ProblemView> problems) {

    public ProblemsView {
        problems = List.copyOf(problems);
    }
}
