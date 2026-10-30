package cl.almejo.vsim.core.terminal;

/**
 * An edit of the not-yet-read typed text, as Logisim's Keyboard does with its arrows (spec §10): a cursor moves inside
 * the queue and Supr removes the character after it. Used when «Flechas y teclas F como secuencias VT» is off.
 */
public enum TypingEdit {
    CURSOR_LEFT,
    CURSOR_RIGHT,
    CURSOR_HOME,
    CURSOR_END,
    DELETE
}
