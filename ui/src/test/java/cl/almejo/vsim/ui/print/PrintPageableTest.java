package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.TextFont;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterAbortException;
import java.awt.print.PrinterException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static cl.almejo.vsim.ui.UiTestSupport.frame;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * The sheets as a printer asks for them: one format per page, banding, cancelling. That the paper shows the PDF's
 * sheet under Java's real transforms (turned, mirrored) is {@link PrintedSheetMatchesThePdfTest}'s.
 */
class PrintPageableTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T15:30:00Z"), ZoneOffset.UTC);
    private static final PrintSetup A4_PORTRAIT = new PrintSetup(Paper.A4, PageOrientation.PORTRAIT,
            PageGeometry.CENTIMETRE_POINTS);
    /** What a 600 dpi printer's graphics adds under the sheet's points (about 8 px/pt), rounded down to 4. */
    private static final double DEVICE_SCALE = 4;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private final RecordingProgress progress = new RecordingProgress();
    private int jobCancels;

    /** Notes each reported page and answers the cancel question the test sets. */
    private static final class RecordingProgress implements PdfExporter.ProgressListener {
        final List<Integer> done = new ArrayList<>();
        final List<Integer> totals = new ArrayList<>();
        boolean cancelled;

        @Override
        public void pageDone(int pagesDone, int total) {
            done.add(pagesDone);
            totals.add(total);
        }

        @Override
        public boolean cancelled() {
            return cancelled;
        }
    }

    private void add(String typeId, int x, int y) {
        document.add(registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y),
                Rotation.R0, Map.of(), ""));
    }

    /** An AND gate at the top-left and a wire far enough to need 2 × 2 A4 portrait sheets at 100 %. */
    private void bigCircuit() {
        add(StandardComponentTypes.AND, 4, 4);
        wire(document, 95, 125, 100, 125);
    }

    private PrintInput input() {
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), 0, true, Map.of(), Map.of(),
                List.of(), Optional.empty());
        return PrintInput.capture(new PrintInput.LiveCircuit(document, registry, netlist, document.revision(),
                snapshot, "Sumador", Optional.of(Path.of("sumador.vsim"))), Set.of(), ColorScheme.LIGHT,
                TextFont.DEFAULT, CLOCK);
    }

    private static PrintOptions options(PrintSetup setup, ScaleMode scaleMode) {
        return new PrintOptions(new PrintScope.WholeCircuit(), setup.paper(), setup.orientation(), scaleMode, true,
                false, true, "Sumador");
    }

    private static PagePainter painter(PrintInput input, PrintSetup setup, ScaleMode scaleMode) {
        PrintOptions options = options(setup, scaleMode);
        PrintArea area = PrintScopes.area(input, options.scope()).orElseThrow();
        PageGeometry geometry = PageGeometry.standard(setup.paper(), setup.orientation(), options.headerFooter(),
                setup.marginPoints());
        return new PagePainter(input, area, options, PageLayout.of(area.gridArea(), geometry, scaleMode));
    }

    private PrintPageable pageable(PagePainter painter, PrintSetup setup) {
        return new PrintPageable(painter, setup, progress, () -> jobCancels++);
    }

    /**
     * What a printer's graphics gets for {@code pageIndex}: its own scale and offset under the sheet's points, over a
     * red background the white sheet must cover.
     */
    private static BufferedImage printed(PrintPageable pageable, int pageIndex) throws PrinterException {
        PageFormat format = pageable.getPageFormat(pageIndex);
        BufferedImage image = deviceImage(format);
        Graphics2D graphics = deviceGraphics(image);
        try {
            assertThat(pageable.getPrintable(pageIndex).print(graphics, format, pageIndex))
                    .isEqualTo(Printable.PAGE_EXISTS);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static BufferedImage deviceImage(PageFormat format) {
        return new BufferedImage((int) Math.floor(format.getWidth() * DEVICE_SCALE / 2),
                (int) Math.floor(format.getHeight() * DEVICE_SCALE / 2), BufferedImage.TYPE_INT_RGB);
    }

    /** Half the image is the sheet's top-left quarter, moved by a printer-like offset: scale(4) + translate. */
    private static Graphics2D deviceGraphics(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.translate(-3, -5);
        graphics.scale(DEVICE_SCALE, DEVICE_SCALE);
        return graphics;
    }

    private static boolean samePixels(BufferedImage first, BufferedImage second) {
        if (first.getWidth() != second.getWidth() || first.getHeight() != second.getHeight()) {
            return false;
        }
        for (int x = 0; x < first.getWidth(); x++) {
            for (int y = 0; y < first.getHeight(); y++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Test
    void thePagesAreThePaintersSheets() {
        bigCircuit();
        PagePainter painter = painter(input(), A4_PORTRAIT, new ScaleMode.Percentage(100));
        PrintPageable pageable = pageable(painter, A4_PORTRAIT);

        assertThat(painter.pages()).hasSize(4);
        assertThat(pageable.getNumberOfPages()).isEqualTo(4);
        assertThat(pageable.getPrintable(3)).isSameAs(pageable);
        assertThatThrownBy(() -> pageable.getPageFormat(4)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void skippingEmptySheetsTheJobHasOnlyThePrintedSheetsAndTheProgressCountsThem() throws PrinterException {
        bigCircuit();
        PrintOptions base = options(A4_PORTRAIT, new ScaleMode.Percentage(100));
        PrintOptions skipping = new PrintOptions(base.scope(), base.paper(), base.orientation(), base.scaleMode(),
                base.withValues(), base.blackAndWhite(), base.headerFooter(), base.title(), false, true, true, true,
                true, true);
        PrintInput input = input();
        PrintArea area = PrintScopes.area(input, skipping.scope()).orElseThrow();
        PageGeometry geometry = PageGeometry.standard(Paper.A4, PageOrientation.PORTRAIT, true,
                PageGeometry.CENTIMETRE_POINTS);
        PagePainter painter = new PagePainter(input, area, skipping, PageLayout.of(area.gridArea(), geometry,
                skipping.scaleMode()));
        PrintPageable pageable = pageable(painter, A4_PORTRAIT);

        assertThat(pageable.getNumberOfPages()).isEqualTo(2);
        printed(pageable, 0);
        printed(pageable, 1);

        assertThat(progress.done).containsExactly(1, 2);
        assertThat(progress.totals).containsExactly(2, 2);
    }

    @Test
    void pastTheLastSheetThereIsNoSuchPage() throws PrinterException {
        bigCircuit();
        PrintPageable pageable = pageable(painter(input(), A4_PORTRAIT, new ScaleMode.Percentage(100)), A4_PORTRAIT);
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            assertThat(pageable.print(graphics, pageable.getPageFormat(0), 4)).isEqualTo(Printable.NO_SUCH_PAGE);
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void everySheetHasThePaperSizeAndTheMarginAsItsImageableArea() {
        add(StandardComponentTypes.AND, 4, 4);
        PrintSetup wide = new PrintSetup(Paper.LETTER, PageOrientation.PORTRAIT, 40);
        PageFormat format = pageable(painter(input(), wide, new ScaleMode.FitToOnePage()), wide).getPageFormat(0);

        assertThat(format.getOrientation()).isEqualTo(PageFormat.PORTRAIT);
        assertThat(format.getWidth()).isCloseTo(612, within(1e-9));
        assertThat(format.getHeight()).isCloseTo(792, within(1e-9));
        assertThat(format.getImageableX()).isCloseTo(40, within(1e-9));
        assertThat(format.getImageableY()).isCloseTo(40, within(1e-9));
        assertThat(format.getImageableWidth()).isCloseTo(612 - 80, within(1e-9));
        assertThat(format.getImageableHeight()).isCloseTo(792 - 80, within(1e-9));
    }

    @Test
    void aLandscapeSheetIsTurnedByTheFormat() {
        add(StandardComponentTypes.AND, 4, 4);
        PrintSetup landscape = new PrintSetup(Paper.A4, PageOrientation.LANDSCAPE, PageGeometry.CENTIMETRE_POINTS);
        PageFormat format = pageable(painter(input(), landscape, new ScaleMode.FitToOnePage()), landscape)
                .getPageFormat(0);

        assertThat(format.getOrientation()).isEqualTo(PageFormat.LANDSCAPE);
        assertThat(format.getWidth()).isCloseTo(Paper.A4.heightPoints(), within(1e-9));
        assertThat(format.getHeight()).isCloseTo(Paper.A4.widthPoints(), within(1e-9));
        assertThat(format.getPaper().getWidth()).as("the paper itself stays portrait")
                .isCloseTo(Paper.A4.widthPoints(), within(1e-9));
    }

    @Test
    void theSameSheetPaintedTwiceGivesTheSameImageAndCountsOnce() throws PrinterException {
        bigCircuit();
        PrintPageable pageable = pageable(painter(input(), A4_PORTRAIT, new ScaleMode.Percentage(100)), A4_PORTRAIT);

        BufferedImage first = printed(pageable, 1);
        BufferedImage second = printed(pageable, 1);
        printed(pageable, 0);

        assertThat(samePixels(first, second)).isTrue();
        assertThat(progress.done).containsExactly(2);
    }

    @Test
    void progressCountsTheHighestSheetAsked() throws PrinterException {
        bigCircuit();
        PrintPageable pageable = pageable(painter(input(), A4_PORTRAIT, new ScaleMode.Percentage(100)), A4_PORTRAIT);

        for (int pageIndex = 0; pageIndex < 4; pageIndex++) {
            printed(pageable, pageIndex);
            printed(pageable, pageIndex);
        }

        assertThat(progress.done).containsExactly(1, 2, 3, 4);
    }

    @Test
    void aFramesTitleIsPrintedOnPaperToo() throws PrinterException {
        frame(document, 0, 0, 40, 20, "MMMMMMMMMMMMMMMM", FrameTint.BLUE);
        wire(document, 190, 0, 195, 0);
        PagePainter titled = painter(input(), A4_PORTRAIT, new ScaleMode.FitToOnePage());
        document.replace(document.frames().iterator().next().withTitle(""));
        PagePainter untitled = painter(input(), A4_PORTRAIT, new ScaleMode.FitToOnePage());

        BufferedImage withTitle = printed(pageable(titled, A4_PORTRAIT), 0);
        BufferedImage withoutTitle = printed(pageable(untitled, A4_PORTRAIT), 0);

        assertThat(samePixels(withTitle, withoutTitle)).isFalse();
    }

    @Test
    void withAPageRangeTheProgressCountsOnlyItsSheets() throws PrinterException {
        bigCircuit();
        PagePainter painter = painter(input(), A4_PORTRAIT, new ScaleMode.Percentage(100));
        PrintPageable pageable = new PrintPageable(painter, A4_PORTRAIT, progress, () -> jobCancels++,
                List.of(1, 2));

        printed(pageable, 1);
        printed(pageable, 1);
        printed(pageable, 2);

        assertThat(progress.done).as("«Páginas 2–3» ends at 2 of 2").containsExactly(1, 2);
        assertThat(progress.totals).containsOnly(2);
    }

    @Test
    void aCancelledPrintStopsAtTheNextSheetAndCancelsTheJob() throws PrinterException {
        bigCircuit();
        PrintPageable pageable = pageable(painter(input(), A4_PORTRAIT, new ScaleMode.Percentage(100)), A4_PORTRAIT);
        printed(pageable, 0);
        progress.cancelled = true;

        assertThatThrownBy(() -> printed(pageable, 1)).isInstanceOf(PrinterAbortException.class);
        assertThat(jobCancels).isEqualTo(1);
        assertThat(progress.done).containsExactly(1);
    }
}
