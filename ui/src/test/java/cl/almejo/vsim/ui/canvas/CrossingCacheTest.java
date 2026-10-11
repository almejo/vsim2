package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CrossingCacheTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CrossingCache cache = new CrossingCache();

    private static Wire wire(CircuitDocument document, int startX, int startY, int endX, int endY) {
        return new Wire(document.newWireId(), new GridPoint(startX, startY), new GridPoint(endX, endY));
    }

    @Test
    void anUnchangedDocumentReusesTheCrossingsItFound() {
        CircuitDocument document = new CircuitDocument();
        Wire horizontal = wire(document, 0, 5, 10, 5);
        Wire vertical = wire(document, 5, 0, 5, 10);
        document.add(horizontal);
        document.add(vertical);

        Map<ElementId, List<Crossing>> first = cache.gapsByVerticalWire(document, registry);

        assertThat(first).containsOnlyKeys(vertical.id());
        assertThat(first.get(vertical.id()))
                .containsExactly(new Crossing(new GridPoint(5, 5), horizontal.id(), vertical.id()));
        assertThat(cache.gapsByVerticalWire(document, registry)).isSameAs(first);
    }

    @Test
    void theCrossingsOfAVerticalWireAreSortedByY() {
        CircuitDocument document = new CircuitDocument();
        Wire lower = wire(document, 0, 8, 10, 8);
        Wire upper = wire(document, 0, 2, 10, 2);
        Wire vertical = wire(document, 5, 0, 5, 10);
        document.add(lower);
        document.add(upper);
        document.add(vertical);

        assertThat(cache.gapsByVerticalWire(document, registry).get(vertical.id())).containsExactly(
                new Crossing(new GridPoint(5, 2), upper.id(), vertical.id()),
                new Crossing(new GridPoint(5, 8), lower.id(), vertical.id()));
    }

    @Test
    void editsUndoAndRedoRefreshTheCrossings() {
        try (OpenDocument openDocument = OpenDocument.createEmpty(registry)) {
            CircuitDocument document = openDocument.document();
            openDocument.execute(new AddElementsCommand(List.of(wire(document, 0, 5, 10, 5))));
            assertThat(cache.gapsByVerticalWire(document, registry)).isEmpty();
            Wire vertical = wire(document, 5, 0, 5, 10);
            openDocument.execute(new AddElementsCommand(List.of(vertical)));
            assertThat(cache.gapsByVerticalWire(document, registry)).containsOnlyKeys(vertical.id());
            openDocument.history().undo();
            assertThat(cache.gapsByVerticalWire(document, registry)).isEmpty();
            openDocument.history().redo();
            assertThat(cache.gapsByVerticalWire(document, registry)).containsOnlyKeys(vertical.id());
        }
    }

    @Test
    void anotherDocumentWithTheSameRevisionGetsItsOwnCrossings() {
        CircuitDocument crossed = new CircuitDocument();
        crossed.add(wire(crossed, 0, 5, 10, 5));
        crossed.add(wire(crossed, 5, 0, 5, 10));
        CircuitDocument teed = new CircuitDocument();
        teed.add(wire(teed, 0, 5, 10, 5));
        teed.add(wire(teed, 5, 5, 5, 10));
        assertThat(crossed.revision()).isEqualTo(teed.revision());

        assertThat(cache.gapsByVerticalWire(crossed, registry)).hasSize(1);
        assertThat(cache.gapsByVerticalWire(teed, registry)).isEmpty();
    }
}
