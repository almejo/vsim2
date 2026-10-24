package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;

import java.util.Objects;
import java.util.Optional;

/**
 * The colours an AI edit gives a note: each one it names is set, each one it leaves empty stays as it is (on a new
 * note, {@code NONE}).
 */
public record NoteStyleChange(Optional<NoteColor> background, Optional<NoteColor> text,
                              Optional<NoteColor> border) {

    public NoteStyleChange {
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(border, "border");
    }

    /** {@code style} with the colours this change names. */
    public NoteStyle applyTo(NoteStyle style) {
        return new NoteStyle(background.orElse(style.background()), text.orElse(style.text()),
                border.orElse(style.border()));
    }
}
