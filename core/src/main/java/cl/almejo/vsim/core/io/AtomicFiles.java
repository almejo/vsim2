package cl.almejo.vsim.core.io;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Writes a whole text file so that a reader never sees half of it: a uniquely named temporary file next to the
 * target, then a move over it (atomic where the file system allows it). On failure the target is unchanged and the
 * temporary file is gone. A replaced file keeps its POSIX permissions.
 */
public final class AtomicFiles {

    private AtomicFiles() {
    }

    public static void writeString(Path target, String content) throws IOException {
        Path absolute = target.toAbsolutePath();
        Path temporary = absolute.getParent().resolve(".vsim2-" + UUID.randomUUID() + ".tmp");
        try {
            Files.createFile(temporary);
            copyPermissions(absolute, temporary);
            Files.writeString(temporary, content);
            try {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Keeps the permissions of the file being replaced; a new file keeps the umask-based ones of createFile. */
    private static void copyPermissions(Path existing, Path temporary) throws IOException {
        if (Files.isRegularFile(existing) && FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(temporary, Files.getPosixFilePermissions(existing));
        }
    }
}
