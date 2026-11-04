package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.memory.LogisimRawFormat;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.memory.MemoryFileProblem;

/**
 * A ROM's {@code contents} attribute: a header {@code addr/data: <address bits> <data bits>} and then the words in hex
 * with {@code count*word} runs, which is the body of Logisim's {@code v2.0 raw} image (gap analysis §5). The body goes
 * through VSim's own reader of that format ({@link LogisimRawFormat}), so both read a program the same way.
 */
final class LogisimMemoryContents {

    private static final String HEADER = "addr/data:";
    /** The first line of a {@code v2.0 raw} image, which the reader expects in front of the words. */
    private static final String RAW_SIGNATURE = "v2.0 raw";

    private LogisimMemoryContents() {
    }

    /**
     * The words of {@code attribute}, as VSim's memory parameter stores them ({@link MemoryContents#encode()}).
     *
     * @throws MemoryFileProblem when a word cannot be read; its line counts from the header, as in the file
     */
    static String encoded(String attribute) throws MemoryFileProblem {
        String text = attribute.stripLeading();
        return new LogisimRawFormat().read(RAW_SIGNATURE + "\n" + body(text)).contents().encode();
    }

    /** The words after the header line; text without the header is all words. */
    private static String body(String text) {
        if (!text.startsWith(HEADER)) {
            return text;
        }
        int headerEnd = text.indexOf('\n');
        return headerEnd < 0 ? "" : text.substring(headerEnd + 1);
    }
}
