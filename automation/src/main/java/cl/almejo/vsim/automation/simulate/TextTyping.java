package cl.almejo.vsim.automation.simulate;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.core.terminal.KeyEncoding;
import cl.almejo.vsim.core.terminal.SpecialKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Turns a text into what a person typing it on a Teclado would send: one byte array per character. */
public final class TextTyping {

    /** The most characters one call types (the largest queue a Teclado holds). */
    public static final int MAXIMUM_LENGTH = 4096;

    private static final int LINE_FEED = '\n';

    private TextTyping() {
    }

    /**
     * The bytes of every character of {@code text}: a line feed is the Enter key as {@code encoding} sends it, any
     * other character is encoded as typed. A character the part cannot send refuses the whole text.
     */
    public static List<byte[]> characters(String text, KeyEncoding encoding) {
        if (text.isEmpty() || text.codePointCount(0, text.length()) > MAXIMUM_LENGTH) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "text has between 1 and "
                    + MAXIMUM_LENGTH + " characters", Map.of("maximum", MAXIMUM_LENGTH));
        }
        List<byte[]> characters = new ArrayList<>();
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            byte[] bytes = codePoint == LINE_FEED
                    ? encoding.encode(SpecialKey.ENTER).orElseGet(() -> encoding.encode(codePoint))
                    : encoding.encode(codePoint);
            if (bytes.length == 0) {
                throw new AutomationException(AutomationError.INVALID_PARAMETER, "This keyboard cannot send U+"
                        + String.format("%04X", codePoint) + " (7-bit data or Latin-1 encoding); nothing was typed");
            }
            characters.add(bytes);
        }
        return characters;
    }
}
