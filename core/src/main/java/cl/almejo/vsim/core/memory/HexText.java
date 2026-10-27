package cl.almejo.vsim.core.memory;

import java.util.List;
import java.util.regex.Pattern;

/** Helpers shared by the text memory formats. */
final class HexText {

    static final int MAXIMUM_DIGITS = 16;
    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]+");
    private static final Pattern LINE_BREAK = Pattern.compile("\\r\\n|\\r|\\n");

    private HexText() {
    }

    /** The lines of {@code text}, without a leading BOM and without line terminators (CRLF, CR or LF). */
    static String[] lines(String text) {
        String clean = text.startsWith("﻿") ? text.substring(1) : text;
        return LINE_BREAK.split(clean, -1);
    }

    /** The first line of {@code text} (without BOM), or "" when empty. */
    static String firstLine(String text) {
        return lines(text)[0];
    }

    /** A word of up to 16 hex digits, {@code _} allowed between digits. */
    static long parseWord(String token, int line) throws MemoryFileProblem {
        String digits = token.replace("_", "");
        if (digits.isEmpty() || !HEX.matcher(digits).matches() || token.startsWith("_") || token.endsWith("_")) {
            throw new MemoryFileProblem(line, "memory.file.notHex", List.of(token));
        }
        if (digits.length() > MAXIMUM_DIGITS) {
            throw new MemoryFileProblem(line, "memory.file.tooLong", List.of(token, MAXIMUM_DIGITS));
        }
        return Long.parseUnsignedLong(digits, 16);
    }

    static String hexDigits(long value, int minimumDigits, boolean upperCase) {
        String digits = Long.toHexString(value);
        if (upperCase) {
            digits = digits.toUpperCase();
        }
        return digits.length() >= minimumDigits ? digits : "0".repeat(minimumDigits - digits.length()) + digits;
    }
}
