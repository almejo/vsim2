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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * «Guardar salida: En un archivo .txt» (spec §8.9): the raw bytes every Terminal of the circuit receives are appended
 * to its file, written by {@code writer} (off the EDT, in order). The file starts over (truncated) on «Reiniciar» and
 * when the recording starts (the document opens, the option is turned on, the file changes), with every byte the log
 * still keeps. An undefined byte is saved as the {@code ?} the screen shows. A file that cannot be written is told once
 * per file through {@code problems} (called on the writer's thread). A relative path is next to the circuit. EDT only,
 * except the writing.
 */
public final class TerminalOutputRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalOutputRecorder.class);
    private static final byte UNDEFINED_BYTE = '?';

    /** One Terminal being recorded: its file and its frames. */
    private record Recording(Path file, TerminalFeeds.Subscription subscription) {
    }

    private final OpenDocument document;
    private final TerminalFeeds feeds;
    private final Executor writer;
    private final Consumer<String> problems;
    private final Map<String, Recording> recordings = new HashMap<>();
    /** Files already told as unwritable; touched on the writer's thread only. */
    private final Set<Path> told = ConcurrentHashMap.newKeySet();
    private final DocumentListener documentListener = this::documentChanged;

    public TerminalOutputRecorder(OpenDocument document, TerminalFeeds feeds, Executor writer,
                                  Consumer<String> problems) {
        this.document = Objects.requireNonNull(document, "document");
        this.feeds = Objects.requireNonNull(feeds, "feeds");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.problems = Objects.requireNonNull(problems, "problems");
        document.document().addListener(documentListener);
        refresh();
    }

    /** Stops every recording (the document closes). */
    public void dispose() {
        document.document().removeListener(documentListener);
        recordings.values().forEach(recording -> recording.subscription().cancel());
        recordings.clear();
    }

    private void documentChanged(CircuitDocument changed, DocumentChange change) {
        refresh();
    }

    /** Starts, moves or stops the recordings so they match the parameters of the Terminals on the circuit. */
    private void refresh() {
        Map<String, Path> wanted = new HashMap<>();
        for (Component component : List.copyOf(document.document().components())) {
            if (component.typeId().equals(StandardComponentTypes.TERMINAL)) {
                outputFile(component).ifPresent(file -> wanted.put(component.id().value(), file));
            }
        }
        for (String componentId : List.copyOf(recordings.keySet())) {
            if (!recordings.get(componentId).file().equals(wanted.get(componentId))) {
                recordings.remove(componentId).subscription().cancel();
            }
        }
        wanted.forEach((componentId, file) -> {
            if (!recordings.containsKey(componentId)) {
                String name = nameOf(componentId);
                recordings.put(componentId, new Recording(file, feeds.subscribe(componentId, 0,
                        update -> record(name, file, update))));
            }
        });
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
        writer.execute(() -> write(name, file, bytes, truncate));
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
