package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameContents;
import cl.almejo.vsim.ui.app.OpenDocument;

/**
 * Whether a sticky note is held by a locked frame (spec §3, assumption 13): it then belongs to the block like a plain
 * note, moves with it and stays inside its walls, and every edit of the note itself (✕, ticks, text, resize, colour,
 * kind, fold) is refused until the frame is unlocked.
 */
public final class StickyNoteLock {

    private StickyNoteLock() {
    }

    /** True when a locked frame holds {@code noteId} (by the same containment rule as plain notes). */
    public static boolean isLocked(CircuitDocument document, ComponentTypeRegistry registry, ElementId noteId) {
        for (Frame frame : document.frames()) {
            if (frame.locked() && FrameContents.of(document, registry, frame).contains(noteId)) {
                return true;
            }
        }
        return false;
    }

    /** {@link #isLocked(CircuitDocument, ComponentTypeRegistry, ElementId)} in {@code openDocument}. */
    public static boolean isLocked(OpenDocument openDocument, ElementId noteId) {
        return isLocked(openDocument.document(), openDocument.registry(), noteId);
    }
}
