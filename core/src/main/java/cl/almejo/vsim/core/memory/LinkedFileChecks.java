package cl.almejo.vsim.core.memory;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.simulation.LinkedFileProblem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Which linked memories of a document have no file to read: the check the AI's reads share with the editor. */
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

    /** The problem of {@code memory}'s link whose file is missing. */
    public static LinkedFileProblem missing(Component memory, Optional<Path> circuitFile) {
        return new LinkedFileProblem(memory.id().value(),
                MemoryLinkPaths.displayPath(MemoryLinkPaths.linkOf(memory), circuitFile),
                LinkedFileProblem.Kind.MISSING, "");
    }
}
