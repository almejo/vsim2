package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.component.VisibleState.MemoryAccess;
import cl.almejo.vsim.core.component.standard.MemoryAccessLog.MemoryEvent;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.simulation.MemoryMapFrame;
import cl.almejo.vsim.core.simulation.MemoryMapRequest;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.core.command.RemoveElementsCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

/** The «Mapa de memoria» tab (spec §6), fed with hand-made frames and a fake wall clock. */
class MemoryMapPanelTest {

    private static final long SECOND = 1_000_000_000L;

    private final OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final AtomicLong clock = new AtomicLong(100 * SECOND);
    private final RecordingActions actions = new RecordingActions();
    private final MemoryMapPanel panel = new MemoryMapPanel(document, actions, () -> ColorScheme.LIGHT, clock::get, () -> true);

    @AfterEach
    void close() {
        panel.dispose();
        document.close();
    }

    private Component ram(String words) {
        return place(document, StandardComponentTypes.RAM, 0, 0, Map.of(MemoryTypes.WORDS, words));
    }

    /** A frame for the panel's current request: reads and writes per cell as given (others 0), all values 0. */
    private MemoryMapFrame frame(Map<Integer, Long> reads, Map<Integer, Long> writes, long time) {
        MemoryMapRequest request = panel.request().orElseThrow();
        long[] readCounts = new long[request.cells()];
        long[] writeCounts = new long[request.cells()];
        reads.forEach((cell, count) -> readCounts[cell] = count);
        writes.forEach((cell, count) -> writeCounts[cell] = count);
        return new MemoryMapFrame(request, readCounts, writeCounts, new long[request.cells()], List.of(), time);
    }

    @Test
    void withoutAMemorySelectedTheTabSaysWhatToDo() {
        assertThat(panel.showsEmptyState()).isTrue();
        assertThat(panel.emptyText()).isEqualTo("Selecciona una RAM o una ROM para ver su mapa.");
        assertThat(panel.request()).isEmpty();

        Component not = place(document, StandardComponentTypes.NOT, 20, 0, Map.of());
        panel.follow(Set.of(not.id()));
        assertThat(panel.showsEmptyState()).isTrue();
    }

    @Test
    void selectingAMemoryAsksForItsWholeMapAndNamesIt() {
        Component ram = ram("1024");
        panel.follow(Set.of(ram.id()));

        assertThat(panel.showsEmptyState()).isFalse();
        assertThat(panel.request()).contains(new MemoryMapRequest(ram.id().value(), 0, 1, 1024));
        assertThat(panel.title()).isEqualTo("Mapa de memoria · RAM1 (RAM 1K × 8)");
    }

    @Test
    void switchingTheSelectionToAnotherMemoryChangesTheRequestAndOtherSelectionsKeepTheLastOne() {
        Component ram = ram("256");
        Component rom = place(document, StandardComponentTypes.ROM, 40, 0, Map.of(MemoryTypes.WORDS, "16"));
        Component not = place(document, StandardComponentTypes.NOT, 80, 0, Map.of());
        panel.follow(Set.of(ram.id()));

        panel.follow(Set.of(rom.id()));
        assertThat(panel.request()).contains(new MemoryMapRequest(rom.id().value(), 0, 1, 16));
        assertThat(panel.title()).isEqualTo("Mapa de memoria · ROM1 (ROM 16 × 8)");

        panel.follow(Set.of(not.id()));
        panel.follow(Set.of());
        assertThat(panel.followed()).contains(rom.id());
    }

    @Test
    void aDeletedMemoryLeavesTheEmptyState() {
        Component ram = ram("256");
        panel.follow(Set.of(ram.id()));

        document.execute(new RemoveElementsCommand(List.of(ram.id())));
        panel.documentChanged();

        assertThat(panel.showsEmptyState()).isTrue();
        assertThat(panel.request()).isEmpty();
    }

    @Test
    void aClickOnACellOpensTheEditorAtItsFirstAddress() {
        Component ram = ram("16384"); // 4 words per cell, 64 x 64 cells
        panel.follow(Set.of(ram.id()));
        assertThat(panel.request()).contains(new MemoryMapRequest(ram.id().value(), 0, 4, 4096));
        MemoryMapPanel.MapGrid grid = panel.grid();
        grid.setSize(640, 640); // cells of 10 pixels

        grid.dispatchEvent(new MouseEvent(grid, MouseEvent.MOUSE_CLICKED, 0, 0, 2 * 10 + 5, 10 + 5, 1, false,
                MouseEvent.BUTTON1));

        assertThat(actions.opened).containsExactly(ram.id() + "@" + (64 + 2) * 4);
    }

    @Test
    void theWheelZoomsABigMemoryDownToSingleWordsAroundTheMouse() {
        Component ram = ram("16777216");
        panel.follow(Set.of(ram.id()));
        assertThat(panel.request().orElseThrow().wordsPerCell()).isEqualTo(4096);
        MemoryMapPanel.MapGrid grid = panel.grid();
        grid.setSize(640, 640);
        int cell = 64 * 40 + 40;
        long address = panel.request().orElseThrow().addressOf(cell);

        for (int notch = 0; notch < 12; notch++) {
            grid.dispatchEvent(new MouseWheelEvent(grid, MouseEvent.MOUSE_WHEEL, 0, 0, 40 * 10 + 5, 40 * 10 + 5, 0,
                    false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, -1));
        }

        MemoryMapRequest zoomed = panel.request().orElseThrow();
        assertThat(zoomed.wordsPerCell()).isEqualTo(1);
        assertThat(zoomed.cells()).isEqualTo(4096);
        assertThat(zoomed.addressOf(cell)).as("the word under the mouse stays under it").isEqualTo(address);

        grid.dispatchEvent(new MouseWheelEvent(grid, MouseEvent.MOUSE_WHEEL, 0, 0, 5, 5, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, 40));
        assertThat(panel.request()).contains(new MemoryMapRequest(ram.id().value(), 0, 4096, 4096));
    }

    @Test
    void whilePausedTheAccessesOfTheLastStepStayLitUntilTheNextStep() {
        Component ram = ram("256");
        panel.follow(Set.of(ram.id()));
        panel.ingest(frame(Map.of(0x10, 3L), Map.of(), 100), false); // what happened before: not recent

        assertThat(panel.litIntensity(0x10)).isZero();
        panel.ingest(frame(Map.of(0x10, 3L, 0x1C, 1L), Map.of(), 110), false);
        clock.addAndGet(5 * SECOND);
        panel.ingest(frame(Map.of(0x10, 3L, 0x1C, 1L), Map.of(), 110), false); // a request, not a step

        assertThat(panel.litIntensity(0x1C)).isEqualTo(1.0);
        assertThat(panel.litAccess(0x1C)).contains(MemoryAccess.READ);
        assertThat(panel.litIntensity(0x10)).isZero();

        panel.ingest(frame(Map.of(0x10, 3L, 0x1C, 1L), Map.of(0x20, 1L), 120), false);
        assertThat(panel.litIntensity(0x1C)).as("the next step turns the last one off").isZero();
        assertThat(panel.litAccess(0x20)).contains(MemoryAccess.WRITE);

        panel.ingest(frame(Map.of(0x10, 3L, 0x1C, 1L), Map.of(0x20, 1L), 130), false);
        assertThat(panel.litIntensity(0x20)).as("a step that touched nothing").isZero();
    }

    @Test
    void whileRunningAnAccessFadesInAboutASecond() {
        Component ram = ram("256");
        panel.follow(Set.of(ram.id()));
        panel.ingest(frame(Map.of(), Map.of(), 0), true);

        panel.ingest(frame(Map.of(0x05, 1L), Map.of(), 10), true);
        assertThat(panel.litIntensity(0x05)).isEqualTo(1.0);
        clock.addAndGet(SECOND / 2);
        assertThat(panel.litIntensity(0x05)).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.01));
        clock.addAndGet(SECOND);
        assertThat(panel.litIntensity(0x05)).isZero();
    }

    @Test
    void recentEventsLightTheirCellEvenWhenTheCountsSayNothing() {
        Component ram = ram("256");
        panel.follow(Set.of(ram.id()));
        MemoryMapRequest request = panel.request().orElseThrow();
        panel.ingest(frame(Map.of(), Map.of(), 0), true);

        panel.ingest(new MemoryMapFrame(request, new long[256], new long[256], new long[256],
                List.of(new MemoryEvent(0x42, MemoryAccess.WRITE, 5)), 5), true);

        assertThat(panel.litAccess(0x42)).contains(MemoryAccess.WRITE);
    }

    @Test
    void aRestartedSimulationStartsCountingAgain() {
        Component ram = ram("256");
        panel.follow(Set.of(ram.id()));
        panel.ingest(frame(Map.of(0x10, 50L), Map.of(), 100), true);

        panel.ingest(frame(Map.of(0x11, 1L), Map.of(), 2), true);

        assertThat(panel.litAccess(0x11)).contains(MemoryAccess.READ);
        assertThat(panel.litIntensity(0x10)).isZero();
    }

    @Test
    void aFrameForAnotherRequestIsIgnored() {
        Component ram = ram("256");
        panel.follow(Set.of(ram.id()));
        MemoryMapRequest other = new MemoryMapRequest("other", 0, 1, 256);
        panel.ingest(new MemoryMapFrame(other, new long[256], new long[256], new long[256],
                List.of(new MemoryEvent(3, MemoryAccess.READ, 1)), 1), true);
        assertThat(panel.litIntensity(3)).isZero();
    }

    @Test
    void theTooltipTellsTheAddressTheCountsAndTheValue() {
        Component ram = ram("1024");
        panel.follow(Set.of(ram.id()));
        MemoryMapRequest request = panel.request().orElseThrow();
        long[] reads = new long[1024];
        long[] values = new long[1024];
        reads[0x1C] = 412;
        values[0x1C] = 0x3A;
        panel.ingest(new MemoryMapFrame(request, reads, new long[1024], values, List.of(), 1), false);

        assertThat(panel.toolTipAt(0x1C)).isEqualTo("0x01C · leída 412 veces · escrita 0 veces · vale 3A");
    }

    @Test
    void theTooltipOfAGroupedCellTellsItsRange() {
        Component ram = ram("16384");
        panel.follow(Set.of(ram.id()));
        panel.ingest(frame(Map.of(1, 1L), Map.of(), 1), false);

        assertThat(panel.toolTipAt(1)).isEqualTo("0x0004–0x0007 · leídas 1 vez · escritas 0 veces · la primera vale 00");
    }

    @Test
    void aRamOffersRestaurarAndVolcarAndARomDoesNot() {
        Component ram = ram("256");
        Component rom = place(document, StandardComponentTypes.ROM, 40, 0, Map.of());
        panel.follow(Set.of(ram.id()));
        assertThat(panel.restoreButton().isVisible()).isTrue();
        assertThat(panel.dumpButton().isVisible()).isTrue();
        assertThat(panel.dumpButton().isEnabled()).as("no dump action wired").isFalse();

        panel.restoreButton().doClick();

        panel.follow(Set.of(rom.id()));
        assertThat(panel.restoreButton().isVisible()).isFalse();
        assertThat(panel.dumpButton().isVisible()).isFalse();
    }

    @Test
    void everyModePaintsWithoutTrouble() {
        Component ram = ram("16384");
        panel.follow(Set.of(ram.id()));
        panel.ingest(frame(Map.of(1, 5L, 7, 1L), Map.of(9, 2L), 1), true);
        panel.ingest(frame(Map.of(1, 6L, 7, 1L), Map.of(9, 3L), 2), true);
        MemoryMapPanel.MapGrid grid = panel.grid();
        grid.setSize(300, 300);
        BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
        for (MemoryMapPanel.Mode mode : MemoryMapPanel.Mode.values()) {
            panel.setMode(mode);
            Graphics2D graphics = image.createGraphics();
            grid.paintComponent(graphics);
            graphics.dispose();
        }
        assertThat(image.getRGB(22 + 4 + 1, 1)).as("cell 1 is painted (64 columns of 4 pixels, centred)").isNotZero();
    }

    @Test
    void framesComeFromTheDocumentsSimulation() {
        Component ram = place(document, StandardComponentTypes.RAM, 0, 0,
                Map.of(MemoryTypes.WORDS, "16", MemoryTypes.CONTENTS, "@3 2A"));
        panel.follow(Set.of(ram.id()));

        waitUntil(() -> {
            panel.refresh();
            return !panel.toolTipAt(3).endsWith("?");
        });

        assertThat(panel.toolTipAt(3)).endsWith("vale 2A");
    }

    @Test
    void aJustReadCellIsPaintedInTheReadColour() {
        Component ram = ram("256");
        panel.follow(Set.of(ram.id()));
        panel.ingest(frame(Map.of(), Map.of(), 0), true);
        panel.ingest(frame(Map.of(1, 1L), Map.of(), 1), true);
        MemoryMapPanel.MapGrid grid = panel.grid();
        grid.setSize(160, 160); // 16 columns of 10 pixels
        BufferedImage image = new BufferedImage(160, 160, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        grid.paintComponent(graphics);
        graphics.dispose();

        assertThat(image.getRGB(15, 5)).isEqualTo(MemoryMapPanel.accessColor(ColorScheme.LIGHT, MemoryAccess.READ).getRGB());
        assertThat(image.getRGB(25, 5)).isNotEqualTo(MemoryMapPanel.accessColor(ColorScheme.LIGHT, MemoryAccess.READ).getRGB());
    }

    private static final class RecordingActions implements MemoryMapActions {
        final List<String> opened = new ArrayList<>();

        @Override
        public boolean canOpenEditor() {
            return true;
        }

        @Override
        public void openEditor(ElementId componentId, long address) {
            opened.add(componentId + "@" + address);
        }
    }
}
