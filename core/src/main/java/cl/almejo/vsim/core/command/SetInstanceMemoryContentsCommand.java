package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.PieceMemories;
import cl.almejo.vsim.core.memory.MemoryContents;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One piece's own contents of a memory inside it (spec §6.6b), set or cleared, as one undo step: the content editor
 * of a component tab writes the piece it shows, never the component, so the other pieces keep theirs. A structural
 * edit like any other: the simulation restarts and every piece starts from its contents. Cleared, the piece uses its
 * component's contents again («Volver al del componente»).
 */
public final class SetInstanceMemoryContentsCommand implements Command {

    /** Same undo text as the circuit's own memories: «Deshacer editar contenido». */
    public static final String SET_DESCRIPTION = "command.editMemory";
    public static final String CLEAR_DESCRIPTION = "command.pieceMemoryBack";

    private final PieceMemories.Place place;
    /** The canonical contents to keep; empty clears the piece's entry. */
    private final Optional<String> contents;
    /** The piece as it was before the first apply; null until then. */
    private Component before;

    private SetInstanceMemoryContentsCommand(PieceMemories.Place place, Optional<String> contents) {
        this.place = Objects.requireNonNull(place, "place");
        this.contents = contents;
    }

    /** The piece of {@code place} keeps {@code contents} for that memory, whatever its component holds. */
    public static SetInstanceMemoryContentsCommand set(PieceMemories.Place place, MemoryContents contents) {
        return new SetInstanceMemoryContentsCommand(place, Optional.of(contents.encode()));
    }

    /** The piece of {@code place} forgets its contents of that memory and uses its component's again. */
    public static SetInstanceMemoryContentsCommand clear(PieceMemories.Place place) {
        return new SetInstanceMemoryContentsCommand(place, Optional.empty());
    }

    @Override
    public void apply(CircuitDocument document) {
        Component piece = document.component(place.piece()).orElseThrow(() ->
                new IllegalStateException("the circuit has no piece " + place.piece()));
        before = piece;
        Map<String, String> kept = new LinkedHashMap<>(piece.memoryContents());
        contents.ifPresentOrElse(text -> kept.put(place.innerPath(), text), () -> kept.remove(place.innerPath()));
        document.replace(piece.withMemoryContents(kept));
    }

    @Override
    public void revert(CircuitDocument document) {
        document.replace(before);
    }

    @Override
    public String description() {
        return contents.isPresent() ? SET_DESCRIPTION : CLEAR_DESCRIPTION;
    }
}
