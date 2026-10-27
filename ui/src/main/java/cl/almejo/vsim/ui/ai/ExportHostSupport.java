package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.export.ExportReceipt;
import cl.almejo.vsim.automation.export.ExportRequest;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.export.CircuitImage;
import cl.almejo.vsim.ui.export.ImageExport;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.StatusBar;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.paint.TextFonts;
import cl.almejo.vsim.ui.print.PageGeometry;
import cl.almejo.vsim.ui.print.PageLayout;
import cl.almejo.vsim.ui.print.PageOrientation;
import cl.almejo.vsim.ui.print.PagePainter;
import cl.almejo.vsim.ui.print.Paper;
import cl.almejo.vsim.ui.print.PdfExport;
import cl.almejo.vsim.ui.print.PdfExporter;
import cl.almejo.vsim.ui.print.PrintArea;
import cl.almejo.vsim.ui.print.PrintInput;
import cl.almejo.vsim.ui.print.PrintOptions;
import cl.almejo.vsim.ui.print.PrintScope;
import cl.almejo.vsim.ui.print.PrintScopes;
import cl.almejo.vsim.ui.print.ScaleMode;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The AI's exports (spec §7, Exports row): Archivo ▸ «Exportar imagen…» and «Exportar a PDF…» without their dialogs,
 * through the same code ({@link CircuitImage}, {@link ImageExport}, {@link PrintInput}, {@link PagePainter},
 * {@link PdfExport}) and with the same options. The file goes only into the folder VSim remembers for that export
 * (an image: the image export's folder; a PDF: the PDF's; each falls back on the other, and with neither on the
 * user's {@code ~/VSim}), under a new name: never over an existing file ({@code -2}, {@code -3}… instead). The
 * picture is taken on the EDT, as the menus do; the file is written on {@code writer}. The circuit's windows then
 * say «La IA guardó …» in their status line, with «Abrir carpeta» where the desktop can show a folder. Nothing here
 * asks the user anything or shows a dialog.
 */
final class ExportHostSupport {

    /** The folder an export goes to when the user never exported (spec §7). */
    static final String DEFAULT_FOLDER = "VSim";
    /** Suffixes tried before giving up: name-2 … name-9999. */
    static final int MAXIMUM_SUFFIX = 9_999;
    /** A file name keeps at most this many characters (code points) of the name asked for… */
    static final int MAXIMUM_NAME_LENGTH = 80;
    /** …and at most this many bytes in UTF-8, so the suffix and extension still fit the usual 255-byte limit. */
    static final int MAXIMUM_NAME_BYTES = 200;
    /** Names Windows keeps for devices, alone or before a dot. */
    private static final Pattern RESERVED_NAME = Pattern.compile("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?");

    private final Function<OpenDocument, List<ShownWindow>> windowsOf;
    private final Supplier<Path> home;
    private final Executor writer;
    private final Executor editorThread;
    private final Clock clock;
    private final FolderOpener folderOpener;

    /**
     * @param windowsOf    the windows showing a circuit (their preferences, scheme and status line); EDT only
     * @param home         the user's home folder
     * @param writer       where files are written (never the EDT)
     * @param editorThread runs the status notices on the EDT
     * @param folderOpener «Abrir carpeta» on the notice
     */
    ExportHostSupport(Function<OpenDocument, List<ShownWindow>> windowsOf, Supplier<Path> home, Executor writer,
                      Executor editorThread, Clock clock, FolderOpener folderOpener) {
        this.windowsOf = Objects.requireNonNull(windowsOf, "windowsOf");
        this.home = Objects.requireNonNull(home, "home");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.editorThread = Objects.requireNonNull(editorThread, "editorThread");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.folderOpener = Objects.requireNonNull(folderOpener, "folderOpener");
    }

    /** What the EDT prepared: everything the file needs, and how to write it into a reserved path. */
    record Prepared(DocumentId document, DocumentVersion version, OpenDocument openDocument,
                    ExportRequest.Format format, Path folder, String baseName, FileWriter fileWriter, int width,
                    int height, int pages) {
    }

    @FunctionalInterface
    interface FileWriter {
        void write(Path target) throws IOException;
    }

    /** EDT: checks the request, takes the picture and chooses the folder; nothing is written yet. */
    Prepared prepare(DocumentId id, DocumentVersion version, OpenDocument document, ExportRequest request) {
        Optional<ShownWindow> window = windowsOf.apply(document).stream().findFirst();
        AppPreferences preferences = window.map(shown -> shown.view().services().preferences())
                .orElse(null);
        ColorScheme windowScheme = window.map(shown -> shown.view().services().appearance().scheme())
                .orElse(ColorScheme.LIGHT);
        Path folder = folderFor(request.format(), Optional.ofNullable(preferences));
        String baseName = baseName(request.fileName().orElse(document.displayName()));
        return switch (request.format()) {
            case PNG, SVG -> prepareImage(id, version, document, request, folder, baseName, windowScheme);
            case PDF -> preparePdf(id, version, document, request, folder, baseName, windowScheme);
        };
    }

    /** Off the EDT: reserves a new name in the folder, writes the file there and tells the circuit's windows. */
    ExportReceipt write(Prepared prepared) {
        try {
            Files.createDirectories(prepared.folder());
        } catch (IOException exception) {
            throw new AutomationException(AutomationError.NOT_ALLOWED, "The export folder " + prepared.folder()
                    + " cannot be created: " + exception.getMessage(), Map.of("folder", prepared.folder().toString()));
        }
        Path target = reserve(prepared.folder(), prepared.baseName(), prepared.format().extension());
        try {
            prepared.fileWriter().write(target);
        } catch (IOException | RuntimeException failure) {
            deleteQuietly(target);
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                    ? failure.getCause() : failure;
            throw new AutomationException(AutomationError.NOT_ALLOWED, "The file could not be written: "
                    + (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()),
                    Map.of("folder", prepared.folder().toString()));
        }
        String fileName = target.getFileName().toString();
        editorThread.execute(() -> tellTheWindows(prepared.openDocument(), target, prepared.folder()));
        return new ExportReceipt(prepared.document(), prepared.version(), prepared.format(), fileName,
                prepared.folder().toString(), target.toString(), prepared.width(), prepared.height(),
                prepared.pages());
    }

    Executor writer() {
        return writer;
    }

    // --- images ---

    private Prepared prepareImage(DocumentId id, DocumentVersion version, OpenDocument document,
                                  ExportRequest request, Path folder, String baseName, ColorScheme windowScheme) {
        ExportRequest.ImageOptions options = request.image();
        ColorScheme scheme = options.printBackground() ? CircuitImage.printScheme(ColorScheme.LIGHT) : windowScheme;
        CircuitImage image = switch (request.scope()) {
            case ExportRequest.Scope.WholeCircuit whole -> CircuitImage.of(document.document(), document.netlist(),
                    document.netlistToken(), document.latestSnapshot(), document.registry(),
                    PainterRegistry.standard(), scheme, Optional.empty(), request.includeStickyNotes())
                    .orElseThrow(ExportHostSupport::nothing);
            case ExportRequest.Scope.Elements elements -> CircuitImage.of(document.document(), document.netlist(),
                    document.netlistToken(), document.latestSnapshot(), document.registry(),
                    PainterRegistry.standard(), scheme, Optional.of(elementIds(document, elements.elementIds())),
                    request.includeStickyNotes()).orElseThrow(ExportHostSupport::nothing);
            case ExportRequest.Scope.Frame frame -> CircuitImage.ofRegion(document.document(), document.netlist(),
                    document.netlistToken(), document.latestSnapshot(), document.registry(),
                    PainterRegistry.standard(), scheme, frameBounds(document, frame.frameId()),
                    request.includeStickyNotes());
        };
        if (request.format() == ExportRequest.Format.SVG) {
            String svg = ImageExport.svgDocument(image);
            return new Prepared(id, version, document, request.format(), folder, baseName,
                    target -> ImageExport.writeSvgInto(target, svg), image.width(1), image.height(1), 1);
        }
        BufferedImage pixels;
        try {
            pixels = ImageExport.pngPixels(image, options.scale());
        } catch (IOException tooLarge) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "The image would be too large ("
                    + image.width(options.scale()) + " × " + image.height(options.scale())
                    + " pixels); use a smaller scale", Map.of("scales", ExportRequest.ImageOptions.SCALES));
        }
        return new Prepared(id, version, document, request.format(), folder, baseName,
                target -> ImageExport.writePngInto(target, pixels), pixels.getWidth(), pixels.getHeight(), 1);
    }

    private static Bounds frameBounds(OpenDocument document, String frameId) {
        Frame frame = document.document().frames().stream()
                .filter(candidate -> candidate.id().value().equals(frameId))
                .findFirst()
                .orElseThrow(() -> new AutomationException(AutomationError.INVALID_REFERENCE, "No frame " + frameId
                        + " in this circuit", Map.of("frames", document.document().frames().stream()
                        .map(candidate -> candidate.id().value()).toList())));
        // The border is drawn on the rectangle: one unit around keeps it whole.
        return new Bounds(frame.topLeft().x() - 1, frame.topLeft().y() - 1, frame.width() + 2, frame.height() + 2);
    }

    private static Set<ElementId> elementIds(OpenDocument document, Set<String> ids) {
        List<String> unknown = ids.stream()
                .filter(id -> document.document().element(new ElementId(id)).isEmpty())
                .sorted()
                .toList();
        if (!unknown.isEmpty()) {
            throw new AutomationException(AutomationError.INVALID_REFERENCE, "No elements " + unknown
                    + " in this circuit", Map.of("unknown", unknown));
        }
        return ids.stream().map(ElementId::new).collect(Collectors.toUnmodifiableSet());
    }

    // --- PDF ---

    private Prepared preparePdf(DocumentId id, DocumentVersion version, OpenDocument document, ExportRequest request,
                                Path folder, String baseName, ColorScheme windowScheme) {
        ExportRequest.PdfOptions chosen = request.pdf();
        Set<ElementId> selection = request.scope() instanceof ExportRequest.Scope.Elements elements
                ? elementIds(document, elements.elementIds()) : Set.of();
        PrintInput input = PrintInput.capture(document, selection, windowScheme, TextFonts.current(), clock);
        PrintScope scope = switch (request.scope()) {
            case ExportRequest.Scope.WholeCircuit whole -> new PrintScope.WholeCircuit();
            case ExportRequest.Scope.Elements elements -> new PrintScope.Selection(selection);
            case ExportRequest.Scope.Frame frame -> {
                frameBounds(document, frame.frameId());
                yield new PrintScope.Frame(new ElementId(frame.frameId()));
            }
        };
        ScaleMode scaleMode = chosen.scalePercent() == 0 ? new ScaleMode.FitToOnePage()
                : new ScaleMode.Percentage(chosen.scalePercent());
        String title = chosen.title().isBlank() ? withoutExtension(document.displayName()) : chosen.title();
        PrintOptions options = new PrintOptions(scope, Paper.valueOf(chosen.paper()),
                PageOrientation.valueOf(chosen.orientation()), scaleMode, chosen.withValues(), chosen.blackAndWhite(),
                chosen.headerFooter(), title, request.includeStickyNotes());
        PrintArea area = PrintScopes.area(input, scope, request.includeStickyNotes()).orElseThrow(ExportHostSupport::nothing);
        PageLayout layout = PageLayout.of(area.gridArea(), PageGeometry.standard(options.paper(),
                options.orientation(), options.headerFooter()), options.scaleMode());
        if (layout.problem().isPresent()) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "The circuit does not fit on the sheet "
                    + "with these options; try another paper, orientation or scale",
                    Map.of("problem", layout.problem().get()));
        }
        if (layout.pageCountExact() > PdfExport.REFUSE_ABOVE_PAGES) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "That would be "
                    + layout.pageCountExact() + " sheets; at most " + PdfExport.REFUSE_ABOVE_PAGES
                    + " (fit it to one sheet or use a smaller scale)",
                    Map.of("maximum", PdfExport.REFUSE_ABOVE_PAGES));
        }
        PagePainter painter = new PagePainter(input, area, options, layout);
        int pages = painter.pages().size();
        PdfExporter exporter = new PdfExport(Runnable::run);
        return new Prepared(id, version, document, request.format(), folder, baseName,
                target -> exporter.export(painter, target, new PdfExporter.ProgressListener() {
                    @Override
                    public void pageDone(int done, int total) {
                    }

                    @Override
                    public boolean cancelled() {
                        return false;
                    }
                }).join(), 0, 0, pages);
    }

    // --- folder and name ---

    /** The format's remembered folder, else the other export's, else {@code ~/VSim}; only folders that exist. */
    Path folderFor(ExportRequest.Format format, Optional<AppPreferences> preferences) {
        Optional<Path> image = preferences.flatMap(AppPreferences::exportDirectory);
        Optional<Path> pdf = preferences.flatMap(AppPreferences::printDirectory);
        Optional<Path> first = format == ExportRequest.Format.PDF ? pdf : image;
        Optional<Path> second = format == ExportRequest.Format.PDF ? image : pdf;
        return first.filter(Files::isDirectory)
                .or(() -> second.filter(Files::isDirectory))
                .orElseGet(() -> home.get().resolve(DEFAULT_FOLDER))
                .toAbsolutePath();
    }

    /**
     * A file name of {@code wanted}: no folders, no characters file systems refuse, no extension, no lone surrogate,
     * cut whole within {@value #MAXIMUM_NAME_LENGTH} characters and {@value #MAXIMUM_NAME_BYTES} UTF-8 bytes, no
     * trailing dots or spaces, and a {@code _} before a name Windows reserves.
     */
    static String baseName(String wanted) {
        String cleaned = withoutExtension(wanted.strip())
                .replaceAll("[/\\\\:*?\"<>|\\p{Cntrl}]", "_")
                .replaceAll("^[.\\s]+", "")
                .strip();
        StringBuilder name = new StringBuilder();
        int characters = 0;
        int bytes = 0;
        for (int codePoint : cleaned.codePoints().toArray()) {
            if (Character.getType(codePoint) == Character.SURROGATE) {
                continue; // a lone half of a pair: no file system takes it
            }
            int size = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (characters == MAXIMUM_NAME_LENGTH || bytes + size > MAXIMUM_NAME_BYTES) {
                break;
            }
            name.appendCodePoint(codePoint);
            characters++;
            bytes += size;
        }
        String result = name.toString().replaceAll("[.\\s]+$", "");
        if (result.isEmpty()) {
            return "circuito";
        }
        return RESERVED_NAME.matcher(result).matches() ? "_" + result : result;
    }

    private static String withoutExtension(String name) {
        for (String extension : List.of(".vsim", ".pdf", ".png", ".svg")) {
            if (name.toLowerCase(Locale.ROOT).endsWith(extension)) {
                return name.substring(0, name.length() - extension.length());
            }
        }
        return name;
    }

    /** Creates {@code name.ext}, else {@code name-2.ext}, …: the first that did not exist, created empty now. */
    static Path reserve(Path folder, String baseName, String extension) {
        for (int suffix = 1; suffix <= MAXIMUM_SUFFIX; suffix++) {
            Path candidate;
            try {
                candidate = folder.resolve(baseName + (suffix == 1 ? "" : "-" + suffix) + "." + extension);
            } catch (InvalidPathException invalid) {
                throw new AutomationException(AutomationError.INVALID_PARAMETER, "That file name cannot be used here: "
                        + invalid.getReason() + "; give another fileName");
            }
            try {
                return Files.createFile(candidate);
            } catch (FileAlreadyExistsException taken) {
                // the next suffix
            } catch (IOException exception) {
                throw new AutomationException(AutomationError.NOT_ALLOWED, "The export folder " + folder
                        + " cannot be written: " + exception.getMessage(), Map.of("folder", folder.toString()));
            }
        }
        throw new AutomationException(AutomationError.NOT_ALLOWED, "Every name from " + baseName + " to " + baseName
                + "-" + MAXIMUM_SUFFIX + " is taken in " + folder + "; give another fileName",
                Map.of("folder", folder.toString()));
    }

    private static void deleteQuietly(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // an empty file left behind is harmless; it was ours
        }
    }

    private void tellTheWindows(OpenDocument document, Path file, Path folder) {
        String notice = Messages.format("ai.export.saved", file.getFileName().toString(), folder.toString());
        boolean canOpen = folderOpener.available();
        for (ShownWindow window : windowsOf.apply(document)) {
            StatusBar statusBar = window.view().statusBar();
            if (canOpen) {
                Runnable openFolder = () -> {
                    if (!folderOpener.open(file)) {
                        statusBar.showNotice(Messages.format("ai.export.cannotOpen", folder.toString()));
                    }
                };
                statusBar.showNotice(notice, Messages.text("ai.export.openFolder"), AiIcons.icon(AiIcons.FOLDER),
                        openFolder);
            } else {
                statusBar.showNotice(notice);
            }
        }
    }

    private static AutomationException nothing() {
        return new AutomationException(AutomationError.INVALID_REFERENCE, "There is nothing to export there (an "
                + "empty circuit or selection)", Map.of());
    }
}
