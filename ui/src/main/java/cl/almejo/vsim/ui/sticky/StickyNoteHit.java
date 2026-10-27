package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.core.document.ElementId;

import java.util.Objects;

/**
 * A sticky note and the part of it under the pointer; {@code itemIndex} is the checklist item of a
 * {@link StickyPart#CHECKBOX} hit, {@link #NO_ITEM} for the other parts.
 */
public record StickyNoteHit(ElementId note, StickyPart part, int itemIndex) {

    public static final int NO_ITEM = -1;

    public StickyNoteHit {
        Objects.requireNonNull(note, "note");
        Objects.requireNonNull(part, "part");
    }
}
