package cl.almejo.vsim.core.document;

/**
 * The named colours of a note's background, text and border (Pulido T5). Only the names live in core; the colours
 * belong to the UI colour schemes, which work out a readable shade of each on the board it is drawn on. {@code NONE}
 * is today's look: no background, the scheme's text colour, no border.
 */
public enum NoteColor {
    NONE, BLUE, GREEN, YELLOW, RED, PURPLE, GRAY, BLACK, WHITE
}
