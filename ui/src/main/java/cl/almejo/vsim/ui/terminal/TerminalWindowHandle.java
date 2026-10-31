package cl.almejo.vsim.ui.terminal;

import java.awt.Rectangle;

/** A Terminal window on screen (a {@code JDialog} in the editor, a fake in tests). */
public interface TerminalWindowHandle {

    void setTitle(String title);

    void toFront();

    /** Where it is and how big, to remember for the next time. */
    Rectangle bounds();

    /** Closes it (the part was deleted, the editor window closed). */
    void close();
}
