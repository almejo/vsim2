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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** Above 64K words the access counts are per 256-word block: the map must not pretend they are per word. */
class MemoryMapBlockCountersTest {

    /** The map's cells hold 256 words each; the counters count blocks of 512 (the memory's own are never wider). */
    private static final long BLOCK = 512;

    private final OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final AtomicLong clock = new AtomicLong(100_000_000_000L);
    private final MemoryMapPanel panel = new MemoryMapPanel(document, new MemoryMapActions() {
        @Override
        public boolean canOpenEditor() {
            return false;
        }

        @Override
        public void openEditor(ElementId componentId, long address) {
        }
    }, () -> ColorScheme.LIGHT, clock::get, () -> true);

    @AfterEach
    void close() {
        panel.dispose();
        document.close();
    }

    private MemoryMapRequest request() {
        Component ram = place(document, StandardComponentTypes.RAM, 0, 0, Map.of(MemoryTypes.WORDS, "1048576"));
        panel.follow(Set.of(ram.id()));
        MemoryMapRequest request = panel.request().orElseThrow();
        assertThat(request.wordsPerCell()).isLessThan(BLOCK);
        return request;
    }

    private MemoryMapFrame frame(MemoryMapRequest request, long readsOfFirstCell, List<MemoryEvent> recent, long time) {
        long[] reads = new long[request.cells()];
        reads[3] = readsOfFirstCell;
        return new MemoryMapFrame(request, reads, new long[request.cells()], new long[request.cells()], recent, time,
                BLOCK);
    }

    @Test
    void aCountThatGrowsInASharedBlockLightsNothingWithoutARecentAccess() {
        MemoryMapRequest request = request();
        panel.ingest(frame(request, 1, List.of(), 1), true);

        panel.ingest(frame(request, 5, List.of(), 2), true);

        assertThat(panel.litIntensity(3)).isZero();
    }

    @Test
    void aRecentAccessLightsOnlyItsOwnWordInASharedBlock() {
        MemoryMapRequest request = request();
        panel.ingest(frame(request, 1, List.of(), 1), true);

        panel.ingest(frame(request, 5, List.of(new MemoryEvent(3 * request.wordsPerCell() + 1, MemoryAccess.READ, 2)), 2), true);

        assertThat(panel.litIntensity(3)).isGreaterThan(0);
        assertThat(panel.litIntensity(4)).isZero();
    }

    @Test
    void theTooltipOfAWordInASharedBlockSaysItIsTheBlocksCount() {
        MemoryMapRequest request = request();
        panel.ingest(frame(request, 412, List.of(), 1), false);

        assertThat(panel.toolTipAt(3)).contains("bloque de 512 palabras").contains("412 lecturas");
    }
}
