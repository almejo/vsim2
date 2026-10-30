package cl.almejo.vsim.core.terminal;

import java.util.Arrays;

/**
 * Decodes UTF-8 one byte at a time, keeping a half-received character between writes (a CPU sends one byte per clock).
 * Follows the WHATWG decoder: overlong forms, surrogates and code points past U+10FFFF are rejected; a bad sequence
 * gives one U+FFFD and the byte that broke it is decoded again on its own, so the text after it is not lost.
 */
final class Utf8Decoder {

    /** Receives each decoded character with the bytes that made it (the array is the receiver's to keep). */
    @FunctionalInterface
    interface Sink {
        void accept(int codePoint, byte[] bytes);
    }

    /** U+FFFD REPLACEMENT CHARACTER, shown for a bad sequence (spec §5). */
    static final int REPLACEMENT_CHARACTER = 0xFFFD;
    private static final int LONGEST_SEQUENCE = 4;
    private static final int LAST_ASCII = 0x7F;
    private static final int FIRST_CONTINUATION = 0x80;
    private static final int LAST_CONTINUATION = 0xBF;
    private static final int CONTINUATION_PAYLOAD = 0x3F;
    private static final int CONTINUATION_BITS = 6;
    /** C0 and C1 would be overlong two-byte forms of ASCII. */
    private static final int FIRST_TWO_BYTE_LEAD = 0xC2;
    private static final int FIRST_THREE_BYTE_LEAD = 0xE0;
    private static final int FIRST_FOUR_BYTE_LEAD = 0xF0;
    /** F5–FF would start code points past U+10FFFF. */
    private static final int LAST_FOUR_BYTE_LEAD = 0xF4;
    /** After E0 the second byte starts at A0 (no overlong three-byte forms). */
    private static final int OVERLONG_THREE_BYTE_LEAD = 0xE0;
    private static final int LOWEST_AFTER_OVERLONG_THREE_BYTE_LEAD = 0xA0;
    /** After ED the second byte ends at 9F (no UTF-16 surrogates). */
    private static final int SURROGATE_LEAD = 0xED;
    private static final int HIGHEST_AFTER_SURROGATE_LEAD = 0x9F;
    /** After F0 the second byte starts at 90 (no overlong four-byte forms). */
    private static final int OVERLONG_FOUR_BYTE_LEAD = 0xF0;
    private static final int LOWEST_AFTER_OVERLONG_FOUR_BYTE_LEAD = 0x90;
    /** After F4 the second byte ends at 8F (nothing past U+10FFFF). */
    private static final int HIGHEST_AFTER_LAST_FOUR_BYTE_LEAD = 0x8F;
    private static final int TWO_BYTE_LEAD_PAYLOAD = 0x1F;
    private static final int THREE_BYTE_LEAD_PAYLOAD = 0x0F;
    private static final int FOUR_BYTE_LEAD_PAYLOAD = 0x07;
    /** One shared array per ASCII byte, so plain text allocates nothing here. */
    private static final byte[][] ASCII_BYTES = new byte[LAST_ASCII + 1][];

    static {
        for (int value = 0; value <= LAST_ASCII; value++) {
            ASCII_BYTES[value] = new byte[] {(byte) value};
        }
    }

    private final byte[] pending = new byte[LONGEST_SEQUENCE];
    private int pendingCount;
    private int bytesNeeded;
    private int codePoint;
    private int lowestNext = FIRST_CONTINUATION;
    private int highestNext = LAST_CONTINUATION;

    /** The shared one-byte array of an ASCII byte; callers must not change it. */
    static byte[] asciiBytes(int value) {
        return ASCII_BYTES[value];
    }

    /** Feeds one byte (0–255); calls {@code sink} for every character it completes (zero, one or two). */
    void feed(int byteValue, Sink sink) {
        if (bytesNeeded == 0) {
            start(byteValue, sink);
            return;
        }
        if (byteValue < lowestNext || byteValue > highestNext) {
            sink.accept(REPLACEMENT_CHARACTER, Arrays.copyOf(pending, pendingCount));
            reset();
            start(byteValue, sink);
            return;
        }
        lowestNext = FIRST_CONTINUATION;
        highestNext = LAST_CONTINUATION;
        codePoint = (codePoint << CONTINUATION_BITS) | (byteValue & CONTINUATION_PAYLOAD);
        pending[pendingCount++] = (byte) byteValue;
        if (--bytesNeeded == 0) {
            int complete = codePoint;
            byte[] bytes = Arrays.copyOf(pending, pendingCount);
            reset();
            sink.accept(complete, bytes);
        }
    }

    /** Whether a character has started and not finished. */
    boolean isPending() {
        return bytesNeeded > 0;
    }

    /** Forgets a half-received character. */
    void reset() {
        pendingCount = 0;
        bytesNeeded = 0;
        codePoint = 0;
        lowestNext = FIRST_CONTINUATION;
        highestNext = LAST_CONTINUATION;
    }

    private void start(int byteValue, Sink sink) {
        if (byteValue <= LAST_ASCII) {
            sink.accept(byteValue, ASCII_BYTES[byteValue]);
            return;
        }
        if (byteValue < FIRST_TWO_BYTE_LEAD || byteValue > LAST_FOUR_BYTE_LEAD) {
            sink.accept(REPLACEMENT_CHARACTER, new byte[] {(byte) byteValue});
            return;
        }
        if (byteValue < FIRST_THREE_BYTE_LEAD) {
            bytesNeeded = 1;
            codePoint = byteValue & TWO_BYTE_LEAD_PAYLOAD;
        } else if (byteValue < FIRST_FOUR_BYTE_LEAD) {
            bytesNeeded = 2;
            codePoint = byteValue & THREE_BYTE_LEAD_PAYLOAD;
            if (byteValue == OVERLONG_THREE_BYTE_LEAD) {
                lowestNext = LOWEST_AFTER_OVERLONG_THREE_BYTE_LEAD;
            } else if (byteValue == SURROGATE_LEAD) {
                highestNext = HIGHEST_AFTER_SURROGATE_LEAD;
            }
        } else {
            bytesNeeded = 3;
            codePoint = byteValue & FOUR_BYTE_LEAD_PAYLOAD;
            if (byteValue == OVERLONG_FOUR_BYTE_LEAD) {
                lowestNext = LOWEST_AFTER_OVERLONG_FOUR_BYTE_LEAD;
            } else if (byteValue == LAST_FOUR_BYTE_LEAD) {
                highestNext = HIGHEST_AFTER_LAST_FOUR_BYTE_LEAD;
            }
        }
        pending[pendingCount++] = (byte) byteValue;
    }
}
