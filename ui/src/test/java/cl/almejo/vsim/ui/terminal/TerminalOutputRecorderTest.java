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

/**
 * «Guardar salida» (spec §8.9): the raw bytes appended to a .txt off the EDT, the file truncated on «Reiniciar». A file
 * is touched only once the user chose it in this session or confirmed the one a saved circuit names.
 */
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
    private final List<String> questions = new ArrayList<>();
    private final TerminalOutputClaims claims = new TerminalOutputClaims();
    private boolean answer;
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
        recorder = recorderOf(document, feeds);
    }

    private TerminalOutputRecorder recorderOf(OpenDocument recorded, TerminalFeeds recordedFeeds) {
        TerminalOutputConfirmation confirmation = (name, file) -> {
            questions.add(name + " → " + file);
            return answer;
        };
        return new TerminalOutputRecorder(recorded, recordedFeeds,
                new TerminalOutputRecorder.Services(writer, Runnable::run, confirmation, claims), problems::add);
    }

    /** The user chose {@code file} with «Elegir archivo…» in this session. */
    private void startChosen(Path file) {
        start();
        recorder.approve(file);
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
        startChosen(file);

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
        startChosen(file);
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
        startChosen(file);

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
        startChosen(file);
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
        recorder.approve(folder.resolve("salida.txt"));
        recorder.approve(folder.resolve("otra.txt"));
        assertThat(terminals.isRequested(terminal.id().value())).isTrue();

        document.execute(new RemoveElementsCommand(List.of(terminal.id())));
        assertThat(terminals.isRequested(terminal.id().value())).isFalse();
        recorder.dispose();
        recorder = null;
        assertThat(terminals.isRequested(other.id().value())).isFalse();
    }

    @Test
    void aFileThatCannotBeWrittenIsToldOnce() {
        Path file = folder.resolve("no-existe").resolve("salida.txt");
        Component terminal = terminalSavingTo(file);
        startChosen(file);

        arrive(terminal, "a");
        writer.runAll();
        arrive(terminal, "b");
        writer.runAll();

        assertThat(problems).hasSize(1);
    }

    @Test
    void openingACircuitNeverTouchesTheFileItNames() throws IOException {
        Path existing = folder.resolve("tesis.txt");
        Files.writeString(existing, "years of work", StandardCharsets.UTF_8);
        Path missing = folder.resolve("nueva.txt");
        Component first = terminalSavingTo(existing);
        Component second = terminalSavingTo(missing);
        start();

        arrive(first, "hola");
        arrive(second, "hola");
        writer.runAll();

        assertThat(read(existing)).isEqualTo("years of work");
        assertThat(missing).doesNotExist();
        assertThat(recorder.isRecording(first.id().value())).isFalse();
    }

    @Test
    void theFileOfASavedCircuitIsAskedAboutOnceAndNoKeepsItUnarmed() throws IOException {
        Path file = folder.resolve("tesis.txt");
        Files.writeString(file, "years of work", StandardCharsets.UTF_8);
        Component terminal = terminalSavingTo(file);
        start();
        assertThat(questions).containsExactly("consola → " + file);

        Map<String, Object> parameters = new HashMap<>(type.resolveParameters(terminal.parameters()));
        parameters.put(TerminalTypes.ROWS, 5);
        document.execute(new ChangeParametersCommand(terminal.id(), type, parameters));
        arrive(terminal, "hola");
        writer.runAll();

        assertThat(questions).as("asked once per file while the circuit is open").hasSize(1);
        assertThat(read(file)).isEqualTo("years of work");
    }

    @Test
    void confirmingTheFileOfASavedCircuitArmsItAndStartsTheFile() throws IOException {
        Path file = folder.resolve("salida.txt");
        Files.writeString(file, "de antes", StandardCharsets.UTF_8);
        Component terminal = terminalSavingTo(file);
        answer = true;
        start();

        arrive(terminal, "hola");
        writer.runAll();

        assertThat(read(file)).isEqualTo("hola");
    }

    @Test
    void aFileChosenInThisSessionIsRecordedWithoutAsking() throws IOException {
        Path file = folder.resolve("salida.txt");
        start();
        recorder.approve(file);
        Component terminal = terminalSavingTo(file);

        arrive(terminal, "hola");
        writer.runAll();

        assertThat(questions).isEmpty();
        assertThat(read(file)).isEqualTo("hola");
    }

    @Test
    void twoTerminalsNeverRecordToTheSameFile() throws IOException {
        Path file = folder.resolve("salida.txt");
        Component first = terminalSavingTo(file);
        Component copy = terminalSavingTo(file);
        startChosen(file);

        arrive(first, "uno");
        arrive(copy, "dos");
        writer.runAll();

        assertThat(read(file)).isEqualTo("uno");
        assertThat(recorder.isRecording(copy.id().value())).isFalse();
        assertThat(problems).hasSize(1);
    }

    @Test
    void twoCircuitsNeverRecordToTheSameFile() throws IOException {
        Path file = folder.resolve("salida.txt");
        Component first = terminalSavingTo(file);
        startChosen(file);
        OpenDocument otherDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        FakeTerminals otherTerminals = new FakeTerminals();
        TerminalFeeds otherFeeds = new TerminalFeeds(otherTerminals);
        Component other = type.newComponent(otherDocument.document().newComponentId(), new GridPoint(0, 0),
                Rotation.R0, Map.of(TerminalTypes.SAVE_OUTPUT, TerminalTypes.SAVE_OUTPUT_TO_FILE,
                        TerminalTypes.OUTPUT_FILE, file.toString()), "otra");
        otherDocument.execute(new AddElementsCommand(List.of(other)));
        otherTerminals.add(other.id().value(), 2, 10);
        TerminalOutputRecorder otherRecorder = recorderOf(otherDocument, otherFeeds);
        try {
            otherRecorder.approve(file);

            arrive(first, "uno");
            writer.runAll();

            assertThat(otherRecorder.isRecording(other.id().value())).isFalse();
            assertThat(read(file)).isEqualTo("uno");
        } finally {
            otherRecorder.dispose();
            otherDocument.close();
        }
    }
}
