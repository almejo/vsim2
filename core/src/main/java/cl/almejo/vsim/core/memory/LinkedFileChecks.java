package cl.almejo.vsim.core.memory;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.simulation.LinkedFileProblem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Which linked memories of a document have no file to read, for a circuit no editor watches, and which of the editor's
 * reported ones are still true.
 */
public final class LinkedFileChecks {

    private LinkedFileChecks() {
    }

    /**
     * One problem for each RAM or ROM linked to a file that is not there (or is not a regular file), in document order.
     * Only looks at the file system; whether a present file can be parsed is the editor's watcher's to say.
     */
    public static List<LinkedFileProblem> missing(CircuitDocument document, Optional<Path> circuitFile) {
        List<LinkedFileProblem> problems = new ArrayList<>();
        for (Component component : document.components()) {
            if (!MemoryLinkPaths.isMemory(component)) {
                continue;
            }
            Optional<Path> file = MemoryLinkPaths.linkedFile(component, circuitFile);
            if (file.isPresent() && !Files.isRegularFile(file.get())) {
                problems.add(missing(component, circuitFile));
            }
        }
        return problems;
    }

    /**
     * The {@code reported} problems (what the editor's watcher last found) that are still true of {@code document}, by
     * element id: a memory that was deleted, or whose link was removed or changed, drops out at once, without waiting
     * for the next look at the files.
     */
    public static List<LinkedFileProblem> stillTrue(CircuitDocument document, Optional<Path> circuitFile,
                                                    List<LinkedFileProblem> reported) {
        return reported.stream().filter(problem -> document.component(new ElementId(problem.elementId()))
                        .flatMap(memory -> MemoryLinkPaths.linkedFile(memory, circuitFile).map(file ->
                                MemoryLinkPaths.displayPath(MemoryLinkPaths.linkOf(memory), circuitFile)))
                        .filter(problem.link()::equals).isPresent())
                .sorted(Comparator.comparing(LinkedFileProblem::elementId)).toList();
    }

    /** The problem of {@code memory}'s link whose file is missing. */
    public static LinkedFileProblem missing(Component memory, Optional<Path> circuitFile) {
        return new LinkedFileProblem(memory.id().value(),
                MemoryLinkPaths.displayPath(MemoryLinkPaths.linkOf(memory), circuitFile),
                LinkedFileProblem.Kind.MISSING, "");
    }
}
