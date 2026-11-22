package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.app.OpenDocument;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * What «Guardar como» does with the file the user chose, apart from the dialogs: a file that another open document
 * is backed by is refused (two documents on one file would overwrite each other), and the refusal comes before the
 * question about replacing an existing file, which would be a question about something that cannot happen.
 */
final class SaveAsTargets {

    private SaveAsTargets() {
    }

    /**
     * True when {@code target} is free for {@code asking}. Otherwise {@code refuse} runs with the target (it tells
     * the user) and the result is false. {@code documentBackedBy} gives another open document backed by a file.
     */
    static boolean accepts(Path target, OpenDocument asking,
                           BiFunction<Path, OpenDocument, Optional<OpenDocument>> documentBackedBy,
                           Consumer<Path> refuse) {
        if (documentBackedBy.apply(target, asking).isPresent()) {
            refuse.accept(target);
            return false;
        }
        return true;
    }

    /** {@code target} when it is free ({@link #accepts}) and {@code confirmOverwrite} agrees; asked in that order. */
    static Optional<Path> confirmed(Path target, OpenDocument asking,
                                    BiFunction<Path, OpenDocument, Optional<OpenDocument>> documentBackedBy,
                                    Consumer<Path> refuse, Predicate<Path> confirmOverwrite) {
        return accepts(target, asking, documentBackedBy, refuse) && confirmOverwrite.test(target)
                ? Optional.of(target)
                : Optional.empty();
    }
}
