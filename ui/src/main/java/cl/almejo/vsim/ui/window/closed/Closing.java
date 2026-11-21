package cl.almejo.vsim.ui.window.closed;

/** How a closed tab's circuit is now: still open somewhere, or how it closed. */
public enum Closing {
    /** Some window still holds the circuit: its tabs come back on the open document. */
    OPEN,
    /** It closed saved: the file comes back as it is. */
    SAVED,
    /** It had a file and closed with changes («No guardar»): the file comes back as it was saved. */
    LOST_CHANGES,
    /** It never had a file: it cannot come back. */
    NEVER_SAVED
}
