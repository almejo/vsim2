package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.io.LibraryDirectory;
import cl.almejo.vsim.core.io.LibraryProblem;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.ui.app.AppPreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LibraryServiceTest {

    @TempDir
    Path directory;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final ComponentLibrary library = new ComponentLibrary();
    private final AtomicInteger uiRuns = new AtomicInteger();
    private final DefinitionBundle inverter = new DefinitionBundle(ExampleDefinitions.inverter(), List.of());
    private LibraryService service;

    private LibraryService serviceOn(Path libraryDirectory) {
        preferences.setLibraryDirectory(Optional.of(libraryDirectory));
        // Runs the hand-off inline (on the reload thread), against ComponentLibrary's EDT rule: fine for a test
        // library nobody else touches, and it lets the test count hand-offs without an EDT.
        service = new LibraryService(preferences, library, registry, task -> {
            uiRuns.incrementAndGet();
            task.run();
        });
        return service;
    }

    @AfterEach
    void close() {
        if (service != null) {
            service.close();
        }
    }

    @Test
    void startLoadsTheDirectoryAndReportsBadFiles() throws Exception {
        Path components = directory.resolve("components");
        new LibraryDirectory(components, registry).write(inverter);
        Files.writeString(components.resolve("broken.vsimcomponent.json"), "nope");
        AtomicInteger problemChanges = new AtomicInteger();
        LibraryService started = serviceOn(components);
        started.addProblemsListener(problemChanges::incrementAndGet);

        started.start();

        assertThat(library.entries()).containsExactly(inverter);
        assertThat(started.problems()).extracting(LibraryProblem::kind).containsExactly(LibraryProblem.Kind.INVALID);
        assertThat(problemChanges).hasValue(1);
    }

    @Test
    void saveRenameExportAndRemoveKeepTheFilesAndTheLibraryInStep() throws Exception {
        Path components = directory.resolve("components");
        LibraryService files = serviceOn(components);
        Path file = components.resolve("inverter.vsimcomponent.json");

        files.save(inverter);
        assertThat(file).isRegularFile();
        assertThat(library.find("inverter")).contains(inverter);
        assertThat(files.fileOf("inverter")).isEqualTo(file.toAbsolutePath().normalize());

        DefinitionBundle renamed = files.rename("inverter", "Negador");
        assertThat(library.find("inverter")).contains(renamed);
        assertThat(files.read(file)).isEqualTo(renamed);

        Path exported = directory.resolve("negador.vsimcomponent.json");
        files.export("inverter", exported);
        assertThat(files.read(exported)).isEqualTo(renamed);

        files.remove("inverter");
        assertThat(file).doesNotExist();
        assertThat(library.entries()).isEmpty();
    }

    @Test
    void filesAddedOrDeletedOutsideReachTheLibraryThroughTheUiExecutor() throws Exception {
        Path components = directory.resolve("components");
        LibraryService watching = serviceOn(components);
        watching.start();
        int before = uiRuns.get();

        new LibraryDirectory(components, registry).write(inverter);
        LibraryWatcherTest.eventually(() -> library.find("inverter").isPresent());
        assertThat(uiRuns.get()).isGreaterThan(before);

        Files.delete(components.resolve("inverter.vsimcomponent.json"));
        LibraryWatcherTest.eventually(() -> library.entries().isEmpty());
    }

    @Test
    void choosingAnotherDirectoryReloadsFromIt() throws Exception {
        Path first = directory.resolve("first");
        Path second = directory.resolve("second");
        new LibraryDirectory(second, registry).write(inverter);
        LibraryService following = serviceOn(first);
        following.start();
        assertThat(library.entries()).isEmpty();

        preferences.setLibraryDirectory(Optional.of(second));

        assertThat(following.directory()).isEqualTo(second.toAbsolutePath().normalize());
        assertThat(library.entries()).containsExactly(inverter);
    }

    @Test
    void aClosedServiceStopsWatchingAndFollowingThePreferences() throws Exception {
        Path components = directory.resolve("components");
        int preferenceListeners = preferences.listenerCount();
        LibraryService closing = serviceOn(components);
        closing.start();
        Thread watchingThread = closing.watcher().orElseThrow().watchingThread();

        closing.close();

        assertThat(preferences.listenerCount()).isEqualTo(preferenceListeners);
        assertThat(closing.watcher()).isEmpty();
        watchingThread.join(TimeUnit.SECONDS.toMillis(5));
        assertThat(watchingThread.isAlive()).isFalse();
        new LibraryDirectory(components, registry).write(inverter);
        preferences.setLibraryDirectory(Optional.of(directory.resolve("other")));
        Thread.sleep(500);
        assertThat(library.entries()).isEmpty();
        assertThat(closing.directory()).isEqualTo(components.toAbsolutePath().normalize());
    }

    @Test
    void startReadsTheDirectoryChosenAfterTheServiceWasMade() throws Exception {
        Path first = directory.resolve("first");
        Path second = directory.resolve("second");
        new LibraryDirectory(second, registry).write(inverter);
        LibraryService late = serviceOn(first);
        preferences.setLibraryDirectory(Optional.of(second));

        late.start();

        assertThat(late.directory()).isEqualTo(second.toAbsolutePath().normalize());
        assertThat(library.entries()).containsExactly(inverter);
    }

    @Test
    void aLoadHandedOverAfterCloseIsDropped() throws Exception {
        Path components = directory.resolve("components");
        preferences.setLibraryDirectory(Optional.of(components));
        Queue<Runnable> handedOver = new ConcurrentLinkedQueue<>();
        service = new LibraryService(preferences, library, registry, handedOver::add);
        service.start();

        new LibraryDirectory(components, registry).write(inverter);
        LibraryWatcherTest.eventually(() -> !handedOver.isEmpty());
        service.close();
        handedOver.forEach(Runnable::run);

        assertThat(library.entries()).isEmpty();
    }

    /** Review M8: a watcher load that began before a save must not hide the saved component. */
    @Test
    void aLoadThatBeganBeforeASaveDoesNotHideTheSavedComponent() throws Exception {
        Path components = directory.resolve("components");
        preferences.setLibraryDirectory(Optional.of(components));
        Queue<Runnable> handedOver = new ConcurrentLinkedQueue<>();
        service = new LibraryService(preferences, library, registry, handedOver::add);
        service.start();
        DefinitionBundle external = new DefinitionBundle(ExampleDefinitions.doubleInverter(),
                List.of(ExampleDefinitions.inverter()));

        new LibraryDirectory(components, registry).write(external); // an outside change: the watcher loads it
        LibraryWatcherTest.eventually(() -> !handedOver.isEmpty());
        List<Runnable> staleLoads = List.copyOf(handedOver);
        handedOver.clear();
        service.save(inverter); // on the EDT, while the stale load waits to be applied
        staleLoads.forEach(Runnable::run);

        assertThat(library.find("inverter")).contains(inverter);

        // the save's own watch event brings a fresh load with both
        LibraryWatcherTest.eventually(() -> {
            Runnable next = handedOver.poll();
            if (next != null) {
                next.run();
            }
            return library.find(ExampleDefinitions.DOUBLE_INVERTER).isPresent();
        });
        assertThat(library.find("inverter")).contains(inverter);
    }

    @Test
    void aDeletedLibraryDirectoryIsReportedUntilAnotherDirectoryIsWatched() throws Exception {
        Path components = directory.resolve("components");
        LibraryService watching = serviceOn(components);
        watching.start();

        Files.delete(components);

        LibraryWatcherTest.eventually(() -> !watching.problems().isEmpty());
        assertThat(watching.problems()).containsExactly(
                LibraryProblem.directoryGone(components.toAbsolutePath().normalize()));
        watching.reload();
        assertThat(watching.problems()).extracting(LibraryProblem::kind)
                .containsExactly(LibraryProblem.Kind.DIRECTORY_GONE);

        preferences.setLibraryDirectory(Optional.of(directory.resolve("other")));

        assertThat(watching.problems()).isEmpty();
    }
}
