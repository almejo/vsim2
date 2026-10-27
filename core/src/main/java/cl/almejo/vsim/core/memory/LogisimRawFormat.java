package cl.almejo.vsim.core.memory;

import java.util.ArrayList;
import java.util.List;

/** Logisim's {@code v2.0 raw} memory image: a signature line, then hex words, with {@code count*word} runs. */
public final class LogisimRawFormat implements MemoryFileFormat {

    public static final String ID = "logisim";
    static final String SIGNATURE = "v2.0 raw";
    private static final int MINIMUM_RUN = 4;
    private static final int TOKENS_PER_LINE = 8;
    private static final long MAXIMUM_RUN = 1L << 24;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean canRead(String fileName, String firstLine) {
        String first = firstLine == null ? "" : firstLine.strip();
        if (first.startsWith("﻿")) {
            first = first.substring(1).strip();
        }
        return first.equals(SIGNATURE);
    }

    @Override
    public boolean canWrite() {
        return true;
    }

    /** Logisim images are told apart by their first line, not by extension. */
    @Override
    public List<String> extensions() {
        return List.of();
    }

    @Override
    public MemoryFile read(String text) throws MemoryFileProblem {
        String[] lines = HexText.lines(text);
        if (!lines[0].strip().equals(SIGNATURE)) {
            throw new MemoryFileProblem(1, "memory.file.notLogisim", List.of());
        }
        MutableMemoryContents contents = new MutableMemoryContents();
        long address = 0;
        for (int index = 1; index < lines.length; index++) {
            int lineNumber = index + 1;
            String line = lines[index];
            int comment = line.indexOf('#');
            String code = comment < 0 ? line : line.substring(0, comment);
            for (String token : code.trim().split("\\s+")) {
                if (token.isEmpty()) {
                    continue;
                }
                int star = token.indexOf('*');
                long count = 1;
                String wordText = token;
                if (star >= 0) {
                    count = parseCount(token.substring(0, star), token, lineNumber);
                    wordText = token.substring(star + 1);
                }
                long value = HexText.parseWord(wordText, lineNumber);
                if (value != 0) {
                    for (long repeat = 0; repeat < count; repeat++) {
                        contents.set(address + repeat, value);
                    }
                }
                address += count;
            }
        }
        return new MemoryFile(MemoryHeader.empty(), contents.snapshot());
    }

    private static long parseCount(String countText, String token, int lineNumber) throws MemoryFileProblem {
        long count;
        try {
            count = Long.parseLong(countText);
        } catch (NumberFormatException exception) {
            throw new MemoryFileProblem(lineNumber, "memory.file.notHex", List.of(token));
        }
        if (count < 1 || count > MAXIMUM_RUN) {
            throw new MemoryFileProblem(lineNumber, "memory.file.badRun", List.of(token));
        }
        return count;
    }

    @Override
    public String write(MemoryFile file, int width) {
        List<String> tokens = new ArrayList<>();
        long[] pending = {0, 0};
        long[] nextAddress = {0};
        file.contents().forEachNonZero((address, value) -> {
            if (address > nextAddress[0]) {
                flush(tokens, pending, 0, address - nextAddress[0]);
            }
            flush(tokens, pending, value, 1);
            nextAddress[0] = address + 1;
        });
        closeRun(tokens, pending);
        StringBuilder text = new StringBuilder(SIGNATURE).append('\n');
        for (int index = 0; index < tokens.size(); index++) {
            text.append(tokens.get(index));
            boolean lineEnd = (index + 1) % TOKENS_PER_LINE == 0 || index == tokens.size() - 1;
            text.append(lineEnd ? '\n' : ' ');
        }
        return text.toString();
    }

    /** Adds {@code count} copies of {@code value} to the run being built, closing the previous run if it differs. */
    private static void flush(List<String> tokens, long[] pending, long value, long count) {
        if (pending[1] > 0 && pending[0] != value) {
            closeRun(tokens, pending);
        }
        pending[0] = value;
        pending[1] += count;
    }

    private static void closeRun(List<String> tokens, long[] pending) {
        long count = pending[1];
        if (count == 0) {
            return;
        }
        String word = Long.toHexString(pending[0]);
        if (count >= MINIMUM_RUN) {
            tokens.add(count + "*" + word);
        } else {
            for (long repeat = 0; repeat < count; repeat++) {
                tokens.add(word);
            }
        }
        pending[1] = 0;
    }
}
