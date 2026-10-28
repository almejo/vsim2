package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.memory.MemoryFile;
import cl.almejo.vsim.core.memory.MemoryFileFormat;
import cl.almejo.vsim.core.memory.MemoryFileProblem;
import cl.almejo.vsim.core.memory.MemoryFormats;
import cl.almejo.vsim.ui.i18n.Messages;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Reads and writes memory files in the formats of {@link MemoryFormats}, and words their problems for the user. */
public final class MemoryFileIo {

    /** A file that cannot be used: the message is already translated. */
    public static final class Unreadable extends Exception {
        public Unreadable(String message) {
            super(message);
        }
    }

    private MemoryFileIo() {
    }

    /** Reads {@code file} (UTF-8, any line ends, optional BOM) with the format that recognizes it, VSim's by default. */
    public static MemoryFile read(Path file) throws Unreadable {
        String text;
        try {
            // a strict decoder: a file that is not UTF-8 is reported, never half-read
            text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(Files.readAllBytes(file))).toString();
        } catch (CharacterCodingException exception) {
            throw new Unreadable(Messages.format("memory.file.notUtf8", file.getFileName()));
        } catch (IOException exception) {
            throw new Unreadable(Messages.format("memory.file.unreadable", file.getFileName(),
                    String.valueOf(exception.getMessage())));
        }
        String firstLine = text.lines().findFirst().orElse("");
        MemoryFileFormat format = MemoryFormats.forReading(file.getFileName().toString(), firstLine)
                .orElse(MemoryFormats.all().get(0));
        try {
            return format.read(text);
        } catch (MemoryFileProblem problem) {
            throw new Unreadable(describe(file, problem));
        }
    }

    public static void write(Path file, MemoryFileFormat format, MemoryFile contents, int width) throws Unreadable {
        try {
            Files.writeString(file, format.write(contents, width), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new Unreadable(Messages.format("memory.file.unwritable", file.getFileName(),
                    String.valueOf(exception.getMessage())));
        }
    }

    /** «programa.hex, línea 4: C6G1 no es hexadecimal». */
    public static String describe(Path file, MemoryFileProblem problem) {
        Object[] arguments = problem.arguments().stream().map(String::valueOf).toArray();
        return Messages.format("memory.file.problem", file.getFileName(), problem.line(),
                Messages.format(problem.messageKey(), arguments));
    }

    /** {@code path} with the format's first extension when it has none (a Logisim file gets {@code .txt}). */
    public static Path withExtension(Path path, MemoryFileFormat format) {
        if (path.getFileName().toString().contains(".")) {
            return path;
        }
        List<String> extensions = format.extensions();
        String extension = extensions.isEmpty() ? ".txt" : extensions.get(0);
        return path.resolveSibling(path.getFileName() + extension.toLowerCase(Locale.ROOT));
    }
}
