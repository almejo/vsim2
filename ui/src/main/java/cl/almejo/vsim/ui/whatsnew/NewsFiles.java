package cl.almejo.vsim.ui.whatsnew;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** The news files bundled with VSim, one per release that has news: {@code /whatsnew/<version>.txt}. */
final class NewsFiles {

    private static final String FOLDER = "/whatsnew/";
    private static final String EXTENSION = ".txt";

    private NewsFiles() {
    }

    /** The news of {@code version}, when it has a file. */
    static Optional<NewsFile> load(String version) {
        String resource = FOLDER + version + EXTENSION;
        try (InputStream stream = NewsFiles.class.getResourceAsStream(resource)) {
            if (stream == null) {
                return Optional.empty();
            }
            return Optional.of(NewsFile.parse(version,
                    new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines().toList()));
        } catch (IOException exception) {
            throw new UncheckedIOException("could not read " + resource, exception);
        }
    }

    /**
     * The news of the running version: its own file, or, in a development build between two releases (which still
     * says the last released version), the file being written for the next release. Empty without a version.
     */
    static Optional<NewsFile> forRunning(String runningVersion) {
        Optional<ReleaseVersion> running = ReleaseVersion.parse(runningVersion);
        if (running.isEmpty()) {
            return Optional.empty();
        }
        Optional<NewsFile> own = load(running.get().toString());
        return own.isPresent() ? own : load(running.get().next().toString());
    }
}
