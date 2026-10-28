package cl.almejo.vsim.core.memory;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VSim's own text format: hex words separated by spaces or newlines, {@code #} comments, {@code @address} jumps and
 * an optional first-line header {@code # vsim: <ram|rom> name=... words=... width=...}.
 */
public final class VsimMemoryFormat implements MemoryFileFormat {

    /** The byte order mark some editors put in front of a text file. */
    private static final char BYTE_ORDER_MARK = '\uFEFF';

    public static final String ID = "vsim";
    static final int MINIMUM_WORDS = 2;
    static final int MAXIMUM_WORDS = 1 << 24;
    static final int MAXIMUM_WIDTH = 64;
    private static final Pattern HEADER = Pattern.compile("^\\s*#\\s*vsim:(.*)$");
    private static final List<String> EXTENSIONS = List.of(".hex", ".mem", ".rom", ".ram", ".txt");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean canRead(String fileName, String firstLine) {
        String first = firstLine == null ? "" : firstLine.strip();
        if (!first.isEmpty() && first.charAt(0) == BYTE_ORDER_MARK) {
            first = first.substring(1);
        }
        if (first.equals(LogisimRawFormat.SIGNATURE)) {
            return false;
        }
        String lowerName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return HEADER.matcher(first).matches() || EXTENSIONS.stream().anyMatch(lowerName::endsWith);
    }

    @Override
    public boolean canWrite() {
        return true;
    }

    @Override
    public List<String> extensions() {
        return EXTENSIONS;
    }

    @Override
    public MemoryFile read(String text) throws MemoryFileProblem {
        String[] lines = HexText.lines(text);
        MemoryHeader header = readHeader(lines[0]);
        MutableMemoryContents contents = new MutableMemoryContents();
        long address = 0;
        for (int index = 0; index < lines.length; index++) {
            int lineNumber = index + 1;
            String line = lines[index];
            int comment = line.indexOf('#');
            String code = comment < 0 ? line : line.substring(0, comment);
            for (String token : code.trim().split("\\s+")) {
                if (token.isEmpty()) {
                    continue;
                }
                if (token.startsWith("@")) {
                    address = HexText.parseWord(token.substring(1), lineNumber);
                    if (address < 0) {
                        throw new MemoryFileProblem(lineNumber, "memory.file.notHex", List.of(token));
                    }
                } else {
                    long value = HexText.parseWord(token, lineNumber);
                    if (header.width().isPresent() && header.width().getAsInt() < Long.SIZE
                            && (value >>> header.width().getAsInt()) != 0) {
                        throw new MemoryFileProblem(lineNumber, "memory.file.tooWide",
                                List.of(token, header.width().getAsInt()));
                    }
                    contents.set(address, value);
                    address++;
                }
            }
        }
        return new MemoryFile(header, contents.snapshot());
    }

    private static MemoryHeader readHeader(String firstLine) throws MemoryFileProblem {
        Matcher matcher = HEADER.matcher(firstLine);
        if (!matcher.matches()) {
            return MemoryHeader.empty();
        }
        Optional<MemoryKind> kind = Optional.empty();
        Optional<String> name = Optional.empty();
        OptionalInt words = OptionalInt.empty();
        OptionalInt width = OptionalInt.empty();
        for (String token : matcher.group(1).trim().split("\\s+")) {
            int equals = token.indexOf('=');
            if (equals < 0) {
                Optional<MemoryKind> parsed = MemoryKind.fromId(token);
                kind = parsed.isPresent() ? parsed : kind;
                continue;
            }
            String key = token.substring(0, equals).toLowerCase(Locale.ROOT);
            String value = token.substring(equals + 1);
            switch (key) {
                case "name" -> name = value.isEmpty() ? name : Optional.of(value);
                case "words" -> words = OptionalInt.of(roundedWords(value));
                case "width" -> width = OptionalInt.of(checkedWidth(value));
                case "kind" -> kind = MemoryKind.fromId(value);
                default -> {
                    // Unknown keys are ignored so newer files stay readable.
                }
            }
        }
        return new MemoryHeader(kind, name, words, width);
    }

    private static int roundedWords(String value) throws MemoryFileProblem {
        long words = parseNumber(value, "memory.file.badWords");
        if (words < MINIMUM_WORDS || words > MAXIMUM_WORDS) {
            throw new MemoryFileProblem(1, "memory.file.badWords", List.of(value));
        }
        return (int) (Long.highestOneBit(words) == words ? words : Long.highestOneBit(words) << 1);
    }

    private static int checkedWidth(String value) throws MemoryFileProblem {
        long width = parseNumber(value, "memory.file.badWidth");
        if (width < 1 || width > MAXIMUM_WIDTH) {
            throw new MemoryFileProblem(1, "memory.file.badWidth", List.of(value));
        }
        return (int) width;
    }

    private static long parseNumber(String value, String messageKey) throws MemoryFileProblem {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new MemoryFileProblem(1, messageKey, List.of(value));
        }
    }

    @Override
    public String write(MemoryFile file, int width) {
        StringBuilder text = new StringBuilder();
        MemoryHeader header = file.header();
        if (!header.isEmpty()) {
            text.append("# vsim:");
            header.kind().ifPresent(kind -> text.append(' ').append(kind.id()));
            header.name().ifPresent(name -> text.append(" name=").append(name.replaceAll("\\s+", "_")));
            header.words().ifPresent(words -> text.append(" words=").append(words));
            header.width().ifPresent(headerWidth -> text.append(" width=").append(headerWidth));
            text.append('\n');
        }
        int digits = Math.max(1, (width + 3) / 4);
        long[] state = {-1, 0};
        file.contents().forEachNonZero((address, value) -> {
            if (address != state[0]) {
                if (state[1] > 0) {
                    text.append('\n');
                }
                text.append('@').append(HexText.hexDigits(address, 3, true)).append('\n');
                state[1] = 0;
            } else if (state[1] == 8) {
                text.append('\n');
                state[1] = 0;
            } else {
                text.append(' ');
            }
            text.append(HexText.hexDigits(value, digits, true));
            state[1]++;
            state[0] = address + 1;
        });
        if (state[1] > 0) {
            text.append('\n');
        }
        return text.toString();
    }
}
