package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.core.document.TextLabel;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Gives several notes a new style at once, as one undo step; revert gives each note its own style back. Only the
 * style changes: a note's text and place stay as they are when the command runs.
 */
public final class SetNoteStyleCommand implements Command {

    private final Map<ElementId, NoteStyle> after;
    private final Map<ElementId, NoteStyle> before = new LinkedHashMap<>();

    /** {@code styles}: the style each note gets; every id must be a note of the document it is applied to. */
    public SetNoteStyleCommand(Map<ElementId, NoteStyle> styles) {
        if (styles.isEmpty()) {
            throw new IllegalArgumentException("no notes to style");
        }
        this.after = new LinkedHashMap<>(styles);
    }

    /**
     * The command that paints {@code role} of every note among {@code ids} in {@code color}, keeping their other
     * colours; ids that are not notes are left out. Empty when no note would change.
     */
    public static Optional<Command> setting(CircuitDocument document, Collection<ElementId> ids,
                                            NoteStyle.Role role, NoteColor color) {
        Map<ElementId, NoteStyle> styles = new LinkedHashMap<>();
        for (ElementId id : ids) {
            document.label(id).filter(note -> note.style().color(role) != color)
                    .ifPresent(note -> styles.put(id, note.style().with(role, color)));
        }
        return styles.isEmpty() ? Optional.empty() : Optional.of(new SetNoteStyleCommand(styles));
    }

    @Override
    public void apply(CircuitDocument document) {
        before.clear();
        document.batch(() -> after.forEach((id, style) -> {
            TextLabel note = document.label(id).orElseThrow(() -> new IllegalStateException("no note " + id));
            before.put(id, note.style());
            document.replace(note.withStyle(style));
        }));
    }

    @Override
    public void revert(CircuitDocument document) {
        document.batch(() -> before.forEach((id, style) ->
                document.replace(document.label(id).orElseThrow().withStyle(style))));
    }

    @Override
    public String description() {
        return "command.noteStyle";
    }
}
