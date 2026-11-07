package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.core.io.LibraryProblem;
import cl.almejo.vsim.core.library.DefinitionBundle;

import java.util.List;
import java.util.Optional;

/**
 * The AI's view of the component library's load problems, which the Problemas list shows under every circuit (spec
 * §5.6): a file skipped, or the library folder gone. They name no part of the circuit; the net id is {@code library:}
 * and the file's path.
 */
final class LibraryProblemViews {

    /** The start of the net id of a library problem: it belongs to no net. */
    static final String NET_PREFIX = "library:";

    private LibraryProblemViews() {
    }

    /** The kind the AI reads for {@code problem}: {@code LIBRARY_} and its kind. */
    static String kindOf(LibraryProblem problem) {
        return "LIBRARY_" + problem.kind().name();
    }

    static ProblemView view(LibraryProblem problem, List<DefinitionBundle> library) {
        return new ProblemView(kindOf(problem), true, NET_PREFIX + problem.file(), message(problem, library),
                List.of(), List.of(), List.of());
    }

    private static String message(LibraryProblem problem, List<DefinitionBundle> library) {
        String file = String.valueOf(problem.file().getFileName());
        String text = switch (problem.kind()) {
            case UNREADABLE -> "The library file " + file + " could not be read (" + problem.detail()
                    + "): its component is not in «Mis componentes»";
            case INVALID -> "The library file " + file + " is not a valid component ("
                    + problem.reason().map(reason -> reason.name() + ": ").orElse("") + problem.detail()
                    + "): its component is not in «Mis componentes»";
            case DUPLICATE_ID -> "The library file " + file + " repeats the id \"" + problem.detail()
                    + "\" of another file: it is skipped";
            case DIRECTORY_GONE -> "The library folder " + problem.file() + " no longer exists: VSim follows its"
                    + " changes again once it is back";
        };
        return deletableBecauseOf(problem, library)
                .map(bundle -> text + ". The library has the component " + bundle.root().name()
                        + " from another file, so this file can be deleted")
                .orElse(text);
    }

    /** A broken file named after a component the library has (saved meanwhile to another file). */
    private static Optional<DefinitionBundle> deletableBecauseOf(LibraryProblem problem,
                                                                 List<DefinitionBundle> library) {
        if (problem.kind() != LibraryProblem.Kind.UNREADABLE && problem.kind() != LibraryProblem.Kind.INVALID) {
            return Optional.empty();
        }
        return problem.componentIdNamed().flatMap(id -> library.stream()
                .filter(bundle -> bundle.root().id().equals(id)).findFirst());
    }
}
