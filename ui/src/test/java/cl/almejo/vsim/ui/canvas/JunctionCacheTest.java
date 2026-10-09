package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JunctionCacheTest {

    private final JunctionCache cache = new JunctionCache();

    private static Wire wire(CircuitDocument document, int startX, int startY, int endX, int endY) {
        return new Wire(document.newWireId(), new GridPoint(startX, startY), new GridPoint(endX, endY));
    }

    @Test
    void anUnchangedDocumentReusesTheJunctionsItFound() {
        CircuitDocument document = new CircuitDocument();
        document.add(wire(document, 0, 0, 10, 0));
        document.add(wire(document, 5, 0, 5, 5));
        Set<GridPoint> first = cache.of(document);
        assertThat(first).containsExactly(new GridPoint(5, 0));
        assertThat(cache.of(document)).isSameAs(first);
    }

    @Test
    void undoAndRedoRefreshTheJunctions() {
        try (OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry())) {
            CircuitDocument document = openDocument.document();
            openDocument.execute(new AddElementsCommand(List.of(wire(document, 0, 0, 10, 0))));
            assertThat(cache.of(document)).isEmpty();
            openDocument.execute(new AddElementsCommand(List.of(wire(document, 5, 0, 5, 5))));
            assertThat(cache.of(document)).containsExactly(new GridPoint(5, 0));
            openDocument.history().undo();
            assertThat(cache.of(document)).isEmpty();
            openDocument.history().redo();
            assertThat(cache.of(document)).containsExactly(new GridPoint(5, 0));
        }
    }

    @Test
    void anotherDocumentWithTheSameRevisionGetsItsOwnJunctions() {
        CircuitDocument branched = new CircuitDocument();
        branched.add(wire(branched, 0, 0, 10, 0));
        branched.add(wire(branched, 5, 0, 5, 5));
        CircuitDocument straight = new CircuitDocument();
        straight.add(wire(straight, 0, 0, 10, 0));
        straight.add(wire(straight, 10, 0, 20, 0));
        assertThat(branched.revision()).isEqualTo(straight.revision());
        assertThat(cache.of(branched)).containsExactly(new GridPoint(5, 0));
        assertThat(cache.of(straight)).isEmpty();
    }
}
