package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.memory.MemoryLinkPaths;
import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.CompositeCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.memory.MemoryFile;
import cl.almejo.vsim.core.memory.MemoryFormats;
import cl.almejo.vsim.core.memory.MemoryHeader;
import cl.almejo.vsim.core.memory.MemoryKind;
import cl.almejo.vsim.ui.app.FileNames;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.window.EditorView;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A memory file made a new RAM or ROM (memory spec §8.5), from Archivo ▸ Importar ▸ «Memoria desde archivo…» or a
 * drop on the canvas: placed at the drop point (or the middle of the canvas), linked to the file, as one undo step
 * «Importar memoria». A file whose header says RAM or ROM is made at once; otherwise a small dialog asks, with the
 * words, width and name the file suggests.
 */
public final class MemoryFileImport {

    /** What a new memory will be: its kind, name («Nombre»), words and width. */
    public record Proposal(MemoryKind kind, String name, long words, int width) {
    }

    /** The first bytes read to find out whether a file is a memory file: enough for any header line. */
    private static final int FIRST_LINE_BYTES = 512;
    /** The width proposed for a file of zeros. */
    private static final int DEFAULT_WIDTH = 8;
    private static final int BITS_PER_DIGIT = 4;
    private static final long MAXIMUM_WORDS = 1L << MemoryTypes.MAXIMUM_ADDRESS_BITS;

    private final MemoryLinkPrompts prompts;

    MemoryFileImport(MemoryLinkPrompts prompts) {
        this.prompts = prompts;
    }

    /** What takes the memory files opened in or dropped on {@code view} (see {@link EditorView#setMemoryFileImporter}). */
    EditorView.PositionedFileImporter importerFor(EditorView view) {
        return new EditorView.PositionedFileImporter() {
            @Override
            public boolean accepts(Path file) {
                return isMemoryFile(file);
            }

            @Override
            public void importFiles(List<Path> files, Optional<GridPoint> position) {
                GridPoint at = position.orElseGet(() -> middleOf(view.canvas()));
                for (Path file : files) {
                    importFile(view, file, at);
                }
            }
        };
    }

    /**
     * Whether {@code file} is a memory file: not a circuit, and a memory format recognizes its name or its first line
     * ({@code .hex}, {@code .mem}, {@code .rom}, {@code .ram}, {@code .txt}, a VSim header, Logisim's
     * {@code v2.0 raw}). A file that cannot be read is not one.
     */
    public static boolean isMemoryFile(Path file) {
        Path fileName = file.getFileName();
        if (fileName == null || !Files.isRegularFile(file)) {
            return false;
        }
        String name = fileName.toString();
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(FileNames.EXTENSION) || lowerName.endsWith(".json")) {
            return false;
        }
        Optional<String> firstLine = firstLine(file);
        return firstLine.isPresent() && MemoryFormats.forReading(name, firstLine.get()).isPresent();
    }

    private static Optional<String> firstLine(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            String start = new String(input.readNBytes(FIRST_LINE_BYTES), StandardCharsets.UTF_8);
            return Optional.of(start.lines().findFirst().orElse(""));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    /**
     * What {@code file} suggests: the header's values, and for what it does not say the name of the file, the words
     * rounded up to a power of 2 and a width of whole hex digits for the widest word. ROM when the kind is unknown
     * (most files dropped are programs).
     */
    static Proposal propose(Path file, MemoryFile read) {
        MemoryHeader header = read.header();
        MemoryContents contents = read.contents();
        String name = header.name().orElseGet(() -> baseName(file));
        long words = header.words().isPresent() ? header.words().getAsInt() : wordsFor(contents);
        int width = header.width().isPresent() ? header.width().getAsInt() : widthFor(contents);
        return new Proposal(header.kind().orElse(MemoryKind.ROM), name, words, width);
    }

    /** Archivo ▸ Importar ▸ «Memoria desde archivo…»: asks for a file and places it in the middle of the canvas. */
    void chooseAndImport(EditorView view) {
        prompts.chooseMemoryFile(view).ifPresent(file -> importFile(view, file, middleOf(view.canvas())));
    }

    /** Reads {@code file} and adds its memory at {@code position}; an unreadable file is explained, nothing added. */
    Optional<Component> importFile(EditorView view, Path file, GridPoint position) {
        MemoryFile read;
        try {
            read = MemoryFileIo.read(file);
        } catch (MemoryFileIo.Unreadable problem) {
            prompts.inform(view, problem.getMessage());
            return Optional.empty();
        }
        Proposal proposal = propose(file, read);
        Optional<Proposal> chosen = read.header().kind().isPresent() ? Optional.of(proposal)
                : prompts.chooseKind(view, String.valueOf(file.getFileName()), proposal);
        return chosen.map(memory -> add(view, memory, read.contents(), file, position));
    }

    private static Component add(EditorView view, Proposal proposal, MemoryContents contents, Path file,
                                 GridPoint position) {
        ComponentType type = view.openDocument().registry().require(proposal.kind().id());
        Map<String, Object> parameters = Map.of(MemoryTypes.WORDS, String.valueOf(proposal.words()),
                MemoryTypes.WIDTH, proposal.width(), MemoryTypes.CONTENTS, contents.encode(),
                MemoryTypes.LINK, MemoryLinkPaths.storedLinkPath(file, Optional.empty()));
        Component component = type.newComponent(view.openDocument().document().newComponentId(), position,
                Rotation.R0, parameters, proposal.name().strip());
        view.openDocument().execute(new CompositeCommand("command.importMemory",
                List.of(new AddElementsCommand(List.of(component)))));
        view.selection().select(component.id());
        return component;
    }

    private static GridPoint middleOf(CircuitCanvas canvas) {
        return canvas.viewport().snap(canvas.getWidth() / 2, canvas.getHeight() / 2);
    }

    private static String baseName(Path file) {
        String name = String.valueOf(file.getFileName());
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** The power of 2 that holds every word of {@code contents}, from 2 to 16M. */
    static long wordsFor(MemoryContents contents) {
        long needed = Math.max(2, contents.highestAddress() + 1);
        if (needed >= MAXIMUM_WORDS) {
            return MAXIMUM_WORDS;
        }
        return Long.highestOneBit(needed - 1) << 1;
    }

    /** Whole hex digits for the widest word: «3A» and «FF» are 8 bits, «FFF» 12; 8 for a file of zeros. */
    private static int widthFor(MemoryContents contents) {
        int bits = contents.widestBits();
        if (bits == 0) {
            return DEFAULT_WIDTH;
        }
        return Math.min(MemoryTypes.MAXIMUM_WIDTH, (bits + BITS_PER_DIGIT - 1) / BITS_PER_DIGIT * BITS_PER_DIGIT);
    }
}
