package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.ui.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads the circuit files the user asks for and, when one cannot be read, tells the user why ({@link FileProblem})
 * through the {@link FileProblemDialog} seam, besides logging it. Inside {@link #collecting} the problems wait and are
 * shown as one message at the end (the start, with a session of several files or several on the command line).
 */
final class OpeningFiles {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpeningFiles.class);

    private final ComponentTypeRegistry registry;
    private final FileProblemDialog dialog;
    /** Non-null while collecting. */
    private List<FileProblem> collected;

    OpeningFiles(ComponentTypeRegistry registry, FileProblemDialog dialog) {
        this.registry = registry;
        this.dialog = dialog;
    }

    /** The document of {@code file}; empty (after telling the user) when it cannot be read. */
    Optional<OpenDocument> read(Path file, Component requester) {
        try {
            return Optional.of(OpenDocument.open(file, registry));
        } catch (CircuitFormatException exception) {
            report(file, exception, requester);
            return Optional.empty();
        }
    }

    /** Logs and tells the user (or keeps for the end of {@link #collecting}) why {@code file} did not open. */
    void report(Path file, CircuitFormatException exception, Component requester) {
        LOGGER.warn("Could not open {}", file, exception);
        FileProblem problem = FileProblem.of(file, exception);
        if (collected != null) {
            collected.add(problem);
        } else {
            dialog.show(requester, Messages.text("fileproblem.title"), problem.message(), problem.details());
        }
    }

    /** Runs {@code openings}; the problems met meanwhile come out as one message, even if it throws. */
    void collecting(Runnable openings) {
        List<FileProblem> previous = collected;
        List<FileProblem> mine = new ArrayList<>();
        collected = mine;
        try {
            openings.run();
        } finally {
            collected = previous;
            showCollected(mine);
        }
    }

    private void showCollected(List<FileProblem> problems) {
        if (problems.isEmpty()) {
            return;
        }
        if (problems.size() == 1) {
            dialog.show(null, Messages.text("fileproblem.title"), problems.get(0).message(), problems.get(0).details());
            return;
        }
        dialog.show(null, Messages.text("fileproblem.title.many"),
                String.join("\n\n", problems.stream().map(FileProblem::message).toList()),
                String.join("\n\n", problems.stream().map(FileProblem::details).toList()));
    }
}
