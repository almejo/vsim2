package cl.almejo.vsim.ui.session;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** What a recovery copy remembers besides the circuit: the file it belongs to (if any), its name and when it was written. */
public record RecoveryMetadata(Optional<Path> originalPath, String displayName, Instant savedAt) {

    public RecoveryMetadata {
        Objects.requireNonNull(originalPath, "originalPath");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(savedAt, "savedAt");
    }
}
