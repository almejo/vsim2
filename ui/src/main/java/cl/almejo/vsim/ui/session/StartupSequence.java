package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.ui.app.FileNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What to open at start (spec §4.1 "Order at start"), decided without Swing: the recovered copies first, then the
 * last session when the option is on and no circuit was given on the command line, then the command-line files. A
 * recovered copy replaces opening its original from the session or the command line and takes that file's views from
 * the session; of several copies of one file only the newest opens. Files are compared in any spelling ({@link FileNames#normalized}).
 */
public final class StartupSequence {

    /** One circuit to open at start. */
    public sealed interface Opening permits Recovered, FromSession {
    }

    /** A recovered copy, with the views its file had in the last session (empty: one window, Recientes' state). */
    public record Recovered(RecoveryCopy copy, List<WindowState> windows) implements Opening {
        public Recovered {
            windows = List.copyOf(windows);
        }
    }

    /** A file of the last session with the state of each of its views (empty: one window, Recientes' state). */
    public record FromSession(Path path, List<WindowState> windows) implements Opening {
        public FromSession {
            windows = List.copyOf(windows);
        }
    }

    /**
     * @param openings         recovered copies and session files, in that order
     * @param missing          session files that no longer exist, to be listed once
     * @param commandLineFiles the command-line circuits still to open the usual way
     */
    public record Plan(List<Opening> openings, List<Path> missing, List<Path> commandLineFiles) {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(StartupSequence.class);

    private StartupSequence() {
    }

    public static Plan plan(List<RecoveryCopy> recovered, boolean restoreSession, List<SessionDocument> session,
                            List<Path> commandLineFiles, Predicate<Path> exists) {
        Map<Path, List<WindowState>> sessionWindows = new LinkedHashMap<>();
        if (restoreSession && commandLineFiles.isEmpty()) {
            for (SessionDocument document : session) {
                sessionWindows.putIfAbsent(FileNames.normalized(document.path()), document.windows());
            }
        }
        Set<Path> recoveredFiles = new HashSet<>();
        List<Opening> openings = new ArrayList<>();
        for (RecoveryCopy copy : newestOfEachOriginal(recovered)) {
            Optional<Path> original = copy.originalPath().map(FileNames::normalized);
            original.ifPresent(recoveredFiles::add);
            openings.add(new Recovered(copy, original.map(sessionWindows::get).orElse(List.of())));
        }
        List<Path> missing = new ArrayList<>();
        sessionWindows.forEach((path, windows) -> {
            if (recoveredFiles.contains(path)) {
                return;
            }
            if (exists.test(path)) {
                openings.add(new FromSession(path, windows));
            } else {
                missing.add(path);
            }
        });
        List<Path> files = commandLineFiles.stream()
                .filter(file -> !recoveredFiles.contains(FileNames.normalized(file)))
                .toList();
        return new Plan(List.copyOf(openings), List.copyOf(missing), files);
    }

    /**
     * The copies to open: of several copies of one original only the newest (the first one wins a tie), since two
     * would be two documents saving to the same file. The others are not opened and stay on disk for the next start.
     */
    private static List<RecoveryCopy> newestOfEachOriginal(List<RecoveryCopy> recovered) {
        Map<Path, RecoveryCopy> newest = new HashMap<>();
        for (RecoveryCopy copy : recovered) {
            copy.originalPath().map(FileNames::normalized).ifPresent(original -> newest.merge(original, copy,
                    (kept, offered) -> offered.savedAt().isAfter(kept.savedAt()) ? offered : kept));
        }
        List<RecoveryCopy> result = new ArrayList<>();
        for (RecoveryCopy copy : recovered) {
            Optional<Path> original = copy.originalPath().map(FileNames::normalized);
            if (original.isEmpty() || newest.get(original.get()) == copy) {
                result.add(copy);
            } else {
                LOGGER.info("Not opening the recovery copy {}: {} has a newer one; it stays for the next start",
                        copy.content(), original.get());
            }
        }
        return result;
    }
}
