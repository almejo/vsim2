package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.TextFont;
import org.junit.jupiter.api.Test;

import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.PageRanges;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Printable;
import java.awt.print.PrinterAbortException;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sending the sheets to a {@link PrinterJob}: a scripted one, so no printer is ever reached. */
class SystemPrinterAccessPrintTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T15:30:00Z"), ZoneOffset.UTC);
    private static final PrintSetup A4_PORTRAIT = new PrintSetup(Paper.A4, PageOrientation.PORTRAIT,
            PageGeometry.CENTIMETRE_POINTS);

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private final List<Runnable> queued = new ArrayList<>();
    /** The print thread: the test runs what was queued when it wants. */
    private final SystemPrinterAccess access = new SystemPrinterAccess(queued::add, Runnable::run);
    private final ScriptedPrinterJob printerJob = new ScriptedPrinterJob();
    private final PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
    private boolean cancelRequested;
    private boolean cancelOnSecondSheet;
    private final List<Integer> done = new ArrayList<>();

    /**
     * A printer job that asks for every page twice (banding), the way {@code RasterPrinterJob} does, and can fail or
     * abort on a chosen page.
     */
    private static final class ScriptedPrinterJob extends PrinterJob {
        Pageable pageable;
        PrintRequestAttributeSet printedWith;
        int asked;
        int cancels;
        Optional<PrinterException> failure = Optional.empty();

        @Override
        public void setPrintable(Printable painter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setPrintable(Printable painter, PageFormat format) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setPageable(Pageable document) {
            this.pageable = document;
        }

        @Override
        public boolean printDialog() {
            throw new AssertionError("no dialog in tests");
        }

        @Override
        public PageFormat pageDialog(PageFormat page) {
            throw new AssertionError("no dialog in tests");
        }

        @Override
        public PageFormat defaultPage(PageFormat page) {
            return page;
        }

        @Override
        public PageFormat validatePage(PageFormat page) {
            return page;
        }

        @Override
        public void print() throws PrinterException {
            print(new HashPrintRequestAttributeSet());
        }

        @Override
        public void print(PrintRequestAttributeSet attributes) throws PrinterException {
            printedWith = attributes;
            if (failure.isPresent()) {
                throw failure.get();
            }
            for (int pageIndex = 0; pageIndex < pageable.getNumberOfPages(); pageIndex++) {
                for (int band = 0; band < 2; band++) {
                    BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        asked++;
                        pageable.getPrintable(pageIndex).print(graphics, pageable.getPageFormat(pageIndex),
                                pageIndex);
                    } finally {
                        graphics.dispose();
                    }
                }
            }
        }

        @Override
        public void setCopies(int copies) {
        }

        @Override
        public int getCopies() {
            return 1;
        }

        @Override
        public String getUserName() {
            return "test";
        }

        @Override
        public void setJobName(String jobName) {
        }

        @Override
        public String getJobName() {
            return "test";
        }

        @Override
        public void cancel() {
            cancels++;
        }

        @Override
        public boolean isCancelled() {
            return cancels > 0;
        }
    }

    private PrintInput input() {
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), 0, true, Map.of(), Map.of(),
                List.of(), Optional.empty());
        return PrintInput.capture(new PrintInput.LiveCircuit(document, registry, netlist, document.revision(),
                snapshot, "Sumador", Optional.of(Path.of("sumador.vsim"))), Set.of(), ColorScheme.LIGHT,
                TextFont.DEFAULT, CLOCK);
    }

    /** Four A4 portrait sheets at 100 %. */
    private PagePainter fourSheets() {
        document.add(registry.require(StandardComponentTypes.AND).newComponent(document.newComponentId(),
                new GridPoint(4, 4), Rotation.R0, Map.of(), ""));
        wire(document, 95, 125, 100, 125);
        PrintOptions options = new PrintOptions(new PrintScope.WholeCircuit(), Paper.A4, PageOrientation.PORTRAIT,
                new ScaleMode.Percentage(100), true, false, false, "");
        PrintInput input = input();
        PrintArea area = PrintScopes.area(input, options.scope()).orElseThrow();
        return new PagePainter(input, area, options, PageLayout.of(area.gridArea(),
                PageGeometry.standard(Paper.A4, PageOrientation.PORTRAIT, false), options.scaleMode()));
    }

    private PdfExporter.ProgressListener progress() {
        return new PdfExporter.ProgressListener() {
            @Override
            public void pageDone(int pagesDone, int total) {
                done.add(pagesDone);
                if (pagesDone == 2) {
                    // «Cancelar» pressed while the second sheet prints, when the test asks for it
                    cancelRequested = cancelRequested || cancelOnSecondSheet;
                }
            }

            @Override
            public boolean cancelled() {
                return cancelRequested;
            }
        };
    }

    private CompletableFuture<Void> print(PagePainter pages) {
        CompletableFuture<Void> result = access.print(new SystemPrinterAccess.SystemJob(printerJob, attributes),
                pages, A4_PORTRAIT, progress());
        assertThat(result).as("nothing printed on the calling thread").isNotDone();
        queued.forEach(Runnable::run);
        return result;
    }

    @Test
    void everySheetGoesToTheJobWithTheDialogsAttributes() throws Exception {
        attributes.add(new PageRanges(2, 3));
        CompletableFuture<Void> result = print(fourSheets());

        result.get();
        assertThat(printerJob.pageable.getNumberOfPages()).isEqualTo(4);
        assertThat(printerJob.printedWith.get(PageRanges.class)).as("the page range goes through untouched")
                .isEqualTo(new PageRanges(2, 3));
        assertThat(printerJob.asked).isEqualTo(8);
        assertThat(done).containsExactly(1, 2, 3, 4);
    }

    @Test
    void cancellingStopsTheJobAndEndsCancelled() {
        cancelOnSecondSheet = true;
        CompletableFuture<Void> result = print(fourSheets());

        assertThatThrownBy(result::get).isInstanceOf(CancellationException.class);
        assertThat(printerJob.cancels).isEqualTo(1);
        assertThat(done).containsExactly(1, 2);
    }

    @Test
    void aJobThePrinterAbortedEndsCancelled() {
        printerJob.failure = Optional.of(new PrinterAbortException());
        CompletableFuture<Void> result = print(fourSheets());

        assertThatThrownBy(result::get).isInstanceOf(CancellationException.class);
    }

    @Test
    void aPrinterThatFailsEndsWithItsFailure() {
        PrinterException gone = new PrinterException("the printer is gone");
        printerJob.failure = Optional.of(gone);
        CompletableFuture<Void> result = print(fourSheets());

        assertThatThrownBy(result::get).isInstanceOf(ExecutionException.class).hasCause(gone);
    }

    @Test
    void aJobFromAnotherAccessIsRefused() {
        CompletableFuture<Void> result = access.print(new PrinterAccess.Job() { }, fourSheets(), A4_PORTRAIT,
                progress());

        assertThat(result).isCompletedExceptionally();
    }

    @Test
    void theLookupRunsOnItsOwnThread() {
        List<Runnable> lookups = new ArrayList<>();
        SystemPrinterAccess lookedUp = new SystemPrinterAccess(Runnable::run, lookups::add);

        CompletableFuture<Boolean> answer = lookedUp.hasPrinters();

        assertThat(answer).isNotDone();
        assertThat(lookups).hasSize(1);
    }
}
