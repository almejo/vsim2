package cl.almejo.vsim.core.terminal;

/**
 * The text a Terminal received, as a person would read it on paper: printable characters and a {@code \n} per line
 * feed, with BS removing the last character of the line. Kept for «Detener al recibir» and «Salida esperada»; only
 * the last {@link #LIMIT} characters matter. Appending is amortised O(1): the buffer is trimmed only when it is twice
 * the limit.
 */
final class Transcript {

    /** How many characters {@link #text()} returns at most. */
    static final int LIMIT = 4000;

    private final StringBuilder text = new StringBuilder();

    void append(int codePoint) {
        text.appendCodePoint(codePoint);
        if (text.length() > 2 * LIMIT) {
            text.delete(0, cutBefore(text.length() - LIMIT));
        }
    }

    void newLine() {
        append('\n');
    }

    /** Removes the last character unless it is a line end: like Logisim's backspace, it never goes up a line. */
    void backspace() {
        int length = text.length();
        if (length == 0 || text.charAt(length - 1) == '\n') {
            return;
        }
        int last = text.offsetByCodePoints(length, -1);
        text.setLength(last);
    }

    /** The last {@link #LIMIT} characters. */
    String text() {
        return text.substring(cutBefore(Math.max(0, text.length() - LIMIT)));
    }

    /** Whether the text ends with {@code suffix}; costs the suffix's length, never a copy of the text. */
    boolean endsWith(String suffix) {
        int start = text.length() - suffix.length();
        if (start < 0) {
            return false;
        }
        for (int index = 0; index < suffix.length(); index++) {
            if (text.charAt(start + index) != suffix.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    Transcript copy() {
        Transcript copy = new Transcript();
        copy.text.append(text);
        return copy;
    }

    /** {@code index}, moved past a low surrogate so a character is never cut in half. */
    private int cutBefore(int index) {
        return index > 0 && Character.isLowSurrogate(text.charAt(index)) ? index + 1 : index;
    }
}
