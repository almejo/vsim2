package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.io.DefinitionFormat;
import cl.almejo.vsim.core.io.LibraryProblem;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.Optional;
import java.util.function.Function;

/** The Problems-tab line for a library file that was skipped, or for a library directory that is gone. */
public final class LibraryProblemText {

    private LibraryProblemText() {
    }

    public static String describe(LibraryProblem problem) {
        return describe(problem, id -> Optional.empty());
    }

    /**
     * Like {@link #describe(LibraryProblem)}; a broken file named after a component the library has ({@code
     * libraryEntry} finds it by id: it was saved meanwhile to another file, since a broken file is never overwritten)
     * also says it can be deleted.
     */
    public static String describe(LibraryProblem problem, Function<String, Optional<DefinitionBundle>> libraryEntry) {
        String file = String.valueOf(problem.file().getFileName());
        String text = switch (problem.kind()) {
            case UNREADABLE -> Messages.format("library.problem.UNREADABLE", file, problem.detail());
            case INVALID -> Messages.format("library.problem.INVALID", file, problem.reason()
                    .map(reason -> Messages.readError(reason, problem.detail()))
                    .orElse(problem.detail()));
            case DUPLICATE_ID -> Messages.format("library.problem.DUPLICATE_ID", file, problem.detail());
            case DIRECTORY_GONE -> Messages.format("library.problem.directoryGoneWaiting", problem.file());
        };
        if (problem.kind() != LibraryProblem.Kind.UNREADABLE && problem.kind() != LibraryProblem.Kind.INVALID) {
            return text;
        }
        return idNamedBy(file).flatMap(libraryEntry)
                .map(bundle -> Messages.format("library.problem.deletable", text, bundle.root().name()))
                .orElse(text);
    }

    /** The id a file name stands for: {@code inverter} for {@code inverter.vsimcomponent.json}. */
    private static Optional<String> idNamedBy(String fileName) {
        int idLength = fileName.length() - DefinitionFormat.FILE_SUFFIX.length();
        boolean componentFile = idLength > 0 && fileName.regionMatches(true, idLength, DefinitionFormat.FILE_SUFFIX, 0,
                DefinitionFormat.FILE_SUFFIX.length());
        return componentFile ? Optional.of(fileName.substring(0, idLength)) : Optional.empty();
    }
}
