package cl.almejo.vsim.core.terminal;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntConsumer;

/**
 * What a key typed on the PC sends to a part that takes typing (a Teclado, spec §10): a character as one or more
 * bytes in the part's data width and encoding, and the special keys as the part's options say (Enter as LF, CR or
 * CR LF; Retroceso as BS or DEL; arrows, Inicio, Fin, Supr and F1–F12 as VT/xterm sequences, or nothing when the
 * part edits its queue with them instead, see {@link SpecialKey#typingEdit()}).
 *
 * @param dataWidth          7 or 8 bits; with 7 only ASCII is representable
 * @param encoding           how an 8-bit part turns characters above 127 into bytes
 * @param enterSends         what Enter sends
 * @param backspaceSends     what Retroceso sends
 * @param arrowsAsSequences  whether the arrows, Inicio, Fin, Supr and the F keys send VT sequences
 */
public record KeyEncoding(int dataWidth, TerminalEncoding encoding, EnterSends enterSends,
                          BackspaceSends backspaceSends, boolean arrowsAsSequences) {

    /** The parameter names a part that takes typing uses for its encoding (the Teclado's, spec §10). */
    public static final String DATA_WIDTH = "data-width";
    public static final String ENCODING = "encoding";
    public static final String ENTER_SENDS = "enter-sends";
    public static final String BACKSPACE_SENDS = "backspace-sends";
    public static final String ARROWS_AS_SEQUENCES = "arrows-as-sequences";

    /** The values of {@link #ENCODING}, {@link #ENTER_SENDS} and {@link #BACKSPACE_SENDS}. */
    public static final String UTF8_VALUE = "utf8";
    public static final String LATIN1_VALUE = "latin1";
    public static final String LF_VALUE = "lf";
    public static final String CR_VALUE = "cr";
    public static final String CR_LF_VALUE = "crlf";
    public static final String BS_VALUE = "bs";
    public static final String DEL_VALUE = "del";

    public static final int SEVEN_BITS = 7;
    public static final int EIGHT_BITS = 8;

    /** The Teclado's defaults (spec §10): 8 bits, UTF‑8, Enter = LF, Retroceso = BS, arrows edit the queue. */
    public static final KeyEncoding DEFAULT = new KeyEncoding(EIGHT_BITS, TerminalEncoding.UTF8, EnterSends.LF,
            BackspaceSends.BS, false);

    private static final int LARGEST_SEVEN_BIT_CODE = 0x7F;
    private static final int LARGEST_LATIN1_CODE = 0xFF;
    private static final int CONTROL_MASK = 0x1F;
    /** The space: codes below it are control characters. */
    private static final int FIRST_PRINTABLE_CODE = 0x20;
    private static final byte LINE_FEED = 0x0A;
    private static final byte CARRIAGE_RETURN = 0x0D;
    private static final byte BACKSPACE = 0x08;
    private static final byte DELETE = 0x7F;
    private static final byte TAB = 0x09;
    private static final byte ESCAPE = 0x1B;
    private static final String CONTROL_SEQUENCE = "\u001B[";
    private static final String SINGLE_SHIFT_THREE = "\u001BO";

    /** VT100 / xterm sequences of the keys that send one when {@link #arrowsAsSequences} is on. */
    private static final Map<SpecialKey, String> SEQUENCES = sequences();

    /** What Enter sends: Logisim's LF, or CR, or CR LF. */
    public enum EnterSends { LF, CR, CR_LF }

    /** What Retroceso sends: BS (8) or DEL (127). */
    public enum BackspaceSends { BS, DEL }

    public KeyEncoding {
        if (dataWidth != SEVEN_BITS && dataWidth != EIGHT_BITS) {
            throw new IllegalArgumentException("data width must be 7 or 8: " + dataWidth);
        }
        Objects.requireNonNull(encoding, "encoding");
        Objects.requireNonNull(enterSends, "enterSends");
        Objects.requireNonNull(backspaceSends, "backspaceSends");
    }

    /**
     * The encoding a part's (resolved) parameters ask for, under the names of {@link #DATA_WIDTH} and the others; a
     * missing or unknown value takes the {@link #DEFAULT} one.
     */
    public static KeyEncoding forParameters(Map<String, Object> parameters) {
        Objects.requireNonNull(parameters, "parameters");
        int dataWidth = dataWidthOf(parameters.get(DATA_WIDTH));
        TerminalEncoding encoding = LATIN1_VALUE.equals(parameters.get(ENCODING)) ? TerminalEncoding.LATIN1
                : TerminalEncoding.UTF8;
        Object enter = parameters.get(ENTER_SENDS);
        EnterSends enterSends = CR_VALUE.equals(enter) ? EnterSends.CR
                : CR_LF_VALUE.equals(enter) ? EnterSends.CR_LF : EnterSends.LF;
        BackspaceSends backspaceSends = DEL_VALUE.equals(parameters.get(BACKSPACE_SENDS)) ? BackspaceSends.DEL
                : BackspaceSends.BS;
        boolean arrowsAsSequences = Boolean.TRUE.equals(parameters.get(ARROWS_AS_SEQUENCES));
        return new KeyEncoding(dataWidth, encoding, enterSends, backspaceSends, arrowsAsSequences);
    }

    /** An INTEGER parameter (7, 8) or a CHOICE one ("7", "8"); anything else is the default 8. */
    private static int dataWidthOf(Object value) {
        String text = value == null ? "" : value.toString();
        return String.valueOf(SEVEN_BITS).equals(text) ? SEVEN_BITS : EIGHT_BITS;
    }

    /**
     * The bytes of character {@code codePoint}; empty when it is not representable (above 127 with 7 bits, above 255
     * in Latin‑1, a surrogate or not a code point at all). A new array on every call.
     */
    public byte[] encode(int codePoint) {
        if (!Character.isValidCodePoint(codePoint) || Character.getType(codePoint) == Character.SURROGATE) {
            return new byte[0];
        }
        if (codePoint <= LARGEST_SEVEN_BIT_CODE) {
            return new byte[]{(byte) codePoint};
        }
        if (dataWidth == SEVEN_BITS) {
            return new byte[0];
        }
        return switch (encoding) {
            case LATIN1 -> codePoint <= LARGEST_LATIN1_CODE ? new byte[]{(byte) codePoint} : new byte[0];
            case UTF8 -> new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
        };
    }

    /**
     * What special key {@code key} sends; empty when it sends nothing with these options (the arrows, Inicio, Fin and
     * Supr then edit the queue, {@link SpecialKey#typingEdit()}). A new array on every call.
     */
    public Optional<byte[]> encode(SpecialKey key) {
        Objects.requireNonNull(key, "key");
        return switch (key) {
            case ENTER -> Optional.of(switch (enterSends) {
                case LF -> new byte[]{LINE_FEED};
                case CR -> new byte[]{CARRIAGE_RETURN};
                case CR_LF -> new byte[]{CARRIAGE_RETURN, LINE_FEED};
            });
            case BACKSPACE -> Optional.of(new byte[]{backspaceSends == BackspaceSends.BS ? BACKSPACE : DELETE});
            case TAB -> Optional.of(new byte[]{TAB});
            case ESCAPE -> Optional.of(new byte[]{ESCAPE});
            default -> arrowsAsSequences
                    ? Optional.of(SEQUENCES.get(key).getBytes(StandardCharsets.US_ASCII)) : Optional.empty();
        };
    }

    /**
     * A whole text (a paste, a Teclado's «Texto inicial») as the keys that would type it, one array per character:
     * a line break (LF, CR or CR LF) is Enter, a tab is Tab, and a character this encoding cannot represent or another
     * control character is left out. Compare the size with {@link #characterCount} to know whether all of it fits.
     */
    public List<byte[]> encodeText(String text) {
        List<byte[]> characters = new ArrayList<>();
        forEachCharacter(text, codePoint -> {
            byte[] bytes = switch (codePoint) {
                case LINE_FEED -> encode(SpecialKey.ENTER).orElseThrow();
                case TAB -> encode(SpecialKey.TAB).orElseThrow();
                default -> isControl(codePoint) ? new byte[0] : encode(codePoint);
            };
            if (bytes.length > 0) {
                characters.add(bytes);
            }
        });
        return characters;
    }

    /** How many characters {@code text} has as {@link #encodeText} reads it (CR LF is one line break). */
    public static int characterCount(String text) {
        int[] count = {0};
        forEachCharacter(text, codePoint -> count[0]++);
        return count[0];
    }

    /** Every code point of {@code text}, with CR LF and a lone CR given as one LF. */
    private static void forEachCharacter(String text, IntConsumer action) {
        Objects.requireNonNull(text, "text");
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            if (codePoint == CARRIAGE_RETURN) {
                if (index < text.length() && text.charAt(index) == LINE_FEED) {
                    index++;
                }
                codePoint = LINE_FEED;
            }
            action.accept(codePoint);
        }
    }

    private static boolean isControl(int codePoint) {
        return codePoint < FIRST_PRINTABLE_CODE || codePoint == DELETE;
    }

    /** Ctrl + {@code letter} (either case): the control code 1 (A) to 26 (Z). Anything but a letter A–Z throws. */
    public byte[] encodeControl(char letter) {
        char upper = Character.toUpperCase(letter);
        if (upper < 'A' || upper > 'Z') {
            throw new IllegalArgumentException("not a letter A-Z: " + letter);
        }
        return new byte[]{(byte) (upper & CONTROL_MASK)};
    }

    private static Map<SpecialKey, String> sequences() {
        Map<SpecialKey, String> sequences = new EnumMap<>(SpecialKey.class);
        sequences.put(SpecialKey.UP, CONTROL_SEQUENCE + "A");
        sequences.put(SpecialKey.DOWN, CONTROL_SEQUENCE + "B");
        sequences.put(SpecialKey.RIGHT, CONTROL_SEQUENCE + "C");
        sequences.put(SpecialKey.LEFT, CONTROL_SEQUENCE + "D");
        sequences.put(SpecialKey.HOME, CONTROL_SEQUENCE + "H");
        sequences.put(SpecialKey.END, CONTROL_SEQUENCE + "F");
        sequences.put(SpecialKey.DELETE, CONTROL_SEQUENCE + "3~");
        sequences.put(SpecialKey.F1, SINGLE_SHIFT_THREE + "P");
        sequences.put(SpecialKey.F2, SINGLE_SHIFT_THREE + "Q");
        sequences.put(SpecialKey.F3, SINGLE_SHIFT_THREE + "R");
        sequences.put(SpecialKey.F4, SINGLE_SHIFT_THREE + "S");
        // xterm skips 16 and 22 (the gaps of the VT220 keyboard)
        sequences.put(SpecialKey.F5, CONTROL_SEQUENCE + "15~");
        sequences.put(SpecialKey.F6, CONTROL_SEQUENCE + "17~");
        sequences.put(SpecialKey.F7, CONTROL_SEQUENCE + "18~");
        sequences.put(SpecialKey.F8, CONTROL_SEQUENCE + "19~");
        sequences.put(SpecialKey.F9, CONTROL_SEQUENCE + "20~");
        sequences.put(SpecialKey.F10, CONTROL_SEQUENCE + "21~");
        sequences.put(SpecialKey.F11, CONTROL_SEQUENCE + "23~");
        sequences.put(SpecialKey.F12, CONTROL_SEQUENCE + "24~");
        return Map.copyOf(sequences);
    }
}
