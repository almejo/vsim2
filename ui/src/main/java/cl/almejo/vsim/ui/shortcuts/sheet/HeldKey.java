package cl.almejo.vsim.ui.shortcuts.sheet;

import java.awt.event.KeyEvent;

/**
 * Tells a held key's auto-repeat from a new press, so holding «?» toggles the sheet once instead of making it flicker.
 * The key that typed the character is held until its release. Systems repeat in two ways: Windows and macOS send more
 * presses without a release; X11 sends a release and a press with the same time stamp, which is a repeat too. A press
 * of another key forgets the held one (its release may have gone to another window).
 */
final class HeldKey {

    private int heldCode = KeyEvent.VK_UNDEFINED;
    private int lastPressedCode = KeyEvent.VK_UNDEFINED;
    private int releasedCode = KeyEvent.VK_UNDEFINED;
    private long releasedWhen = -1;

    /** Follows the presses and releases (other events are ignored). */
    void see(KeyEvent event) {
        int code = event.getKeyCode();
        if (event.getID() == KeyEvent.KEY_PRESSED) {
            lastPressedCode = code;
            if (code == releasedCode && event.getWhen() == releasedWhen) {
                heldCode = code; // X11's repeat: released and pressed again at the same instant
            } else if (code != heldCode) {
                heldCode = KeyEvent.VK_UNDEFINED;
            }
        } else if (event.getID() == KeyEvent.KEY_RELEASED && code == heldCode) {
            heldCode = KeyEvent.VK_UNDEFINED;
            releasedCode = code;
            releasedWhen = event.getWhen();
        }
    }

    /** Whether the character just typed comes from the key still held since it was last used: a repeat. */
    boolean isRepeat() {
        return heldCode != KeyEvent.VK_UNDEFINED;
    }

    /** The character just typed was used: its key (the last one pressed) is held from now on, until released. */
    void used() {
        heldCode = lastPressedCode;
    }
}
