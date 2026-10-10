package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.library.DefinitionBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The user's component library on disk (spec §5.6): one {@code <id>.vsimcomponent.json} per definition. Reading never
 * fails as a whole: a bad file is skipped, logged and reported as a {@link LibraryProblem}. Writes are atomic.
 */
public final class LibraryDirectory {

    private static final Logger LOGGER = LoggerFactory.getLogger(LibraryDirectory.class);

    /** Turns a file's text into a bundle; a seam so tests can make reading fail in unexpected ways. */
    @FunctionalInterface
    interface BundleReader {
        DefinitionBundle read(String json) throws CircuitFormatException;
    }

    private final Path directory;
    private final DefinitionFormat format;
    private final BundleReader reader;

    public LibraryDirectory(Path directory, ComponentTypeRegistry registry) {
        this(directory, new DefinitionFormat(registry));
    }

    private LibraryDirectory(Path directory, DefinitionFormat format) {
        this(directory, format, format::read);
    }

    LibraryDirectory(Path directory, DefinitionFormat format, BundleReader reader) {
        this.directory = directory.toAbsolutePath().normalize();
        this.format = format;
        this.reader = reader;
    }

    public Path directory() {
        return directory;
    }

    /** The file a definition with this id is written to when no other file holds it. */
    public Path fileOf(String id) {
        return directory.resolve(id + DefinitionFormat.FILE_SUFFIX);
    }

    public static boolean isComponentFile(Path path) {
        Path fileName = path.getFileName();
        return fileName != null
                && fileName.toString().toLowerCase(Locale.ROOT).endsWith(DefinitionFormat.FILE_SUFFIX);
    }

    /**
     * Reads every component file. When two files hold the same id, the one named after the id wins (otherwise the
     * first by file name) and the others are {@link LibraryProblem.Kind#DUPLICATE_ID} problems.
     */
    public LibraryLoad load() {
        if (!Files.isDirectory(directory)) {
            return new LibraryLoad(List.of(), List.of());
        }
        List<Path> files;
        try (Stream<Path> listing = Files.list(directory)) {
            files = listing.filter(LibraryDirectory::isComponentFile).sorted().toList();
        } catch (IOException exception) {
            LOGGER.warn("Could not list the component library {}", directory, exception);
            return new LibraryLoad(List.of(), List.of(LibraryProblem.unreadable(directory, exception)));
        } catch (UncheckedIOException exception) {
            LOGGER.warn("Could not list the component library {}", directory, exception);
            return new LibraryLoad(List.of(), List.of(LibraryProblem.unreadable(directory, exception.getCause())));
        }
        List<LibraryProblem> problems = new ArrayList<>();
        List<Loaded> loaded = new ArrayList<>();
        for (Path file : files) {
            try {
                loaded.add(new Loaded(file, read(file)));
            } catch (IOException exception) {
                problems.add(LibraryProblem.unreadable(file, exception));
            } catch (CircuitFormatException exception) {
                problems.add(LibraryProblem.invalid(file, exception));
            } catch (RuntimeException exception) {
                LOGGER.warn("Reading the library file {} failed unexpectedly", file, exception);
                problems.add(LibraryProblem.invalid(file, exception));
            }
        }
        loaded.sort(Comparator.comparing((Loaded entry) -> !entry.file().equals(fileOf(entry.bundle().root().id())))
                .thenComparing(Loaded::file));
        Map<String, DefinitionBundle> bundles = new LinkedHashMap<>();
        for (Loaded entry : loaded) {
            String id = entry.bundle().root().id();
            if (bundles.containsKey(id)) {
                problems.add(LibraryProblem.duplicate(entry.file(), id));
            } else {
                bundles.put(id, entry.bundle());
            }
        }
        problems.sort(Comparator.comparing(LibraryProblem::file));
        for (LibraryProblem problem : problems) {
            LOGGER.warn("Skipped library file {}: {} {} {}", problem.file(), problem.kind(),
                    problem.reason().map(Enum::name).orElse(""), problem.detail());
        }
        return new LibraryLoad(List.copyOf(bundles.values()), problems);
    }

    public DefinitionBundle read(Path file) throws IOException, CircuitFormatException {
        return reader.read(Files.readString(file));
    }

    /**
     * Writes {@code bundle} over the file that already holds its id, otherwise to {@link #fileOf}; creates the
     * directory. A file named after the id that holds something else (another component, a broken file) is never
     * overwritten: the bundle goes to the first free {@code <id>-2}, {@code <id>-3}, … file instead.
     */
    public Path write(DefinitionBundle bundle) throws IOException {
        Files.createDirectories(directory);
        String id = bundle.root().id();
        Path target = locate(id).orElseGet(() -> freeFileFor(id));
        AtomicFiles.writeString(target, format.write(bundle));
        return target;
    }

    /** Deletes every file holding {@code id}, so a duplicate does not take its place; false when there is none. */
    public boolean delete(String id) throws IOException {
        boolean deleted = false;
        for (Path file : holders(id)) {
            deleted |= Files.deleteIfExists(file);
        }
        return deleted;
    }

    /** Same id, new visible name, same file. */
    public DefinitionBundle rename(DefinitionBundle bundle, String newName) throws IOException {
        DefinitionBundle renamed = new DefinitionBundle(bundle.root().withName(newName), bundle.dependencies());
        write(renamed);
        return renamed;
    }

    public void export(DefinitionBundle bundle, Path target) throws IOException {
        AtomicFiles.writeString(target.toAbsolutePath(), format.write(bundle));
    }

    /** The file that holds {@code id}: {@link #fileOf} when it holds it, otherwise any component file with that id. */
    public Optional<Path> locate(String id) {
        Path named = fileOf(id);
        if (Files.isRegularFile(named) && holds(named, id)) {
            return Optional.of(named);
        }
        return holders(id).stream().findFirst();
    }

    /** Every component file that holds {@code id}, by file name. */
    private List<Path> holders(String id) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> listing = Files.list(directory)) {
            return listing.filter(LibraryDirectory::isComponentFile)
                    .filter(Files::isRegularFile)
                    .sorted()
                    .filter(file -> holds(file, id))
                    .toList();
        } catch (IOException | UncheckedIOException exception) {
            LOGGER.warn("Could not list the component library {}", directory, exception);
            return List.of();
        }
    }

    private Path freeFileFor(String id) {
        Path named = fileOf(id);
        for (int suffix = 2; Files.exists(named, LinkOption.NOFOLLOW_LINKS); suffix++) {
            named = directory.resolve(id + "-" + suffix + DefinitionFormat.FILE_SUFFIX);
        }
        return named;
    }

    private boolean holds(Path file, String id) {
        try {
            return read(file).root().id().equals(id);
        } catch (IOException | CircuitFormatException | RuntimeException exception) {
            return false;
        }
    }

    private record Loaded(Path file, DefinitionBundle bundle) {
    }
}
