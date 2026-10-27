package cl.almejo.vsim.core.memory;

import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.io.CircuitFormat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Where a memory's linked file is, as paths: how a link is stored (relative to the circuit's folder, with {@code /}),
 * resolved, shown and made absolute or relative again for a whole document. Pure {@code java.nio}: the editor and
 * the AI's reads share it.
 */
public final class MemoryLinkPaths {

    private MemoryLinkPaths() {
    }

    /**
     * How a link to {@code file} is written: relative to the folder of {@code circuitFile} with {@code /} between
     * names («programas/rom.hex», «../datos/rom.hex»); absolute when the circuit is unsaved or the file is on another
     * drive.
     */
    public static String storedLinkPath(Path file, Optional<Path> circuitFile) {
        Path absolute = file.toAbsolutePath().normalize();
        Optional<Path> folder = circuitFile.map(circuit -> circuit.toAbsolutePath().normalize().getParent());
        if (folder.isPresent() && folder.get() != null) {
            try {
                return slashes(folder.get().relativize(absolute));
            } catch (IllegalArgumentException otherDrive) {
                return slashes(absolute);
            }
        }
        return slashes(absolute);
    }

    /** The file a stored link points at: {@code stored} against the folder of {@code circuitFile} when relative. */
    public static Path resolve(String stored, Optional<Path> circuitFile) {
        Path path = Path.of(stored);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        Optional<Path> folder = circuitFile.map(circuit -> circuit.toAbsolutePath().normalize().getParent());
        return (folder.isPresent() && folder.get() != null ? folder.get().resolve(path) : path.toAbsolutePath())
                .normalize();
    }

    /** The link as the user reads it: relative to the circuit when it is saved («programas/rom.hex»). */
    public static String displayPath(String stored, Optional<Path> circuitFile) {
        try {
            return storedLinkPath(resolve(stored, circuitFile), circuitFile);
        } catch (InvalidPathException unusable) {
            return stored;
        }
    }

    /** The file {@code memory} is linked to, empty when it is not linked (or the link is not a path here). */
    public static Optional<Path> linkedFile(Component memory, Optional<Path> circuitFile) {
        String link = linkOf(memory);
        if (link.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(resolve(link, circuitFile));
        } catch (InvalidPathException unusable) {
            return Optional.empty();
        }
    }

    /** Whether {@code first} and {@code second} name the same file (also through links, when both exist). */
    public static boolean isSameFile(Path first, Path second) {
        Path firstAbsolute = first.toAbsolutePath().normalize();
        Path secondAbsolute = second.toAbsolutePath().normalize();
        if (firstAbsolute.equals(secondAbsolute)) {
            return true;
        }
        try {
            return Files.exists(firstAbsolute) && Files.exists(secondAbsolute)
                    && Files.isSameFile(firstAbsolute, secondAbsolute);
        } catch (IOException exception) {
            return false;
        }
    }

    /**
     * Makes every link of {@code document}'s memories absolute against {@code circuitFile} (a circuit just read; not
     * an edit: no undo step, the document stays unmodified).
     */
    public static void absoluteLinks(CircuitDocument document, Optional<Path> circuitFile) {
        for (Component component : List.copyOf(document.components())) {
            Optional<Path> file = isMemory(component) ? linkedFile(component, circuitFile) : Optional.empty();
            if (file.isPresent()) {
                String absolute = storedLinkPath(file.get(), Optional.empty());
                if (!absolute.equals(linkOf(component))) {
                    document.replace(withLink(component, absolute));
                }
            }
        }
    }

    /** What {@link CircuitFormat} writes for each component: links relative to the circuit. */
    public static UnaryOperator<Component> relativeLinks(Optional<Path> circuitFile) {
        return component -> {
            Optional<Path> file = isMemory(component) ? linkedFile(component, Optional.empty()) : Optional.empty();
            return file.map(path -> withLink(component, storedLinkPath(path, circuitFile))).orElse(component);
        };
    }

    public static String linkOf(Component component) {
        return String.valueOf(component.parameters().getOrDefault(MemoryTypes.LINK, ""));
    }

    public static boolean isMemory(Component component) {
        return MemoryTypes.kindOf(component.typeId()).isPresent();
    }

    private static Component withLink(Component component, String link) {
        Map<String, Object> parameters = new HashMap<>(component.parameters());
        parameters.put(MemoryTypes.LINK, link);
        return component.withParameters(parameters);
    }

    private static String slashes(Path path) {
        return path.toString().replace('\\', '/');
    }

    /** Just the name of the file a stored link points at («rom.hex»): what the AI is shown instead of a user's path. */
    public static String fileName(String stored) {
        String normalized = stored.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }
}
