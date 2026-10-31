package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/** «Guardar salida» (spec §8.9): the raw bytes appended to a .txt off the EDT, the file truncated on «Reiniciar». */
class TerminalOutputRecorderTest {

    /** An executor that only runs its tasks when asked: nothing is written where the bytes arrive. */
    private static final class LaterExecutor implements Executor {
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        void runAll() {
            List<Runnable> pending = List.copyOf(tasks);
            tasks.clear();
            pending.forEach(Runnable::run);
        }
    }

    @TempDir
    Path folder;

    private OpenDocument document;
    private ComponentType type;
    private final FakeTerminals terminals = new FakeTerminals();
    private final LaterExecutor writer = new LaterExecutor();
    private final List<String> problems = new ArrayList<>();
    private TerminalFeeds feeds;
    private TerminalOutputRecorder recorder;

    @BeforeEach
    void create() {
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        type = document.registry().require(StandardComponentTypes.TERMINAL);
        feeds = new TerminalFeeds(terminals);
    }

    @AfterEach
    void close() {
        if (recorder != null) {
            recorder.dispose();
        }
        document.close();
    }

    private Component terminalSavingTo(Path file) {
        Map<String, Object> parameters = new HashMap<>(Map.of(TerminalTypes.SAVE_OUTPUT,
                TerminalTypes.SAVE_OUTPUT_TO_FILE, TerminalTypes.OUTPUT_FILE, file.toString()));
        Component component = type.newComponent(document.document().newComponentId(), new GridPoint(0, 0),
                Rotation.R0, parameters, "consola");
        document.execute(new AddElementsCommand(List.of(component)));
        terminals.add(component.id().value(), 2, 10);
        return component;
    }

    private void start() {
        recorder = new TerminalOutputRecorder(document, feeds, writer, problems::add);
    }

    private void arrive(Component component, String text) {
        terminals.terminal(component.id().value()).write(text);
        terminals.publish(component.id().value());
        feeds.poll();
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    void theBytesAreAppendedOnlyWhenTheWriterRuns() throws IOException {
        Path file = folder.resolve("salida.txt");
        Component terminal = terminalSavingTo(file);
        start();

        arrive(terminal, "? 5\n");
        assertThat(file).as("nothing is written where the frames arrive").doesNotExist();
        writer.runAll();
        assertThat(read(file)).isEqualTo("? 5\n");

        arrive(terminal, "fact(5)=120\n");
        writer.runAll();
        assertThat(read(file)).isEqualTo("? 5\nfact(5)=120\n");
    }

    @Test
    void aRestartTruncatesTheFile() throws IOException {
        Path file = folder.resolve("salida.txt");
        Component terminal = terminalSavingTo(file);
        start();
        arrive(terminal, "primera vuelta");
        writer.runAll();

        terminals.terminal(terminal.id().value()).restart();
        arrive(terminal, "otra");
        writer.runAll();

        assertThat(read(file)).isEqualTo("otra");
    }

    @Test
    void anUndefinedByteIsSavedAsTheQuestionMarkTheScreenShows() throws IOException {
        Path file = folder.resolve("salida.txt");
        Component terminal = terminalSavingTo(file);
        start();

        terminals.terminal(terminal.id().value()).write("a").writeUndefined();
        terminals.publish(terminal.id().value());
        feeds.poll();
        writer.runAll();

        assertThat(read(file)).isEqualTo("a?");
    }

    @Test
    void turningItOffStopsTheRecordingAndKeepsTheFile() throws IOException {
        Path file = folder.resolve("salida.txt");
        Component terminal = terminalSavingTo(file);
        start();
        arrive(terminal, "uno");
        writer.runAll();

        Map<String, Object> parameters = new HashMap<>(type.resolveParameters(terminal.parameters()));
        parameters.put(TerminalTypes.SAVE_OUTPUT, TerminalTypes.SAVE_OUTPUT_NO);
        document.execute(new ChangeParametersCommand(terminal.id(), type, parameters));
        assertThat(terminals.isRequested(terminal.id().value())).isFalse();
        assertThat(read(file)).isEqualTo("uno");
    }

    @Test
    void deletingThePartOrDisposingStopsTheRequests() {
        Component terminal = terminalSavingTo(folder.resolve("salida.txt"));
        Component other = terminalSavingTo(folder.resolve("otra.txt"));
        start();
        assertThat(terminals.isRequested(terminal.id().value())).isTrue();

        document.execute(new RemoveElementsCommand(List.of(terminal.id())));
        assertThat(terminals.isRequested(terminal.id().value())).isFalse();
        recorder.dispose();
        recorder = null;
        assertThat(terminals.isRequested(other.id().value())).isFalse();
    }

    @Test
    void aFileThatCannotBeWrittenIsToldOnce() {
        Component terminal = terminalSavingTo(folder.resolve("no-existe").resolve("salida.txt"));
        start();

        arrive(terminal, "a");
        writer.runAll();
        arrive(terminal, "b");
        writer.runAll();

        assertThat(problems).hasSize(1);
    }
}
