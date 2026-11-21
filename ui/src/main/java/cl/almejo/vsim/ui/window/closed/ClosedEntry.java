package cl.almejo.vsim.ui.window.closed;

import java.util.stream.Stream;

/** One close the user made, as «Reabrir pestaña cerrada» brings it back: a tab, a whole group or a whole window. */
public sealed interface ClosedEntry permits ClosedTab, ClosedGroup, ClosedWindow {

    /** The circuits this entry refers to. */
    Stream<DocumentReference> documents();
}
