package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.simulation.InstancePath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Each piece's own memory contents (spec §6.6b). A user-component piece may store initial contents for any RAM or ROM
 * in its body, under the memory's path inside it ({@link Component#memoryContents()}: {@code "c10"}, or
 * {@code "c3/c10"} for a memory in a piece inside it). The memory simulated as {@code c1/c3/c10} starts with what the
 * outermost piece on its path stores for it ({@code c1}, then {@code c3} inside the CPU's body), else with its own
 * contents, the definition's. The editor stores a piece's contents on the circuit's piece, so they are that piece's
 * alone.
 */
public final class PieceMemories {

    /** Where a piece's contents of one memory are kept: on {@code piece} (of the circuit), under {@code innerPath}. */
    public record Place(ElementId piece, String innerPath) {
        public Place {
            Objects.requireNonNull(piece, "piece");
            if (innerPath == null || innerPath.isBlank()) {
                throw new IllegalArgumentException("a memory inside a piece needs its path");
            }
        }
    }

    /**
     * Contents a circuit's piece keeps that do not do what they seem (problems are never hidden): kept for a memory
     * that is no longer in its component ({@link Kind#FOR_NOTHING}: the entry is ignored), or for a memory linked to a
     * file, which this piece then does not use ({@link Kind#OVER_LINK}).
     */
    public record StoredContentsIssue(Place place, Kind kind) {

        public enum Kind {
            /** The inner path names no memory of the component (any more): the entry is ignored. */
            FOR_NOTHING,
            /**
             * The memory is linked to a file (the component's, in every piece): this piece starts from its own
             * contents, not from the file (the user, 2026-11-06).
             */
            OVER_LINK
        }

        public StoredContentsIssue {
            Objects.requireNonNull(place, "place");
            Objects.requireNonNull(kind, "kind");
        }
    }

    private PieceMemories() {
    }

    /**
     * The contents kept by the circuit's pieces that do not do what they seem, piece by piece in document order and
     * by inner path: only the pieces that keep some are looked at.
     */
    public static List<StoredContentsIssue> storedContentsIssues(CircuitDocument document) {
        List<StoredContentsIssue> issues = new ArrayList<>();
        for (Component piece : document.components()) {
            if (piece.memoryContents().isEmpty()) {
                continue;
            }
            piece.memoryContents().keySet().stream().sorted().forEach(innerPath ->
                    issueOf(document, piece, innerPath).ifPresent(kind ->
                            issues.add(new StoredContentsIssue(new Place(piece.id(), innerPath), kind))));
        }
        return List.copyOf(issues);
    }

    /** What is wrong with the contents {@code piece} keeps under {@code innerPath}; empty when they apply. */
    private static Optional<StoredContentsIssue.Kind> issueOf(CircuitDocument document, Component piece,
                                                              String innerPath) {
        Optional<Component> memory = memoryAt(document, piece, innerPath);
        if (memory.isEmpty()) {
            return Optional.of(StoredContentsIssue.Kind.FOR_NOTHING);
        }
        boolean linked = !String.valueOf(memory.get().parameters().getOrDefault(MemoryTypes.LINK, "")).isBlank();
        return linked ? Optional.of(StoredContentsIssue.Kind.OVER_LINK) : Optional.empty();
    }

    /**
     * The memory whose contents {@code place} keeps (inside the circuit's piece, at the inner path); empty when the
     * piece or the memory is not there.
     */
    public static Optional<Component> memory(CircuitDocument document, Place place) {
        return document.component(place.piece()).flatMap(piece -> memoryAt(document, piece, place.innerPath()));
    }

    /** The memory {@code innerPath} names inside {@code piece} ({@code c3/c10}); empty when it names none. */
    private static Optional<Component> memoryAt(CircuitDocument document, Component piece, String innerPath) {
        List<String> segments = InstancePath.segments(innerPath);
        List<Component> level = bodyOf(piece, document);
        for (int depth = 0; depth < segments.size() - 1; depth++) {
            Optional<Component> inner = find(level, segments.get(depth));
            if (inner.isEmpty()) {
                return Optional.empty();
            }
            level = bodyOf(inner.get(), document);
        }
        return find(level, segments.getLast()).filter(part -> MemoryTypes.kindOf(part.typeId()).isPresent());
    }

    /**
     * Where the contents of memory {@code memoryId} of the body shown for the piece at {@code contextPath} are kept for
     * that piece alone: on the circuit's piece the path starts with ({@code c1} for {@code c1/c3}), under the rest of
     * the path ({@code c3/c10}).
     */
    public static Place placeOf(String contextPath, ElementId memoryId) {
        List<String> segments = InstancePath.segments(contextPath);
        List<String> inner = new ArrayList<>(segments.subList(1, segments.size()));
        inner.add(memoryId.value());
        return new Place(new ElementId(segments.getFirst()), String.join(InstancePath.SEPARATOR, inner));
    }

    /**
     * The contents the memory simulated as {@code flatMemoryId} ({@code c1/c3/c10}) starts with when a piece on its
     * path stores some: the outermost one's. Empty for a memory of the circuit, a path that names nothing, or when no
     * piece stores any (the memory uses its own, the definition's).
     */
    public static Optional<MemoryContents> effective(CircuitDocument document, String flatMemoryId) {
        return effectiveText(document, flatMemoryId).map(MemoryContents::decode);
    }

    /**
     * {@link #effective} as the text the piece keeps ({@link MemoryContents#encode()}), so the simulation's flattening
     * hands it on without decoding a large memory on every edit.
     */
    public static Optional<String> effectiveText(CircuitDocument document, String flatMemoryId) {
        List<String> segments = InstancePath.segments(flatMemoryId);
        List<Component> level = document.components();
        for (int depth = 0; depth < segments.size() - 1; depth++) {
            Optional<Component> piece = find(level, segments.get(depth));
            if (piece.isEmpty()) {
                return Optional.empty();
            }
            String rest = String.join(InstancePath.SEPARATOR, segments.subList(depth + 1, segments.size()));
            String stored = piece.get().memoryContents().get(rest);
            if (stored != null) {
                return Optional.of(stored);
            }
            level = bodyOf(piece.get(), document);
        }
        return Optional.empty();
    }

    /** What the circuit's piece of {@code place} stores itself for that memory; empty when it stores nothing. */
    public static Optional<MemoryContents> stored(CircuitDocument document, Place place) {
        return document.component(place.piece())
                .map(piece -> piece.memoryContents().get(place.innerPath()))
                .map(MemoryContents::decode);
    }

    /**
     * {@code body} (the parts of {@code piece}'s definition, about to replace the piece: «Desarmar») with the piece's
     * own contents given to them: a memory takes its entry as its contents, a piece inside takes the entries under it
     * (over its own), so nothing the piece kept is lost.
     */
    public static CircuitFragment withContentsOf(Component piece, CircuitFragment body) {
        if (piece.memoryContents().isEmpty()) {
            return body;
        }
        Map<String, Map<String, String>> byPart = new HashMap<>();
        piece.memoryContents().forEach((path, contents) -> {
            List<String> segments = InstancePath.segments(path);
            String rest = String.join(InstancePath.SEPARATOR, segments.subList(1, segments.size()));
            byPart.computeIfAbsent(segments.getFirst(), part -> new LinkedHashMap<>()).put(rest, contents);
        });
        List<Component> components = body.components().stream()
                .map(part -> given(part, byPart.getOrDefault(part.id().value(), Map.of())))
                .toList();
        return new CircuitFragment(components, body.wires(), body.labels(), body.definitions(), body.frames(),
                body.stickyNotes());
    }

    /** {@code part} with {@code entries} (rest of path → contents; "" is the part itself, a memory). */
    private static Component given(Component part, Map<String, String> entries) {
        if (entries.isEmpty()) {
            return part;
        }
        Component given = part;
        String own = entries.get("");
        if (own != null && MemoryTypes.kindOf(part.typeId()).isPresent()) {
            Map<String, Object> parameters = new HashMap<>(part.parameters());
            parameters.put(MemoryTypes.CONTENTS, own);
            given = given.withParameters(parameters);
        }
        Map<String, String> inner = new LinkedHashMap<>(part.memoryContents());
        entries.forEach((path, contents) -> {
            if (!path.isEmpty()) {
                inner.put(path, contents);
            }
        });
        return given.withMemoryContents(inner);
    }

    private static Optional<Component> find(List<Component> level, String id) {
        return level.stream().filter(component -> component.id().value().equals(id)).findFirst();
    }

    private static List<Component> bodyOf(Component piece, CircuitDocument document) {
        return SubcircuitTypes.definitionIdOf(piece.typeId()).flatMap(document::definition)
                .map(definition -> definition.body().components())
                .orElse(List.of());
    }
}
