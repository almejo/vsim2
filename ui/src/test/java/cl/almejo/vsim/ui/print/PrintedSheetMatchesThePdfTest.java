package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.terminal.TerminalScreen;
import cl.almejo.vsim.core.terminal.TerminalSettings;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.TextFont;
import org.jfree.pdf.PDFDocument;
import org.jfree.pdf.PDFGraphics2D;
import org.jfree.pdf.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.PrinterException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The paper shows what the PDF shows, whatever transform Java's printer graphics carries (final review of «Imprimir»,
 * C1/I1). Java hands {@link PrintPageable#print} a graphics whose transform is {@code [band flip] · scale(dpi/72) ·
 * PageFormat.getMatrix()}: a quarter turn on a landscape sheet, a vertical mirror on a page painted in raster bands
 * (any translucency). Here each case is painted through {@link PrintPageable} under that real transform and compared
 * with the sheet painted the PDF's way (upright, only scaled), the reference's pixels carried through the same turn
 * and mirror. Texts turned in their cells, a missing Terminal title or an empty sticky note change whole regions.
 */
class PrintedSheetMatchesThePdfTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T15:30:00Z"), ZoneOffset.UTC);
    /** Letter is a whole number of points both ways, so the sheet maps onto whole device pixels at any whole scale. */
    private static final Paper PAPER = Paper.LETTER;
    /** Device pixels per point: a 144 dpi printer. */
    private static final double DEVICE_SCALE = 2;
    /**
     * How far apart two pixels' channels may be and still count as the same: anti-aliased edges are not bit-exact
     * under a quarter turn or a mirror, a turned glyph or a missing text is black on white.
     */
    private static final int CHANNEL_TOLERANCE = 96;
    /** Inked pixels allowed to differ beyond the channel tolerance, per thousand: only edge noise. */
    private static final double DIFFERENT_PER_THOUSAND = 0.5;
    private static final int WHITE = 0xFFFFFF;
    private static final Map<String, Object> TERMINAL_PARAMETERS = Map.of(TerminalTypes.ROWS, 4,
            TerminalTypes.COLUMNS, 20);

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private final PdfExporter.ProgressListener noProgress = new PdfExporter.ProgressListener() {
        @Override
        public void pageDone(int pagesDone, int total) {
        }

        @Override
        public boolean cancelled() {
            return false;
        }
    };

    /** How Java's printing graphics reaches the device: a vector pass, or a raster band stored bottom-up. */
    private enum Pass { VECTOR, BAND }

    /**
     * A Terminal with text on its screen, a free label, an AND gate and an adder (pin names) turned a
     * quarter with labels, and a sticky note:
     * every kind of text whose drawing looks at the graphics' transform.
     */
    private PrintInput input() {
        Component terminal = registry.require(StandardComponentTypes.TERMINAL).newComponent(
                document.newComponentId(), new GridPoint(2, 4), Rotation.R0, TERMINAL_PARAMETERS, "consola");
        document.add(terminal);
        document.add(registry.require(StandardComponentTypes.AND).newComponent(document.newComponentId(),
                new GridPoint(50, 6), Rotation.R90, Map.of(), "compuerta"));
        document.add(registry.require(StandardComponentTypes.ADDER).newComponent(document.newComponentId(),
                new GridPoint(36, 20), Rotation.R90, Map.of(), "sumador"));
        document.add(new TextLabel(document.newLabelId(), new GridPoint(4, 34), "Una etiqueta libre"));
        document.add(new StickyNote(document.newStickyNoteId(), new GridPoint(50, 24), 12, 8, StickyColor.YELLOW,
                new StickyText("Revisar el reloj"), false));
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), 0, true, Map.of(),
                Map.of(terminal.id().value(), terminalText("hola mundo")), List.of(), Optional.empty());
        return PrintInput.capture(new PrintInput.LiveCircuit(document, registry, netlist, document.revision(),
                snapshot, "Consola", Optional.of(Path.of("consola.vsim"))), Set.of(), ColorScheme.LIGHT,
                TextFont.DEFAULT, CLOCK);
    }

    private static VisibleState.TerminalState terminalText(String text) {
        TerminalScreen screen = new TerminalScreen(TerminalSettings.vt(4, 20));
        for (byte value : text.getBytes(StandardCharsets.UTF_8)) {
            screen.write(value & 0xFF, 0);
        }
        return new VisibleState.TerminalState(1, screen.version(), screen.frame(), false);
    }

    private static PagePainter painter(PrintInput input, PageOrientation orientation) {
        PrintSetup setup = new PrintSetup(PAPER, orientation, PageGeometry.CENTIMETRE_POINTS);
        PrintOptions options = new PrintOptions(new PrintScope.WholeCircuit(), PAPER, orientation,
                new ScaleMode.Percentage(100), true, false, true, "Consola", true);
        PrintArea area = PrintScopes.area(input, options.scope(), true).orElseThrow();
        PageGeometry geometry = PageGeometry.standard(PAPER, orientation, true, setup.marginPoints());
        PagePainter painter = new PagePainter(input, area, options,
                PageLayout.of(area.gridArea(), geometry, options.scaleMode()));
        assertThat(painter.pages()).as("everything on one sheet").hasSize(1);
        return painter;
    }

    /** Java's transform for {@code format}'s sheet: points from the sheet's corner to the device's pixels. */
    private static AffineTransform javaTransform(PageFormat format, Pass pass) {
        AffineTransform transform = new AffineTransform();
        if (pass == Pass.BAND) {
            transform.translate(0, format.getPaper().getHeight() * DEVICE_SCALE);
            transform.scale(1, -1);
        }
        transform.scale(DEVICE_SCALE, DEVICE_SCALE);
        transform.concatenate(new AffineTransform(format.getMatrix()));
        return transform;
    }

    /** The sheet as the printer gets it: the portrait paper's pixels, painted through {@link PrintPageable}. */
    private BufferedImage printed(PagePainter painter, PageOrientation orientation, Pass pass)
            throws PrinterException {
        PrintPageable pageable = new PrintPageable(painter,
                new PrintSetup(PAPER, orientation, PageGeometry.CENTIMETRE_POINTS), noProgress, () -> { });
        PageFormat format = pageable.getPageFormat(0);
        BufferedImage image = redImage(PAPER.widthPoints(), PAPER.heightPoints());
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.transform(javaTransform(format, pass));
            pageable.print(graphics, format, 0);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /** The sheet painted the PDF's way, upright and only scaled, then carried to the device like Java's. */
    private static BufferedImage expected(PagePainter painter, PageFormat format, Pass pass) {
        BufferedImage upright = redImage(format.getWidth(), format.getHeight());
        Graphics2D graphics = upright.createGraphics();
        try {
            graphics.scale(DEVICE_SCALE, DEVICE_SCALE);
            // only to measure text as JFreePDF does: over a plain scale there is no turn or mirror to hide
            painter.paint(SheetGraphics.over(graphics), 0);
        } finally {
            graphics.dispose();
        }
        return carried(upright, javaTransform(format, pass));
    }

    /** {@code upright}'s pixels where {@code sheetToDevice} puts them on the portrait paper; lossless. */
    private static BufferedImage carried(BufferedImage upright, AffineTransform sheetToDevice) {
        BufferedImage device = redImage(PAPER.widthPoints(), PAPER.heightPoints());
        AffineTransform deviceToSheet;
        try {
            deviceToSheet = sheetToDevice.createInverse();
        } catch (NoninvertibleTransformException exception) {
            throw new IllegalStateException(exception);
        }
        for (int deviceX = 0; deviceX < device.getWidth(); deviceX++) {
            for (int deviceY = 0; deviceY < device.getHeight(); deviceY++) {
                Point2D sheet = deviceToSheet.transform(new Point2D.Double(deviceX + 0.5, deviceY + 0.5), null);
                int uprightX = (int) Math.floor(sheet.getX() * DEVICE_SCALE);
                int uprightY = (int) Math.floor(sheet.getY() * DEVICE_SCALE);
                if (uprightX >= 0 && uprightX < upright.getWidth() && uprightY >= 0
                        && uprightY < upright.getHeight()) {
                    device.setRGB(deviceX, deviceY, upright.getRGB(uprightX, uprightY));
                }
            }
        }
        return device;
    }

    private static BufferedImage redImage(double widthPoints, double heightPoints) {
        BufferedImage image = new BufferedImage((int) Math.round(widthPoints * DEVICE_SCALE),
                (int) Math.round(heightPoints * DEVICE_SCALE), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        return image;
    }

    /**
     * Of the pixels with ink (not white) in either image, how many per thousand have channels that differ by more than
     * {@link #CHANNEL_TOLERANCE}: counting against the ink, not the whole sheet, keeps a turned Terminal row visible.
     */
    private static double differentPerThousandInked(BufferedImage first, BufferedImage second) {
        assertThat(first.getWidth()).isEqualTo(second.getWidth());
        assertThat(first.getHeight()).isEqualTo(second.getHeight());
        long inked = 0;
        long different = 0;
        for (int x = 0; x < first.getWidth(); x++) {
            for (int y = 0; y < first.getHeight(); y++) {
                int firstRgb = first.getRGB(x, y);
                int secondRgb = second.getRGB(x, y);
                if (farApart(firstRgb, WHITE) || farApart(secondRgb, WHITE)) {
                    inked++;
                    if (farApart(firstRgb, secondRgb)) {
                        different++;
                    }
                }
            }
        }
        assertThat(inked).as("the sheet has something painted on it").isPositive();
        return 1000.0 * different / inked;
    }

    private static boolean farApart(int firstRgb, int secondRgb) {
        for (int shift = 0; shift <= 16; shift += 8) {
            if (Math.abs(((firstRgb >> shift) & 0xFF) - ((secondRgb >> shift) & 0xFF)) > CHANNEL_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    private void assertPrintedLikeThePdf(PageOrientation orientation, Pass pass) throws PrinterException {
        PagePainter painter = painter(input(), orientation);
        PageFormat format = PrintPageable.pageFormat(new PrintSetup(PAPER, orientation,
                PageGeometry.CENTIMETRE_POINTS));

        double different = differentPerThousandInked(printed(painter, orientation, pass),
                expected(painter, format, pass));

        assertThat(different).as("inked pixels per thousand that differ from the PDF's sheet, %s %s", orientation,
                pass).isLessThanOrEqualTo(DIFFERENT_PER_THOUSAND);
    }

    @Test
    void aPortraitVectorPassIsThePdfsSheet() throws PrinterException {
        assertPrintedLikeThePdf(PageOrientation.PORTRAIT, Pass.VECTOR);
    }

    @Test
    void aPortraitRasterBandIsThePdfsSheetMirrored() throws PrinterException {
        assertPrintedLikeThePdf(PageOrientation.PORTRAIT, Pass.BAND);
    }

    @Test
    void aLandscapeVectorPassIsThePdfsSheetTurned() throws PrinterException {
        assertPrintedLikeThePdf(PageOrientation.LANDSCAPE, Pass.VECTOR);
    }

    @Test
    void aLandscapeRasterBandIsThePdfsSheetTurnedAndMirrored() throws PrinterException {
        assertPrintedLikeThePdf(PageOrientation.LANDSCAPE, Pass.BAND);
    }

    /**
     * A printer would set plain text in its own Helvetica or Courier (final review, I3): every text reaches it as
     * shapes in VSim's font, like the PDF's. A JFreePDF page stands in for the printer, since it too writes text it is
     * given as text.
     */
    @Test
    void textReachesThePrinterAsShapes(@TempDir Path folder) throws PrinterException, IOException {
        PagePainter painter = painter(input(), PageOrientation.PORTRAIT);
        PrintPageable pageable = new PrintPageable(painter,
                new PrintSetup(PAPER, PageOrientation.PORTRAIT, PageGeometry.CENTIMETRE_POINTS), noProgress, () -> { });
        PageFormat format = pageable.getPageFormat(0);
        PDFDocument pdf = new PDFDocument();
        Page page = pdf.createPage(new Rectangle2D.Double(0, 0, format.getWidth(), format.getHeight()));
        PDFGraphics2D printer = page.getGraphics2D();
        pageable.print(printer, format, 0);
        Path file = folder.resolve("printer.pdf");
        Files.write(file, pdf.getPDFBytes());

        assertThat(PdfReading.operators(file, 0)).doesNotContain("BT", "Tj", "TJ");
        assertThat(PdfReading.text(file, 0)).isEmpty();
        assertThat(PdfReading.pathOperatorCount(file, 0)).isPositive();
    }
}
