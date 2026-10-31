package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentChange;
import cl.almejo.vsim.core.document.DocumentListener;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.memory.MemoryLinkPaths;
import cl.almejo.vsim.core.terminal.ByteLog;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * «Guardar salida: En un archivo .txt» (spec §8.9): the raw bytes every top-level Terminal of the circuit receives are
 * appended to its file, written by the services' writer (off the EDT, in order). An undefined byte is saved as the
 * {@code ?} the screen shows. A relative path is next to the circuit. EDT only, except the writing.
 *
 * <p>A file is touched only when the user wants it in this session: a recording is <em>armed</em> once its file was
 * chosen with «Elegir archivo…» ({@link #approve}) or, for a file a saved circuit names, once the user confirmed it
 * (asked once per file while the circuit is open; «no» leaves it unarmed). Opening a circuit never creates or
 * truncates anything. Arming starts the file over (truncated) with every byte the log still keeps, and so does
 * «Reiniciar» of an armed recording. Two Terminals, of this circuit or of another open one, never record to the same
 * file: the second one is told and stays unarmed. A file that cannot be written is told once per file through
 * {@code problems} (called on the writer's thread or on the EDT).
 */
public final class TerminalOutputRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalOutputRecorder.class);
    private static final byte UNDEFINED_BYTE = '?';

    /**
     * What the recorder takes from the outside: the {@code writer} of the files (off the EDT, in order), how a question
     * is left for later on the EDT ({@code later}: never from inside a document change), the question about a saved
     * circuit's file and the files every open circuit records to.
     */
    public record Services(Executor writer, Executor later, TerminalOutputConfirmation confirmation,
                           TerminalOutputClaims claims) {

        public Services {
            Objects.requireNonNull(writer, "writer");
            Objects.requireNonNull(later, "later");
            Objects.requireNonNull(confirmation, "confirmation");
            Objects.requireNonNull(claims, "claims");
        }
    }

    /** One Terminal being recorded: its file and its frames. */
    private record Recording(Path file, TerminalFeeds.Subscription subscription) {
    }

    /** Who holds a file in the claims: one Terminal of one recorder (the recorder compared by identity). */
    private record Holder(TerminalOutputRecorder recorder, String componentId) {
    }

    private final OpenDocument document;
    private final TerminalFeeds feeds;
    private final Services services;
    private final Consumer<String> problems;
    private final Map<String, Recording> recordings = new HashMap<>();
    /** The files the user chose or confirmed while this circuit is open (absolute, normalized). */
    private final Set<Path> approved = new HashSet<>();
    /** The files already asked about while this circuit is open, whatever the answer. */
    private final Set<Path> asked = new HashSet<>();
    /** The file each Terminal was told it cannot record to (another one records there), so it is told once. */
    private final Map<String, Path> toldBusy = new HashMap<>();
    /** Files already told as unwritable; touched on the writer's thread only. */
    private final Set<Path> told = ConcurrentHashMap.newKeySet();
    private final DocumentListener documentListener = this::documentChanged;
    private boolean disposed;

    public TerminalOutputRecorder(OpenDocument document, TerminalFeeds feeds, Services services,
                                  Consumer<String> problems) {
        this.document = Objects.requireNonNull(document, "document");
        this.feeds = Objects.requireNonNull(feeds, "feeds");
        this.services = Objects.requireNonNull(services, "services");
        this.problems = Objects.requireNonNull(problems, "problems");
        document.document().addListener(documentListener);
        refresh();
    }

    /**
     * The user chose {@code file} in this session («Elegir archivo…», which already confirmed replacing it): a Terminal
     * that saves its output there is armed, now or as soon as it names it.
     */
    public void approve(Path file) {
        approved.add(normalized(file));
        refresh();
    }

    /** Whether the output of the Terminal {@code componentId} is being written to its file. */
    public boolean isRecording(String componentId) {
        return recordings.containsKey(componentId);
    }

    /** Stops every recording (the document closes). */
    public void dispose() {
        disposed = true;
        document.document().removeListener(documentListener);
        List.copyOf(recordings.keySet()).forEach(this::stop);
    }

    private void documentChanged(CircuitDocument changed, DocumentChange change) {
        refresh();
    }

    /** Arms, moves or stops the recordings so they match the parameters of the Terminals on the circuit. */
    private void refresh() {
        if (disposed) {
            return;
        }
        Map<String, Path> wanted = wantedFiles();
        for (String componentId : List.copyOf(recordings.keySet())) {
            if (!recordings.get(componentId).file().equals(wanted.get(componentId))) {
                stop(componentId);
            }
        }
        toldBusy.keySet().retainAll(wanted.keySet());
        wanted.forEach(this::armOrAsk);
    }

    /** The file each top-level Terminal saving its output names, in document order. */
    private Map<String, Path> wantedFiles() {
        Map<String, Path> wanted = new LinkedHashMap<>();
        for (Component component : List.copyOf(document.document().components())) {
            if (component.typeId().equals(StandardComponentTypes.TERMINAL)) {
                outputFile(component).ifPresent(file -> wanted.put(component.id().value(), normalized(file)));
            }
        }
        return wanted;
    }

    private void armOrAsk(String componentId, Path file) {
        if (recordings.containsKey(componentId)) {
            return;
        }
        if (!approved.contains(file)) {
            askOnce(file);
            return;
        }
        if (!services.claims().claim(file, new Holder(this, componentId))) {
            tellBusy(componentId, file);
            return;
        }
        toldBusy.remove(componentId);
        String name = nameOf(componentId);
        recordings.put(componentId, new Recording(file, feeds.subscribe(componentId, 0,
                update -> record(name, file, update))));
    }

    private void stop(String componentId) {
        Recording recording = recordings.remove(componentId);
        recording.subscription().cancel();
        services.claims().release(recording.file(), new Holder(this, componentId));
    }

    /** A saved circuit's file: the user is asked once (later, out of the document change) whether to record there. */
    private void askOnce(Path file) {
        if (asked.add(file)) {
            services.later().execute(() -> ask(file));
        }
    }

    private void ask(Path file) {
        if (disposed || approved.contains(file)) {
            return;
        }
        Optional<String> terminal = wantedFiles().entrySet().stream()
                .filter(entry -> entry.getValue().equals(file))
                .map(Map.Entry::getKey)
                .findFirst();
        if (terminal.isPresent() && services.confirmation().recordThere(nameOf(terminal.get()), file.toString())) {
            approve(file);
        }
    }

    private void tellBusy(String componentId, Path file) {
        if (!file.equals(toldBusy.put(componentId, file))) {
            problems.accept(Messages.format("terminal.output.busy", nameOf(componentId), file.getFileName()));
        }
    }

    private static Path normalized(Path file) {
        return file.toAbsolutePath().normalize();
    }

    private Optional<Path> outputFile(Component terminal) {
        Map<String, Object> resolved = document.registry().typeOf(terminal).resolveParameters(terminal.parameters());
        String stored = String.valueOf(resolved.getOrDefault(TerminalTypes.OUTPUT_FILE, ""));
        if (!TerminalTypes.SAVE_OUTPUT_TO_FILE.equals(resolved.get(TerminalTypes.SAVE_OUTPUT)) || stored.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(MemoryLinkPaths.resolve(stored, document.path()));
        } catch (InvalidPathException exception) {
            LOGGER.warn("The output file {} is not a path", stored, exception);
            return Optional.empty();
        }
    }

    private String nameOf(String componentId) {
        return document.document().component(new ElementId(componentId))
                .map(component -> component.label().isBlank() ? Messages.componentName(component.typeId())
                        : component.label()).orElse(componentId);
    }

    private void record(String name, Path file, TerminalUpdate update) {
        if (update.newBytes().isEmpty() && !update.startedOver()) {
            return;
        }
        byte[] bytes = new byte[update.newBytes().size()];
        int index = 0;
        for (ByteLog.Entry entry : update.newBytes()) {
            bytes[index++] = entry.undefined() ? UNDEFINED_BYTE : (byte) entry.value();
        }
        boolean truncate = update.startedOver();
        services.writer().execute(() -> write(name, file, bytes, truncate));
    }

    private void write(String name, Path file, byte[] bytes, boolean truncate) {
        StandardOpenOption mode = truncate ? StandardOpenOption.TRUNCATE_EXISTING : StandardOpenOption.APPEND;
        try (OutputStream output = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                mode)) {
            output.write(bytes);
            told.remove(file);
        } catch (IOException exception) {
            LOGGER.warn("Could not save the output of {} to {}", name, file, exception);
            if (told.add(file)) {
                problems.accept(Messages.format("terminal.output.failed", name, file.getFileName()));
            }
        }
    }
}
