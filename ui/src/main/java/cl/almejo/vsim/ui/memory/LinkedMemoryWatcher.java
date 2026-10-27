package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.memory.MemoryLinkPaths;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.memory.MemoryFile;
import cl.almejo.vsim.ui.app.Listeners;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Follows the linked files of one open document (memory spec §8.2): {@link #poll()} (every second, when a window gets
 * the focus, or by hand in tests) looks at each linked memory's file, and when it changed (and stayed the same for a
 * second poll) reads it and applies its
 * words as one undo step «Recargar contenido» — the same as any edit while simulating: that memory starts again
 * with the new program. A file that is missing or cannot be read is a problem of the part ({@link #problem}); the
 * circuit keeps simulating with the copy it saved. Nothing is ever written to the file. EDT only.
 */
public final class LinkedMemoryWatcher {

    /** What a file looked like the last time it was read: a change of any of these reads it again. */
    private record Fingerprint(Path file, FileTime modified, long size) {
    }

    private final OpenDocument document;
    private final Map<ElementId, Fingerprint> seen = new HashMap<>();
    /** A file that changed since it was read, waiting for a second poll that finds it the same (a save in two writes). */
    private final Map<ElementId, Fingerprint> settling = new HashMap<>();
    private final Map<ElementId, String> problems = new HashMap<>();
    private final Listeners<Runnable> listeners = new Listeners<>();

    public LinkedMemoryWatcher(OpenDocument document) {
        this.document = Objects.requireNonNull(document, "document");
    }

    /** Looks at every linked file once: reloads the changed ones, notes the missing or unreadable ones. */
    public void poll() {
        Map<ElementId, String> before = Map.copyOf(problems);
        Set<ElementId> linked = new HashSet<>();
        for (Component component : document.document().components()) {
            if (MemoryTypes.kindOf(component.typeId()).isEmpty()) {
                continue;
            }
            Optional<Path> file = MemoryLinkPaths.linkedFile(component, document.path());
            if (file.isPresent()) {
                linked.add(component.id());
                check(component, file.get());
            }
        }
        seen.keySet().retainAll(linked);
        settling.keySet().retainAll(linked);
        problems.keySet().retainAll(linked);
        if (!problems.equals(before)) {
            listeners.fire(Runnable::run);
        }
    }

    /** Why the linked file of {@code componentId} is not used now («No se encuentra …»), empty when all is well. */
    public Optional<String> problem(ElementId componentId) {
        return Optional.ofNullable(problems.get(componentId));
    }

    /** Every linked memory whose file cannot be used now, with why. */
    public Map<ElementId, String> problems() {
        return Map.copyOf(problems);
    }

    /** Runs {@code listener} whenever the problems change; returns its removal. */
    public Runnable addProblemsListener(Runnable listener) {
        return listeners.add(listener);
    }

    private void check(Component component, Path file) {
        ElementId id = component.id();
        String shown = MemoryLinkPaths.displayPath(MemoryLinkPaths.linkOf(component), document.path());
        Optional<Fingerprint> now = fingerprint(file);
        if (now.isEmpty()) {
            seen.remove(id);
            settling.remove(id);
            problems.put(id, Messages.format("memory.link.missing", shown));
            return;
        }
        if (now.get().equals(seen.get(id))) {
            settling.remove(id);
            return;
        }
        // A file read before is read again only once two polls in a row find it the same: an editor that saves in
        // two writes (truncate, then write) must not be read half done.
        if (seen.containsKey(id) && !now.get().equals(settling.get(id))) {
            settling.put(id, now.get());
            return;
        }
        settling.remove(id);
        seen.put(id, now.get());
        try {
            MemoryFile read = MemoryFileIo.read(file);
            problems.remove(id);
            reloadIfChanged(component, read.contents());
        } catch (MemoryFileIo.Unreadable problem) {
            problems.put(id, Messages.format("memory.link.unreadable", problem.getMessage()));
        }
    }

    private void reloadIfChanged(Component component, MemoryContents read) {
        String stored = String.valueOf(component.parameters().getOrDefault(MemoryTypes.CONTENTS, ""));
        if (!MemoryContents.decode(stored).equals(read)) {
            document.execute(MemoryLinks.changing(document, component, Map.of(MemoryTypes.CONTENTS, read.encode()),
                    "command.reloadMemory"));
        }
    }

    private static Optional<Fingerprint> fingerprint(Path file) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            if (!attributes.isRegularFile()) {
                return Optional.empty();
            }
            return Optional.of(new Fingerprint(file, attributes.lastModifiedTime(), attributes.size()));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }
}
