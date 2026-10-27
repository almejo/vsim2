package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.awaitSimulation;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** The watcher of a document's linked memory files, polled by hand (no timer, no focus). */
class LinkedMemoryWatcherTest {

    @TempDir
    Path folder;

    private final OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final LinkedMemoryWatcher watcher = new LinkedMemoryWatcher(document);
    private int problemChanges;

    @AfterEach
    void close() {
        document.close();
    }

    private Path program(String text) throws IOException {
        Path file = folder.resolve("rom.hex");
        Files.writeString(file, text);
        return file;
    }

    /** Rewrites the file and moves its modification time, as a save in another program a moment later does. */
    private static void edit(Path file, String text, int secondsLater) throws IOException {
        Files.writeString(file, text);
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(secondsLater)));
    }

    private Component linkedRom(Path file, String contents) {
        return place(document, StandardComponentTypes.ROM, 0, 0,
                Map.of(MemoryTypes.WORDS, "16", MemoryTypes.CONTENTS, contents, MemoryTypes.LINK, file.toString()));
    }

    private MemoryContents contentsOf(Component component) {
        return MemoryContents.decode((String) document.document().component(component.id()).orElseThrow()
                .parameters().get(MemoryTypes.CONTENTS));
    }

    @Test
    void aFileThatChangedIsReloadedAsOneUndoStep() throws IOException {
        Path file = program("01\n");
        Component rom = linkedRom(file, "@0 1");
        watcher.poll();
        long changes = document.changeCount();

        edit(file, "01 02 03\n", 5);
        watcher.poll();

        assertThat(contentsOf(rom)).isEqualTo(MemoryContents.decode("@0 1 2 3"));
        assertThat(document.changeCount()).isEqualTo(changes + 1);
        assertThat(document.history().undoDescription()).contains("command.reloadMemory");
        document.history().undo();
        assertThat(contentsOf(rom)).isEqualTo(MemoryContents.decode("@0 1"));
    }

    @Test
    void anUnchangedFileChangesNothing() throws IOException {
        Path file = program("01\n");
        linkedRom(file, "@0 1");
        long changes = document.changeCount();
        watcher.poll();
        watcher.poll();
        assertThat(document.changeCount()).isEqualTo(changes);
    }

    @Test
    void aReloadWhileSimulatingReachesTheRunningMemory() throws Exception {
        Path file = program("01\n");
        Component rom = linkedRom(file, "@0 1");
        awaitSimulation(document);
        watcher.poll();

        edit(file, "0A 0B\n", 5);
        watcher.poll();
        awaitSimulation(document);

        assertThat(document.latestSnapshot().running()).isTrue();
        assertThat(document.requestMemoryContents(rom.id()).get()).contains(MemoryContents.decode("@0 A B"));
    }

    @Test
    void aDeletedFileIsAProblemAndTheCircuitKeepsItsCopy() throws IOException {
        Path file = program("01\n");
        Component rom = linkedRom(file, "@0 1");
        watcher.addProblemsListener(() -> problemChanges++);
        watcher.poll();
        assertThat(watcher.problem(rom.id())).isEmpty();

        Files.delete(file);
        watcher.poll();

        assertThat(watcher.problem(rom.id())).contains(Messages.format("memory.link.missing",
                file.toAbsolutePath().toString().replace('\\', '/')));
        assertThat(contentsOf(rom)).isEqualTo(MemoryContents.decode("@0 1"));
        assertThat(problemChanges).isEqualTo(1);
        assertThat(document.latestSnapshot().failure()).isEmpty();

        program("01\n");
        watcher.poll();
        assertThat(watcher.problem(rom.id())).isEmpty();
        assertThat(problemChanges).isEqualTo(2);
    }

    @Test
    void aFileThatIsNotUtf8IsAProblemAndNothingIsReloaded() throws IOException {
        Path file = program("01\n");
        Component rom = linkedRom(file, "@0 1");
        watcher.poll();
        long changes = document.changeCount();

        Files.write(file, new byte[]{'0', '2', (byte) 0xE9, '\n'});
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(5)));
        watcher.poll();

        assertThat(watcher.problem(rom.id())).hasValueSatisfying(text -> assertThat(text).contains("UTF-8"));
        assertThat(document.changeCount()).isEqualTo(changes);
    }

    @Test
    void aMemoryThatIsUnlinkedOrRemovedHasNoProblem() throws IOException {
        Path file = program("01\n");
        Component rom = linkedRom(file, "@0 1");
        Files.delete(file);
        watcher.poll();
        assertThat(watcher.problem(rom.id())).isPresent();

        document.history().undo();
        watcher.poll();

        assertThat(watcher.problem(rom.id())).isEqualTo(Optional.empty());
    }
}
