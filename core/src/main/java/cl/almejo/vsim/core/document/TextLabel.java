package cl.almejo.vsim.core.document;

import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.Objects;

/**
 * A free text on the canvas (a note): its top-left corner, its text (one or more lines) and its {@link NoteStyle}
 * ({@link NoteStyle#PLAIN} unless the user gave it colours).
 */
public record TextLabel(ElementId id, GridPoint position, String text, NoteStyle style) implements DocumentElement {

    public TextLabel {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(position, "position");
        text = text == null ? "" : text;
        Objects.requireNonNull(style, "style");
    }

    /** A plain note. */
    public TextLabel(ElementId id, GridPoint position, String text) {
        this(id, position, text, NoteStyle.PLAIN);
    }

    public TextLabel withText(String newText) {
        return new TextLabel(id, position, newText, style);
    }

    public TextLabel withStyle(NoteStyle newStyle) {
        return new TextLabel(id, position, text, newStyle);
    }

    /** This note, with its text and style, under another id at another place (a paste). */
    public TextLabel placed(ElementId newId, GridPoint newPosition) {
        return new TextLabel(newId, newPosition, text, style);
    }

    @Override
    public TextLabel translate(int deltaX, int deltaY) {
        return placed(id, position.translate(deltaX, deltaY));
    }
}
