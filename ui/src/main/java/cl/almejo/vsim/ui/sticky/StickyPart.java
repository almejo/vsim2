package cl.almejo.vsim.ui.sticky;

/** The part of a sticky note under the pointer: its controls, its resize corner or the paper itself. */
public enum StickyPart {
    /** The ✕ at the top-right corner: closes (unsticks) the note after a confirmation. */
    CLOSE,
    /** The checkbox of one checklist item: ticks or unticks it. */
    CHECKBOX,
    /** The folded bottom-right corner of an unfolded note: dragging it resizes the note. */
    RESIZE,
    /** Anywhere else on the paper: selects and moves the note; a double click edits or unfolds it. */
    BODY
}
