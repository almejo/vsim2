package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.core.document.TextLabel;

import java.util.Objects;

/**
 * A free text on a machine's diagram, as a circuit's {@link TextLabel}: its top-left corner at {@code position} (diagram
 * units, pixels at 100 %), its text (one or more lines) and its {@link NoteStyle} ({@link NoteStyle#PLAIN} unless the
 * user gave it colours). Only a reminder for the reader: it takes no part in the simulation and never shows on the
 * piece's face.
 */
public record MachineText(String id, DiagramPoint position, String text, NoteStyle style) {

    public MachineText {
        StateMachine.requireId(id, "text");
        Objects.requireNonNull(position, "position");
        text = text == null ? "" : text;
        Objects.requireNonNull(style, "style");
    }

    /** A plain text. */
    public MachineText(String id, DiagramPoint position, String text) {
        this(id, position, text, NoteStyle.PLAIN);
    }

    public MachineText withText(String newText) {
        return new MachineText(id, position, newText, style);
    }

    public MachineText withStyle(NoteStyle newStyle) {
        return new MachineText(id, position, text, newStyle);
    }

    public MachineText withPosition(DiagramPoint newPosition) {
        return new MachineText(id, newPosition, text, style);
    }

    /** This text, with its words and style, under another id at another place (a paste). */
    public MachineText placed(String newId, DiagramPoint newPosition) {
        return new MachineText(newId, newPosition, text, style);
    }
}
