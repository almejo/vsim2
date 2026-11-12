package cl.almejo.vsim.ui.shortcuts;

import java.awt.event.KeyEvent;
import java.util.Optional;
import java.util.Set;

/**
 * The key code of a key event as shortcuts use it, and the keys that are «the one under Esc, left of 1». A key Java has
 * no {@code VK_} constant for (º on a Spanish layout on Linux) comes as {@link KeyEvent#VK_UNDEFINED} with an extended
 * key code, {@code 0x01000000 + the character}; a shortcut stores that extended code as its key code.
 */
public final class KeyCodes {

    /** The extended key codes start here: this plus the code point of the key's character. */
    public static final int EXTENDED_FLAG = 0x01000000;

    /**
     * What the key under Esc, bare, reports on the layouts we know: ` (US, UK, most), º (Spanish), ² (French) and §
     * (Swiss, Nordic). Left out on purpose: the dead ^ (it is the key right of P on French and Belgian layouts), ª and
     * ° (the Shift of that key on the Spanish and Latin American layouts, so a bare press never reports them).
     * Unverified on Windows and macOS.
     */
    private static final Set<Integer> UNDER_ESC = Set.of(
            KeyEvent.VK_BACK_QUOTE, EXTENDED_FLAG + 0xBA, EXTENDED_FLAG + 0xB2, EXTENDED_FLAG + 0xA7);

    private KeyCodes() {
    }

    /** The key code of {@code event}, or its extended key code when Java has no constant for the key. */
    public static int of(KeyEvent event) {
        int keyCode = event.getKeyCode();
        return keyCode == KeyEvent.VK_UNDEFINED ? event.getExtendedKeyCode() : keyCode;
    }

    /** Whether {@code keyCode} is the key under Esc as some layout reports it. */
    public static boolean isUnderEsc(int keyCode) {
        return UNDER_ESC.contains(keyCode);
    }

    /**
     * Whether a shortcut whose key is {@code bound} is the key the user pressed, {@code pressed}: the same code, or,
     * for the default key under Esc ({@link KeyEvent#VK_BACK_QUOTE}), whatever that key is on the user's layout.
     */
    public static boolean matches(int bound, int pressed) {
        return bound == pressed || (bound == KeyEvent.VK_BACK_QUOTE && isUnderEsc(pressed));
    }

    /**
     * Whether a shortcut whose key is {@code bound} is the key of {@code event}, by its key code or by its extended
     * one: a system may give the key a real code and say only in the extended one which character it is.
     */
    public static boolean matches(int bound, KeyEvent event) {
        return matches(bound, of(event)) || matches(bound, event.getExtendedKeyCode());
    }

    /** The character of an extended key code, if {@code keyCode} is one. */
    public static Optional<String> extendedCharacter(int keyCode) {
        int codePoint = keyCode & ~EXTENDED_FLAG;
        if ((keyCode & EXTENDED_FLAG) == 0 || codePoint == 0 || !Character.isValidCodePoint(codePoint)) {
            return Optional.empty();
        }
        return Optional.of(new String(Character.toChars(codePoint)));
    }
}
