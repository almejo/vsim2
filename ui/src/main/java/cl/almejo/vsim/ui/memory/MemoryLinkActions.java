package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.window.EditorView;

/**
 * What the content editor asks of the linked-file machinery (the application's {@code MemoryLinks}): link the memory
 * to a file, unlink it (the contents stay in the circuit), open the linked file in the external editor. Each one is
 * its own user action and changes the document (or the desktop) by itself; the editor re-reads the component after.
 */
public interface MemoryLinkActions {

    /** Asks for a file and links the memory to it. */
    void link(EditorView view, ElementId componentId);

    /** Removes the link; the contents stay as they are in the circuit. */
    void unlink(EditorView view, ElementId componentId);

    /** Opens the linked file in the external editor. */
    void openExternal(EditorView view, ElementId componentId);
}
