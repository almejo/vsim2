package cl.almejo.vsim.ui.session;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * A copy left by a run that did not close properly: its circuit file and what its metadata says. When the metadata
 * cannot be read, the name is the file's stem and the time is the circuit file's modification time.
 */
public record RecoveryCopy(Path content, Optional<Path> originalPath, String displayName, Instant savedAt) {

    /** The {@code .properties} file next to {@link #content()}. */
    public Path metadata() {
        return RecoveryStore.metadataFor(content);
    }
}
