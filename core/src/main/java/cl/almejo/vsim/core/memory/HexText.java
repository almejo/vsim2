package cl.almejo.vsim.core.memory;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Helpers shared by the text memory formats. */
final class HexText {

    static final int MAXIMUM_DIGITS = 16;
    private static final char BYTE_ORDER_MARK = '\uFEFF';
    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]+");
    private static final Pattern LINE_BREAK = Pattern.compile("\\r\\n|\\r|\\n");

    private HexText() {
    }

    /** The lines of {@code text}, without a leading BOM and without line terminators (CRLF, CR or LF). */
    static String[] lines(String text) {
        String clean = text.startsWith("﻿") ? text.substring(1) : text;
        return LINE_BREAK.split(clean, -1);
    }

    /** The first line of a file stripped, without a leading BOM (what may follow the BOM is not stripped again). */
    static String firstLineWithoutBom(String firstLine) {
        String first = firstLine == null ? "" : firstLine.strip();
        if (!first.isEmpty() && first.charAt(0) == BYTE_ORDER_MARK) {
            first = first.substring(1);
        }
        return first;
    }

    /** The tokens of {@code line} before its {@code #} comment, split at whitespace; none for a blank line. */
    static List<String> tokens(String line) {
        int comment = line.indexOf('#');
        String code = comment < 0 ? line : line.substring(0, comment);
        return Arrays.stream(code.trim().split("\\s+")).filter(token -> !token.isEmpty()).toList();
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
