package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.TextFont;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.IntPredicate;
import java.util.stream.Stream;

import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Writing the PDF file: off the caller's thread, page by page, and never leaving a broken file behind. */
class PdfExportTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T15:30:00Z"), ZoneOffset.UTC);
    private static final double SIZE_TOLERANCE = 0.5;
    private static final byte[] EARLIER_FILE = "an earlier PDF".getBytes();

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private final ControlledExecutor executor = new ControlledExecutor();

    /** Runs the queued tasks only when the test says so. */
    private static final class ControlledExecutor implements Executor {
        private final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        void runAll() {
            while (!tasks.isEmpty()) {
                tasks.poll().run();
            }
        }
    }

    /** Records the progress and stops when {@code stopAfter} says so for the pages done so far. */
    private static final class RecordingProgress implements PdfExporter.ProgressListener {
        private final List<String> calls = new ArrayList<>();
        private final IntPredicate stopAfter;
        private int done;

        RecordingProgress(IntPredicate stopAfter) {
            this.stopAfter = stopAfter;
        }

        @Override
        public void pageDone(int pagesDone, int total) {
            done = pagesDone;
            calls.add(pagesDone + "/" + total);
        }

        @Override
        public boolean cancelled() {
            return stopAfter.test(done);
        }
    }

    private static RecordingProgress never() {
        return new RecordingProgress(done -> false);
    }

    // --- the circuit, the input and the painter ----------------------------------------------------------------

    private Component add(String typeId, int x, int y) {
        Component component = registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y),
                Rotation.R0, Map.of(), "");
        document.add(component);
        return component;
    }

    private void smallCircuit() {
        add(StandardComponentTypes.AND2, 4, 4);
    }

    /** An AND gate at the top-left and a wire far enough to need 3 columns × 2 rows of A4 portrait at 100 %. */
    private void wideCircuit() {
        add(StandardComponentTypes.AND2, 4, 4);
        wire(document, 135, 125, 140, 125);
    }

    private PrintInput input() {
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), 0, true, Map.of(), Map.of(),
                List.of(), Optional.empty());
        return PrintInput.capture(new PrintInput.LiveCircuit(document, registry, netlist, document.revision(),
                snapshot, "Sumador", Optional.empty()), Set.of(), ColorScheme.LIGHT, TextFont.DEFAULT, CLOCK);
    }

    private static PrintOptions options(Paper paper, PageOrientation orientation, ScaleMode scaleMode) {
        return new PrintOptions(new PrintScope.WholeCircuit(), paper, orientation, scaleMode, true, false, false,
                "Sumador");
    }

    private PagePainter painter(PrintOptions options) {
        PrintInput input = input();
        PrintArea area = PrintScopes.area(input, options.scope()).orElseThrow();
        PageGeometry geometry = PageGeometry.standard(options.paper(), options.orientation(), options.headerFooter());
        return new PagePainter(input, area, options, PageLayout.of(area.gridArea(), geometry, options.scaleMode()));
    }

    private PagePainter fitOnA4() {
        return painter(options(Paper.A4, PageOrientation.PORTRAIT, new ScaleMode.FitToOnePage()));
    }

    private PagePainter wideAtFullSize() {
        wideCircuit();
        return painter(options(Paper.A4, PageOrientation.PORTRAIT, new ScaleMode.Percentage(100)));
    }

    private static List<Path> filesIn(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.toList();
        }
    }

    private static Throwable failureOf(CompletableFuture<Void> future) {
        assertThat(future).isCompletedExceptionally();
        try {
            future.get();
            throw new AssertionError("the future did not fail");
        } catch (ExecutionException failure) {
            return failure.getCause();
        } catch (CancellationException cancellation) {
            return cancellation;
        } catch (InterruptedException interrupted) {
            throw new AssertionError(interrupted);
        }
    }

    // --- the file ----------------------------------------------------------------------------------------------

    @Test
    void nothingIsWrittenUntilTheExecutorRuns(@TempDir Path folder) throws IOException {
        smallCircuit();
        Path target = folder.resolve("circuito.pdf");
        CompletableFuture<Void> future = new PdfExport(executor).export(fitOnA4(), target, never());
        assertThat(future).isNotDone();
        assertThat(filesIn(folder)).isEmpty();
        executor.runAll();
        assertThat(future).isCompleted();
        assertThat(filesIn(folder)).containsExactly(target);
    }

    @Test
    void oneA4PortraitSheetIsTheSizeOfA4(@TempDir Path folder) throws IOException {
        smallCircuit();
        Path target = folder.resolve("circuito.pdf");
        CompletableFuture<Void> future = new PdfExport(executor).export(fitOnA4(), target, never());
        executor.runAll();
        assertThat(future).isCompleted();
        assertThat(PdfReading.pageCount(target)).isEqualTo(1);
        PdfPages.PageSize size = PdfReading.pageSize(target, 0);
        assertThat(size.widthPoints()).isCloseTo(595.28, within(SIZE_TOLERANCE));
        assertThat(size.heightPoints()).isCloseTo(841.89, within(SIZE_TOLERANCE));
        assertThat(PdfReading.hasImages(target, 0)).isFalse();
        assertThat(PdfReading.pathOperatorCount(target, 0)).isPositive();
    }

    @Test
    void landscapeSheetsOfEveryOtherPaperHaveTheirSize(@TempDir Path folder) throws IOException {
        smallCircuit();
        Map<Paper, PdfPages.PageSize> expected = Map.of(
                Paper.LETTER, new PdfPages.PageSize(792, 612),
                Paper.OFICIO, new PdfPages.PageSize(935.43, 612.28),
                Paper.A3, new PdfPages.PageSize(1190.55, 841.89));
        for (Map.Entry<Paper, PdfPages.PageSize> entry : expected.entrySet()) {
            Path target = folder.resolve(entry.getKey().name() + ".pdf");
            new PdfExport(executor).export(painter(options(entry.getKey(), PageOrientation.LANDSCAPE,
                    new ScaleMode.FitToOnePage())), target, never());
            executor.runAll();
            PdfPages.PageSize size = PdfReading.pageSize(target, 0);
            assertThat(size.widthPoints()).as(entry.getKey().name()).isCloseTo(entry.getValue().widthPoints(),
                    within(SIZE_TOLERANCE));
            assertThat(size.heightPoints()).as(entry.getKey().name()).isCloseTo(entry.getValue().heightPoints(),
                    within(SIZE_TOLERANCE));
        }
    }

    @Test
    void aThreeByTwoLayoutWritesSixVectorPagesAndReportsEachOne(@TempDir Path folder) throws IOException {
        PagePainter painter = wideAtFullSize();
        Path target = folder.resolve("circuito.pdf");
        RecordingProgress progress = never();
        CompletableFuture<Void> future = new PdfExport(executor).export(painter, target, progress);
        executor.runAll();
        assertThat(future).isCompleted();
        assertThat(PdfReading.pageCount(target)).isEqualTo(6);
        for (int pageIndex = 0; pageIndex < 6; pageIndex++) {
            assertThat(PdfReading.hasImages(target, pageIndex)).isFalse();
            assertThat(PdfReading.pathOperatorCount(target, pageIndex)).isPositive();
        }
        assertThat(progress.calls).containsExactly("1/6", "2/6", "3/6", "4/6", "5/6", "6/6");
    }

    @Test
    void anExistingFileIsReplacedOnSuccess(@TempDir Path folder) throws IOException {
        smallCircuit();
        Path target = folder.resolve("circuito.pdf");
        Files.write(target, EARLIER_FILE);
        new PdfExport(executor).export(fitOnA4(), target, never());
        executor.runAll();
        assertThat(PdfReading.pageCount(target)).isEqualTo(1);
        assertThat(filesIn(folder)).containsExactly(target);
    }

    // --- cancelling and failing --------------------------------------------------------------------------------

    @Test
    void cancellingAfterTheFirstPageLeavesTheEarlierFileAndNoTemporaryFile(@TempDir Path folder) throws IOException {
        PagePainter painter = wideAtFullSize();
        Path target = folder.resolve("circuito.pdf");
        Files.write(target, EARLIER_FILE);
        RecordingProgress progress = new RecordingProgress(done -> done >= 1);
        CompletableFuture<Void> future = new PdfExport(executor).export(painter, target, progress);
        executor.runAll();
        assertThat(failureOf(future)).isInstanceOf(CancellationException.class);
        assertThat(future).isCancelled();
        assertThat(progress.calls).containsExactly("1/6");
        assertThat(Files.readAllBytes(target)).isEqualTo(EARLIER_FILE);
        assertThat(filesIn(folder)).containsExactly(target);
    }

    @Test
    void cancellingAfterTheLastPageStillSavesNothing(@TempDir Path folder) throws IOException {
        smallCircuit();
        Path target = folder.resolve("circuito.pdf");
        CompletableFuture<Void> future = new PdfExport(executor).export(fitOnA4(), target,
                new RecordingProgress(done -> done >= 1));
        executor.runAll();
        assertThat(failureOf(future)).isInstanceOf(CancellationException.class);
        assertThat(filesIn(folder)).isEmpty();
    }

    @Test
    void anExceptionMidWriteFailsWithItsCauseAndLeavesTheEarlierFile(@TempDir Path folder) throws IOException {
        PagePainter painter = wideAtFullSize();
        Path target = folder.resolve("circuito.pdf");
        Files.write(target, EARLIER_FILE);
        IllegalStateException broken = new IllegalStateException("broken");
        PdfExporter.ProgressListener failing = new PdfExporter.ProgressListener() {
            @Override
            public void pageDone(int done, int total) {
                if (done == 2) {
                    throw broken;
                }
            }

            @Override
            public boolean cancelled() {
                return false;
            }
        };
        CompletableFuture<Void> future = new PdfExport(executor).export(painter, target, failing);
        executor.runAll();
        assertThat(failureOf(future)).isSameAs(broken);
        assertThat(Files.readAllBytes(target)).isEqualTo(EARLIER_FILE);
        assertThat(filesIn(folder)).containsExactly(target);
    }

    @Test
    void aFailedMoveDeletesTheTemporaryFileAndLeavesTheEarlierFile(@TempDir Path folder) throws IOException {
        smallCircuit();
        Path target = folder.resolve("circuito.pdf");
        Files.write(target, EARLIER_FILE);
        IOException diskFull = new IOException("disk full");
        List<Path> movedFrom = new ArrayList<>();
        PdfExport.FileMover failing = (source, destination, copyOptions) -> {
            movedFrom.add(source);
            throw diskFull;
        };
        CompletableFuture<Void> future = new PdfExport(executor, failing).export(fitOnA4(), target, never());
        executor.runAll();
        assertThat(failureOf(future)).isSameAs(diskFull);
        assertThat(movedFrom).singleElement().satisfies(source -> {
            assertThat(source.getParent()).isEqualTo(folder.toAbsolutePath());
            assertThat(source.getFileName().toString()).startsWith("circuito.pdf.tmp-");
        });
        assertThat(Files.readAllBytes(target)).isEqualTo(EARLIER_FILE);
        assertThat(filesIn(folder)).containsExactly(target);
    }

    @Test
    void withoutAtomicMovesTheFileIsStillMovedIntoPlace(@TempDir Path folder) throws IOException {
        smallCircuit();
        Path target = folder.resolve("circuito.pdf");
        Files.write(target, EARLIER_FILE);
        List<List<CopyOption>> attempts = new ArrayList<>();
        PdfExport.FileMover noAtomicMoves = (source, destination, copyOptions) -> {
            attempts.add(List.of(copyOptions));
            if (List.of(copyOptions).contains(StandardCopyOption.ATOMIC_MOVE)) {
                throw new AtomicMoveNotSupportedException(source.toString(), destination.toString(), "test");
            }
            Files.move(source, destination, copyOptions);
        };
        CompletableFuture<Void> future = new PdfExport(executor, noAtomicMoves).export(fitOnA4(), target, never());
        executor.runAll();
        assertThat(future).isCompleted();
        assertThat(attempts).containsExactly(
                List.of(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING),
                List.of(StandardCopyOption.REPLACE_EXISTING));
        assertThat(PdfReading.pageCount(target)).isEqualTo(1);
        assertThat(filesIn(folder)).containsExactly(target);
    }

    @Test
    void aRejectingExecutorFailsTheFuture(@TempDir Path folder) {
        smallCircuit();
        IllegalStateException rejected = new IllegalStateException("shut down");
        CompletableFuture<Void> future = new PdfExport(task -> {
            throw rejected;
        }).export(fitOnA4(), folder.resolve("circuito.pdf"), never());
        assertThat(failureOf(future)).isSameAs(rejected);
    }

    @Test
    void moreSheetsThanTheLimitAreRefusedWithoutPaintingOrWriting(@TempDir Path folder) throws IOException {
        add(StandardComponentTypes.AND2, 4, 4);
        wire(document, 1000, 1000, 1005, 1000);
        PagePainter painter = painter(options(Paper.A4, PageOrientation.PORTRAIT, new ScaleMode.Percentage(400)));
        assertThat(painter.pages().size()).isGreaterThan(PdfExport.REFUSE_ABOVE_PAGES);
        RecordingProgress progress = never();
        CompletableFuture<Void> future = new PdfExport(executor).export(painter, folder.resolve("circuito.pdf"),
                progress);
        executor.runAll();
        assertThat(failureOf(future)).isInstanceOf(IllegalArgumentException.class);
        assertThat(progress.calls).isEmpty();
        assertThat(filesIn(folder)).isEmpty();
    }

    @Test
    void theLimitsAreTheDialogs() {
        assertThat(PdfExport.CONFIRM_ABOVE_PAGES).isEqualTo(50);
        assertThat(PdfExport.REFUSE_ABOVE_PAGES).isEqualTo(1000);
    }

    @Test
    void theArgumentsAreRequired(@TempDir Path folder) {
        smallCircuit();
        PdfExport export = new PdfExport(executor);
        assertThatThrownBy(() -> export.export(null, folder.resolve("circuito.pdf"), never()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> export.export(fitOnA4(), null, never())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> export.export(fitOnA4(), folder.resolve("circuito.pdf"), null))
                .isInstanceOf(NullPointerException.class);
    }
}
