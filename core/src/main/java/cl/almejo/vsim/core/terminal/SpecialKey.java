package cl.almejo.vsim.core.terminal;

import java.util.Optional;

/**
 * A key of the PC keyboard that is not a printable character but still means something to a part that takes typing
 * (spec §10): what it sends is decided by {@link KeyEncoding#encode(SpecialKey)}.
 */
public enum SpecialKey {
    ENTER,
    BACKSPACE,
    TAB,
    /** Esc: sent with Shift+Esc while capturing, since Esc alone ends the capture. */
    ESCAPE,
    DELETE,
    UP,
    DOWN,
    LEFT,
    RIGHT,
    HOME,
    END,
    F1,
    F2,
    F3,
    F4,
    F5,
    F6,
    F7,
    F8,
    F9,
    F10,
    F11,
    F12;

    /**
     * How the key edits the not-yet-read queue when it sends no sequence (Logisim's Keyboard: ← → Inicio Fin Supr);
     * empty for the keys that never edit it.
     */
    public Optional<TypingEdit> typingEdit() {
        return switch (this) {
            case LEFT -> Optional.of(TypingEdit.CURSOR_LEFT);
            case RIGHT -> Optional.of(TypingEdit.CURSOR_RIGHT);
            case HOME -> Optional.of(TypingEdit.CURSOR_HOME);
            case END -> Optional.of(TypingEdit.CURSOR_END);
            case DELETE -> Optional.of(TypingEdit.DELETE);
            default -> Optional.empty();
        };
    }
}
